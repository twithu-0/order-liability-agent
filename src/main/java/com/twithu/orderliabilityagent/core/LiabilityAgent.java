package com.twithu.orderliabilityagent.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.twithu.orderliabilityagent.advisor.TokenUsageAdvisor;
import com.twithu.orderliabilityagent.custom.CustomJdbcChatMemoryRepository;
import com.twithu.orderliabilityagent.entity.Rule;
import com.twithu.orderliabilityagent.model.dto.ExtractResult;
import com.twithu.orderliabilityagent.model.dto.FieldDiff;
import com.twithu.orderliabilityagent.model.dto.JudgmentResult;
import com.twithu.orderliabilityagent.model.dto.PreprocessResult;
import com.twithu.orderliabilityagent.rag.DocumentLoader;
import com.twithu.orderliabilityagent.rag.LoggerAdvisor;
import com.twithu.orderliabilityagent.rag.AdvisorFactory;
import com.twithu.orderliabilityagent.rag.postretrieval.PriorityRerankPostProcessor;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.Collectors;

import static com.twithu.orderliabilityagent.custom.CustomJdbcChatMemoryRepository.MAX_MESSAGE;

@Component
public class LiabilityAgent {


    @Resource
    private DocumentLoader documentLoader;

    @Resource
    private PgVectorStore pgVectorStore;

    private final ChatModel ollamaChatModel;

    private final ChatModel deepSeekChatModel;

    private final ChatMemory chatMemory;

    /** 重度模型  */
    private final ChatClient chatClient;
    private final ChatClient noMemoryChatClient;
    /** 轻量模型，用于压缩历史对话、改写中文  */
    private final ChatClient lightChatClient;
    /**
     * P9 流式表达层专用 ChatClient，挂本地 Ollama 轻量模型。
     * 与 lightChatClient 的区别：不挂 MessageChatMemoryAdvisor。
     * 表达是无状态润色（输入=问题+已验证结论），不需要历史；
     * 若进记忆会把润色 prompt 混入判责上下文窗口，污染后续轮次的抽取/判责。
     * 模型分级落地：重任务（抽取/判责）走 DeepSeek，轻任务（流式表达）走本地模型，零云端成本。
     */
    private final ChatClient presentChatClient;

