package com.twithu.orderliabilityagent.core.guard;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.twithu.orderliabilityagent.core.evidence.EvidenceComparator;
import com.twithu.orderliabilityagent.entity.Rule;
import com.twithu.orderliabilityagent.model.dto.FieldDiff;
import com.twithu.orderliabilityagent.model.dto.JudgmentResult;
import com.twithu.orderliabilityagent.model.enums.Liability;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * P8 判责可信度打分器：纯代码，不调 LLM，与 JudgmentGuard 并列。
 * 设计立场：权威可信度由确定性信号算出，LLM 自报 confidence 仅作偏差校验信号（校准差、不可复现）。
 * 总分 = 0.4×证据链质量 + 0.25×规则匹配质量 + 0.35×判决一致性(是否跟着证据链走)，各维从 1.0 起扣，扣到 0 为止。
 * 每一分都有扣分明细（deductions），落 RunTrace 可审计、可回放。
 * 特殊规则豁免：去哪儿/大众点评 CHANNEL 跳改判我司时，一致性维不扣"分支不一致"（见 SpecialRulePolicy）。
 * 权重与阈值为设计起点，需在判责评测集上校准；分档 HIGH≥0.85 / MEDIUM≥0.60 / LOW<0.60。
 */
@Component
public class ConfidenceScorer {

    public static final double WEIGHT_EVIDENCE = 0.4;
    public static final double WEIGHT_RULE = 0.25;
    public static final double WEIGHT_CONSISTENCY = 0.35;
    public static final double HIGH_THRESHOLD = 0.85;
    public static final double MEDIUM_THRESHOLD = 0.60;

    /** 自由文本字段：字符串严格比对有错字/别名假阳性风险，证据维额外扣分 */
    private static final Set<String> FREE_TEXT_FIELDS = Set.of("hotelName", "hotelAddress");

    /**
     * 打分报告。
     * - score：加权总分 0~1
     * - tier：HIGH / MEDIUM / LOW
     * - evidenceScore/ruleScore/consistencyScore：三维各自得分
     * - specialOverride：是否触发特殊规则豁免
     * - deductions：逐条扣分明细（含维度前缀），供人工复核与 trace 审计
     */
    public record ConfidenceReport(double score, String tier,
                                   double evidenceScore, double ruleScore, double consistencyScore,
                                   boolean specialOverride, List<String> deductions) {
    }

    /**
     * 计算可信度。
     * - judgment：P7 结构化判决
     * - hitRule：判决引用的规则（ruleId 命中候选）；幻觉引用时为 null，守卫会硬失败
     * - diffList：P5 逐跳比对结果
     * - retrievedRules：P6 重排后召回文档（携带相似度 getScore 与 metadata.rerank_score）
     * - channel：订单 DB 渠道（evidence.channel()，权威值，不用 LLM 抽取的口述渠道）
     */
    public ConfidenceReport score(JudgmentResult judgment, Rule hitRule, List<FieldDiff> diffList,
                                  List<Document> retrievedRules, String channel) {
        List<String> deductions = new ArrayList<>();
        String hop = EvidenceComparator.aggregateHop(diffList);
        Liability liability = judgment == null ? null : judgment.getLiability();
        boolean specialOverride = SpecialRulePolicy.isOverrideApplicable(channel, hop, liability);

        double evidence = scoreEvidence(diffList, hop, deductions);
        double rule = scoreRule(hitRule, retrievedRules, deductions);
        double consistency = scoreConsistency(judgment, hitRule, diffList, hop, specialOverride,
                evidence, rule, deductions);

        double total = clamp(WEIGHT_EVIDENCE * evidence + WEIGHT_RULE * rule + WEIGHT_CONSISTENCY * consistency);
        String tier = total >= HIGH_THRESHOLD ? "HIGH" : total >= MEDIUM_THRESHOLD ? "MEDIUM" : "LOW";
        return new ConfidenceReport(total, tier, evidence, rule, consistency, specialOverride, deductions);
    }

