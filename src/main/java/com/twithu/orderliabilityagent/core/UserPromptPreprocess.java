package com.twithu.orderliabilityagent.core;

import com.twithu.orderliabilityagent.model.dto.PreprocessResult;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.twithu.orderliabilityagent.constants.OrderConstant.*;

/**
 * 用户输入预处理
 */
public class UserPromptPreprocess {

    public static void main(String[] args) {
        String input = "MT12345222  渠道去哪儿 供应商 酒店名字： 京州大酒店，电话134231。客人反馈找不到酒店，电话也联系不上";
        PreprocessResult extract = UserPromptPreprocess.extract(input);
        System.out.println(extract);
    }

    /**
     * 订单号规则：MT开头 + 8位数字
     * 前后增加非字母数字边界，避免从更长的字符串中误截取
     */
    private static final Pattern ORDER_NO_PATTERN =
            Pattern.compile("(?<![A-Za-z0-9])MT\\d{8}(?![A-Za-z0-9])");

    private static final List<String> CHANNEL_STOP_KEYWORDS = Arrays.asList(
            "供应商", "酒店名字", "酒店名称", "酒店", "电话", "订单号", "客人", "反馈"
    );

    private static final List<String> SUPPLIER_STOP_KEYWORDS = Arrays.asList(
            "酒店名字", "酒店名称", "酒店", "电话", "订单号", "客人", "反馈", "渠道"
    );

    public static PreprocessResult extract(String input) {
        PreprocessResult result = new PreprocessResult();
        try {
            String normalized = normalizeInput(input);
            result.setNormalizedInput(normalized);

            // 1. 订单号抽取 & 是否目标请求
            Matcher matcher = ORDER_NO_PATTERN.matcher(normalized);
            if (matcher.find()) {
                result.setLiabilityRequest(true);
                result.setOrderNoHint(matcher.group());
            } else {
                result.setLiabilityRequest(false);
                result.setOrderNoHint(null);
            }

            // 2. 渠道抽取
            result.setCustomerHint(
                    extractHint(normalized, "渠道", CHANNEL_SET, CHANNEL_STOP_KEYWORDS)
            );

            // 3. 供应商抽取
            result.setSupplierHint(
                    extractHint(normalized, "供应商", SUPPLIER_SET, SUPPLIER_STOP_KEYWORDS)
            );

            if(result.getCustomerHint() == null || result.getSupplierHint() == null){
                result.setLiabilityRequest(false);
            }
        } catch (Exception e) {
            result.setLiabilityRequest(false);
        }

        return result;
    }

    /**
     * 归一化文本：
     * 1. 全角空格、换行、Tab 转普通空格
     * 2. 连续空白压缩为一个空格
     * 3. 英文标点统一为中文标点
     * 4. 去掉中文标点前后的空格
     */
    public static String normalizeInput(String input) {
        if (input == null) {
            return "";
        }

        String s = input.replace('\u3000', ' ')
                .replaceAll("[\\t\\r\\n]+", " ");

        // 压缩连续空格
        s = s.replaceAll("\\s+", "").trim();

        // 统一标点符号
        s = s.replace(',', '，')
                .replace('.', '。')
                .replace(':', '：')
                .replace(';', '；')
                .replace('?', '？')
                .replace('!', '！');

        // 去掉中文标点前的空格
        s = s.replaceAll("\\s+([，。：；！？、])", "$1");

        // 去掉中文标点后的空格
        s = s.replaceAll("([，。：；！？、])\\s+", "$1");

        return s.trim();
    }

    /**
     * 从关键字后提取候选片段，并匹配白名单。
     * 例如：
     * 渠道 A 供应商 B 酒店名字：xxx
     * 抽取“渠道”后、到“供应商”前的片段 "A"，再匹配 customerSet。
     */
    private static String extractHint(String text,
                                      String keyword,
                                      Set<String> whitelist,
                                      List<String> stopKeywords) {
        if (text == null || text.isEmpty()
                || whitelist == null || whitelist.isEmpty()) {
            return null;
        }

        int keywordIndex = text.indexOf(keyword);

        if (keywordIndex >= 0) {
            int start = keywordIndex + keyword.length();

            // 跳过关键字后的空格和冒号
            while (start < text.length()) {
                char c = text.charAt(start);
                if (Character.isWhitespace(c) || c == '：' || c == ':') {
                    start++;
                } else {
                    break;
                }
            }

            // 截取到下一个业务关键字之前
            int end = text.length();
            for (String stopKeyword : stopKeywords) {
                int index = text.indexOf(stopKeyword, start);
                if (index >= 0 && index < end) {
                    end = index;
                }
            }

            String segment = text.substring(start, end).trim();

            // 去掉候选片段开头可能残留的标点
            segment = segment.replaceAll("^[，。：；！？、\\s]+", "").trim();

            String matched = matchWhitelist(segment, whitelist);
            if (matched != null) {
                return matched;
            }
        }

        // 如果关键字后没有匹配到，则退化为全文白名单匹配
        return matchWhitelist(text, whitelist);
    }

    /**
     * 在白名单中匹配文本，返回命中的白名单原值。
     * 多个命中时优先返回长度更长的名称，降低短名称误匹配概率。
     */
    private static String matchWhitelist(String text, Set<String> whitelist) {
        if (text == null || text.isEmpty() || whitelist == null || whitelist.isEmpty()) {
            return null;
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        String bestMatch = null;

        for (String name : whitelist) {
            if (name == null || name.trim().isEmpty()) {
                continue;
            }

            String trimmedName = name.trim();
            String lowerName = trimmedName.toLowerCase(Locale.ROOT);

            if (lowerText.contains(lowerName)) {
                if (bestMatch == null || trimmedName.length() > bestMatch.length()) {
                    bestMatch = trimmedName;
                }
            }
        }

        return bestMatch;
    }

}
