package com.twithu.orderliabilityagent.rag.transform;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 领域感知的文档 Transform（ETL 的 T 环节）。
 * <p>规则文档的 dimension/rule_id/issue_type/priority/evidence_required 元数据
 * 已由 JsonReader 从结构化字段精确提取，无需 LLM 生成；本组件做两件通用 enricher 做不了的事：</p>
 * <ol>
 *   <li>按 issue_type 注入口语别名（床型=大床/双床/king/twin），弥合投诉口语与规则书面语的词汇鸿沟，提升召回；</li>
 *   <li>别名拼入文本参与 embedding，元数据保持原样供过滤与重排使用。</li>
 * </ol>
 * <p>别名表维护在 classpath:rag/issue-aliases.json（issue_type → 空格分隔的别名串），
 * 新增/调整别名只改配置文件，无需动代码。</p>
 * <p>注意：文本变化会改变 content_hash，首次启用会触发全量重新 embedding（幂等机制自动处理）。</p>
 */
@Component
public class RuleMetadataEnricher implements DocumentTransformer {

    /** issue_type → 口语/英文别名，来自 classpath:rag/issue-aliases.json；未收录的 issue_type 不做扩充 */
    private final Map<String, String> issueAliasMap;

    public RuleMetadataEnricher(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("rag/issue-aliases.json").getInputStream()) {
            issueAliasMap = objectMapper.readValue(in, new TypeReference<Map<String, String>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("加载口语别名配置 rag/issue-aliases.json 失败", e);
        }
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        List<Document> result = new ArrayList<>(documents.size());
        for (Document doc : documents) {
            String issueType = Objects.toString(doc.getMetadata().get("issue_type"), "");
            String aliases = issueAliasMap.get(issueType);
            if (aliases == null || aliases.isBlank()) {
                result.add(doc);
                continue;
            }
            result.add(doc.mutate()
                    .text(doc.getText() + "\n- 口语表达: " + aliases)
                    .build());
        }
        return result;
    }
}