    /** 证据链质量：跳定位越客观越高；SUPPLIER 的实际值来自口述无佐证，自由文本与多字段冲突额外扣 */
    private double scoreEvidence(List<FieldDiff> diffList, String hop, List<String> deductions) {
        double s = switch (hop == null ? "" : hop) {
            case EvidenceComparator.HOP_OURS, EvidenceComparator.HOP_CHANNEL -> 1.0;
            case EvidenceComparator.HOP_SUPPLIER -> 0.7;
            case EvidenceComparator.HOP_EVIDENCE_GAP -> 0.3;
            default -> 0.2;
        };
        if (EvidenceComparator.HOP_SUPPLIER.equals(hop)) {
            deductions.add("[证据] 实际值来自投诉口述，无第三方佐证，基线0.7");
        } else if (EvidenceComparator.HOP_EVIDENCE_GAP.equals(hop)) {
            deductions.add("[证据] 证据链缺失跳，无法定位，基线0.3");
        } else if (EvidenceComparator.HOP_NONE.equals(hop) || hop == null) {
            deductions.add("[证据] 四跳一致，投诉不成立或需人工，基线0.2");
        }
        boolean freeText = diffList != null
                && diffList.stream().anyMatch(d -> FREE_TEXT_FIELDS.contains(d.field()));
        if (freeText) {
            s -= 0.1;
            deductions.add("[证据] 自由文本字段字符串比对有假阳性风险 -0.1");
        }
        if (diffList != null && diffList.size() > 1
                && diffList.stream().map(FieldDiff::firstBadHop).distinct().count() > 1) {
            s -= 0.1;
            deductions.add("[证据] 多字段 firstBadHop 冲突，聚合取舍有歧义 -0.1");
        }
        return clamp(s);
    }

    /** 规则匹配质量：命中相似度 + 重排分差（规则竞争）+ 兜底规则上限 */
    private double scoreRule(Rule hitRule, List<Document> retrievedRules, List<String> deductions) {
        if (hitRule == null) {
            deductions.add("[规则] 命中规则缺失，无法评估匹配质量，取0.5");
            return 0.5;
        }
        double s = 1.0;
        Double sim = retrievedRules == null ? null : retrievedRules.stream()
                .filter(d -> hitRule.getRuleId().equals(Objects.toString(d.getMetadata().get("rule_id"), null)))
                .map(Document::getScore)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        if (sim == null) {
            s -= 0.1;
            deductions.add("[规则] 命中规则不在召回集（无相似度） -0.1");
        } else if (sim < 0.6) {
            s -= 0.3;
            deductions.add("[规则] 召回相似度过低 " + fmt(sim) + " -0.3");
        } else if (sim < 0.7) {
            s -= 0.2;
            deductions.add("[规则] 召回相似度偏低 " + fmt(sim) + " -0.2");
        } else if (sim < 0.8) {
            s -= 0.1;
            deductions.add("[规则] 召回相似度中等 " + fmt(sim) + " -0.1");
        }
        if (retrievedRules != null && retrievedRules.size() >= 2) {
            double margin = rerankScore(retrievedRules.get(0)) - rerankScore(retrievedRules.get(1));
            if (margin < 0.05) {
                s -= 0.1;
                deductions.add("[规则] top1/top2 重排分差<0.05，规则竞争 -0.1");
            }
        }
        if (hitRule.getDefaultLiability() == Liability.UNDETERMINED) {
            if (s > 0.5) {
                deductions.add("[规则] 命中兜底/无法判定类规则，上限0.5");
            }
            s = Math.min(s, 0.5);
        }
        return clamp(s);
    }