    public LiabilityAgent(ChatModel deepSeekChatModel, ChatModel ollamaChatModel,CustomJdbcChatMemoryRepository customJdbcChatMemoryRepository) {
        this.deepSeekChatModel = deepSeekChatModel;
        this.chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(customJdbcChatMemoryRepository).maxMessages(MAX_MESSAGE).build();
        chatClient = ChatClient.builder(deepSeekChatModel)
                // .defaultSystem()
                .defaultAdvisors(
                        new LoggerAdvisor()
                        , new TokenUsageAdvisor()
                        , MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
        noMemoryChatClient = ChatClient.builder(deepSeekChatModel)
                .defaultAdvisors(
                        new LoggerAdvisor()
                        , new TokenUsageAdvisor()
                )
                .build();

        lightChatClient = ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(
                        new LoggerAdvisor()
                        , new TokenUsageAdvisor()
                        , MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
        presentChatClient = ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(
                        new LoggerAdvisor()
                        , new TokenUsageAdvisor()
                )
                .build();
        this.ollamaChatModel = ollamaChatModel;
    }

    /** P9 表达层 prompt：只润色，不得改变事实与结论 */
    private final String presentTemplate = """
            你是酒店客服判责助手。下面的判责结论已经过证据比对和规则守卫校验，请把它改写成面向客人的回复。
            要求：
            - 不得改变责任方、规则编号和结论语义，只润色措辞
            - 语气专业、有同理心、简洁，不超过 200 字
            - 保留规则编号引用，结尾原样附上固定提示语"结论仅供参考，如有异议请人工复核。"
            - 直接输出回复正文，不要 JSON、不要标题、不要解释你在做什么
            - 必须把结论放在回复开头，例如“我认为是渠道责任，经核查...”、“目前我无法确定责任方，经核查...”

            用户原始问题：{userPrompt}
            判责结论：{result}
            判责依据：{reason}
            """;

    /**
     * P9 流式表达：把守卫通过的结论流式润色成客服话术。
     * 返回 Flux<ChatResponse> 而非 Flux<String>：末 chunk 携带 usage 元数据，
     * 供服务层落 P9 轨迹与 token 汇总（与 TokenUsageAdvisor 流式日志同源）。
     * 可靠性与体验分离：事实正确性由 P8 守卫保证，本层只负责表达，出错可降级回原结论。
     */
    public Flux<ChatResponse> streamPresentation(String userPrompt, String result, String reason) {
        return presentChatClient.prompt()
                .user(promptUserSpec -> promptUserSpec.text(presentTemplate)
                        .param("userPrompt", userPrompt)
                        .param("result", result)
                        .param("reason", reason == null ? "" : reason))
                .stream()
                .chatResponse();
    }

    /** 提取历史对话记录并压缩 */
    public String compressChatHistory(String userPrompt, String chatId){
        Query query = Query.builder()
                .text(userPrompt)
                .history(chatMemory.get(chatId))
                .build();
        QueryTransformer transformer = AdvisorFactory.createCompressionTransformer(deepSeekChatModel);
        query = transformer.transform(query);
        return query.text();
    }

    /**
     * P6 前置：查询变换。
     * 模型分级：查询侧理想挂轻量模型，当前本地 3b 改写不达标（评测已否决），暂用 DeepSeek；
     * 换 qwen3:4b-instruct 等更强本地模型时只需替换 createQueryTransformers 的入参。
     */
    public String transformQuery(String userPrompt) {
        Query query = Query.builder()
                .text(userPrompt)
                .build();
        QueryTransformer transformer = AdvisorFactory.createRewriteTransformer(deepSeekChatModel);
        query = transformer.transform(query);
        return query.text();
    }

    /**
     * P6 纯检索：宽召回 + 优先级重排，零 LLM 调用。
     * dimension 为空时不做维度过滤（兜底，正常应由 P2 抽取给出）。
     */
    public List<Document> retrieveRules(String query, String dimension) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(5)
                .similarityThreshold(0.60);
        if (dimension != null && !dimension.isEmpty()) {
            builder.filterExpression(new FilterExpressionBuilder().eq("dimension", dimension).build());
        }
        List<Document> docs = pgVectorStore.similaritySearch(builder.build());
        return new PriorityRerankPostProcessor(3, 0.7).process(new Query(query), docs);
    }

    /** 判责用户消息模板：事实 + 证据比对 + 候选规则全文 */
    private final String judgeTemplate = """
            complaint: {complaint}
            diffList: {diffList}
            rules:
            {rules}
            """;

    /**
     * P7 判责：事实 + FieldDiff + 候选规则全文（含分支裁决）→ 结构化判决。
     * 同步主流程最后一次 LLM 调用（流式路径另有 P9 表达层）；ruleId 合法性由 P8 守卫校验。
     * 返回 ResponseEntity：entity 是判决结果，response 携带 usage 元数据，供编排器落 token 消耗。
     */
    public ResponseEntity<ChatResponse, JudgmentResult> judge(String complaint, List<FieldDiff> diffList, List<Rule> rules, String chatId) {
        ObjectMapper objectMapper = new ObjectMapper();
        String diffJsonStr = objectMapper.writeValueAsString(diffList);
        String complaintJsonStr = objectMapper.writeValueAsString(complaint);
        String ruleText = rules.stream()
                .map(r -> r.getRuleId() + " " + r.getName() + "\n" + r.getContentMd()
                        + "\n分支裁决: " + r.getBranchesJson())
                .collect(Collectors.joining("\n---\n"));
        return chatClient.prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))
                .system(documentLoader.loadResource("classpath:prompt/judgePrompt.txt"))
                .user(promptUserSpec -> promptUserSpec.text(judgeTemplate)
                        .param("complaint", complaintJsonStr)
                        .param("diffList", diffJsonStr)
                        .param("rules", ruleText))
                .call()
                .responseEntity(JudgmentResult.class, spec -> spec.validateSchema());
    }

    /** 结构化输出template */
    private final String template = """
            originalUserPrompt:{userPrompt}.
            Identified attributes from the originalUserPrompt:
            orderNo:{orderNo},channelName:{customerName},supplierName:{supplierName}.
            """;


    /**
     * P2 结构化抽取。返回 ResponseEntity：entity 是抽取结果，response 携带 usage 元数据，
     * 供编排器落 RunTrace 的 token 消耗（与 TokenUsageAdvisor 日志同源）。
     */
    public ResponseEntity<ChatResponse, ExtractResult> extract(PreprocessResult preprocessResult, String chatId) {
        return noMemoryChatClient.prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))
                .system(documentLoader.loadResource("classpath:prompt/extractPrompt.txt"))
                .user(promptUserSpec -> promptUserSpec.text(template)
                        .param("userPrompt", preprocessResult.getNormalizedInput())
                        .param("orderNo", preprocessResult.getOrderNoHint())
                        .param("customerName", preprocessResult.getCustomerHint())
                        .param("supplierName", preprocessResult.getSupplierHint()))
                .call()
                .responseEntity(ExtractResult.class
                        , spec -> spec.validateSchema() //失败带错误反馈重试，默认最多 3 次
                        .useProviderStructuredOutput() // 要求上游强制结构化输出,这里上游DS模型没有实现接口，先不管。
                );
    }

    public String chatWithRag(String userPrompt, String chatId, String dimension){
        return chatClient.prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors( AdvisorFactory.createRetrievalAdvisor(pgVectorStore, dimension))
                .system("从已有的知识文档返回最相关的文档内容")
                .user(userPrompt)
                .call()
                .content();
    }

    public String CompressRewriteUserPrompt(String chatId) {
        return lightChatClient.prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(AdvisorFactory.createRewriteAdvisor(ollamaChatModel))
                .call()
                .content();
    }


    // region 测试代码

    public String chat(String prompt, String chatId){
        String res = chatClient.prompt(prompt)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .content();
        System.out.println(res);
        return res;
    }

    /**
     * 抽取用户输入的关键信息
     */
    public void chatOutputStructure(String prompt, String chatId){
        Action res = chatClient.prompt(prompt)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))
                .system("你需要从用户输入中抽取关键信息，并用结构化json输出。当相关信息不存在时，设置为null返回，不要自己编造数据")
                .call()
                .entity(Action.class
                        // , spec -> spec.validateSchema() // 失败带错误反馈重试，默认最多 3 次
                                // .useProviderStructuredOutput() // 要求上游强制结构化输出
                );
        System.out.println(res);
    }

    // @JsonProperty 作用于字段，解析 JSON 时将字段名映射为指定的名称
    record Action(@JsonProperty("时间") String AAA, @JsonProperty("地点") String BBB) {
    }

    // endregion


}