    /** 判决一致性：分支裁决（特殊规则豁免）+ reason 覆盖 + result 措辞 + 自报偏差 */
    private double scoreConsistency(JudgmentResult judgment, Rule hitRule, List<FieldDiff> diffList,
                                    String hop, boolean specialOverride,
                                    double evidence, double rule, List<String> deductions) {
        if (judgment == null) {
            return 0.0;
        }
        double s = 1.0;
        Liability liability = judgment.getLiability();
        Liability expected = branchExpectation(hitRule, hop);
        if (expected != null && expected != liability) {
            if (specialOverride) {
                // 豁免：特殊规则覆盖分支裁决，不扣分（specialOverride 已记录于报告与 trace）
            } else if (judgment.getReason() != null && !judgment.getReason().isBlank()) {
                s = 0.6;
                deductions.add("[一致性] 偏离分支裁决（附理由）降至0.6");
            } else {
                s = 0.0;
                deductions.add("[一致性] 偏离分支裁决且无理由降至0");
            }
        }
        String reason = judgment.getReason();
        if (reason != null && !reason.isBlank() && diffList != null) {
            int uncovered = 0;
            for (FieldDiff d : diffList) {
                boolean covered = contains(reason, d.actualValue())
                        || contains(reason, d.channelValue())
                        || contains(reason, d.field());
                if (!covered) {
                    uncovered++;
                }
            }
            if (uncovered > 0) {
                double pen = Math.min(0.2, 0.1 * uncovered);
                s -= pen;
                deductions.add("[一致性] reason 未覆盖 " + uncovered + " 个争议字段 -" + fmt(pen));
            }
        }
        String result = judgment.getResult();
        String keyword = liabilityKeyword(liability);
        if (result != null && !result.isBlank() && keyword != null && !result.contains(keyword)) {
            s -= 0.15;
            deductions.add("[一致性] result 措辞与责任枚举不一致 -0.15");
        }
        s = clamp(s);
        Double self = judgment.getConfidence();
        if (self != null) {
            // 用不含本项的临时总分比较，避免自指循环
            double provisional = WEIGHT_EVIDENCE * evidence + WEIGHT_RULE * rule + WEIGHT_CONSISTENCY * s;
            if (Math.abs(self - provisional) > 0.3) {
                s = clamp(s - 0.05);
                deductions.add("[一致性] 自报置信度与代码分偏差>0.3 -0.05");
            }
        }
        return clamp(s);
    }

    /**
     * 解析 branches_json，返回 firstBadHop 对应的期望责任方；无匹配返回 null。
     * 公开静态供 JudgmentGuard 复用，避免两处各写一份解析。
     */
    public static Liability branchExpectation(Rule rule, String firstBadHop) {
        if (rule == null || rule.getBranchesJson() == null || rule.getBranchesJson().isBlank() || firstBadHop == null) {
            return null;
        }
        JSONArray branches = JSONUtil.parseArray(rule.getBranchesJson());
        for (Object o : branches) {
            JSONObject branch = (JSONObject) o;
            JSONObject when = branch.getJSONObject("when");
            if (when != null && firstBadHop.equals(when.getStr("firstBadHop"))) {
                return Liability.of(branch.getStr("liability"));
            }
        }
        return null;
    }

    private static double rerankScore(Document d) {
        Object r = d.getMetadata().get("rerank_score");
        if (r instanceof Number n) {
            return n.doubleValue();
        }
        return d.getScore() == null ? 0.0 : d.getScore();
    }

    private static String liabilityKeyword(Liability l) {
        if (l == null) {
            return null;
        }
        return switch (l) {
            case OURS_PRIMARY -> "我司";
            case CHANNEL_PRIMARY -> "渠道";
            case SUPPLIER_PRIMARY -> "供应商";
            case UNDETERMINED -> "无法";
            default -> null;
        };
    }

    private static boolean contains(String text, String val) {
        return val != null && !val.isBlank() && text.contains(val);
    }

    private static double clamp(double v) {
        return Math.clamp(v, 0.0, 1.0);
    }

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }
}
