package com.twithu.orderliabilityagent.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.twithu.orderliabilityagent.advisor.TokenUsageAdvisor;
import com.twithu.orderliabilityagent.custom.CustomJdbcChatMemoryRepository;
import com.twithu.orderliabilityagent.model.dto.ExtractResult;
import com.twithu.orderliabilityagent.model.dto.PreprocessResult;
import com.twithu.orderliabilityagent.rag.DocumentLoader;
import com.twithu.orderliabilityagent.rag.LoggerAdvisor;
import com.twithu.orderliabilityagent.rag.AdvisorFactory;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.stereotype.Component;

import static com.twithu.orderliabilityagent.custom.CustomJdbcChatMemoryRepository.MAX_MESSAGE;

@Component
public class LiabilityAgent {


    @Resource
    private DocumentLoader documentLoader;

    @Resource
    private PgVectorStore pgVectorStore;

    private final ChatModel ollamaChatModel;

    /** 重度模型  */
    private final ChatClient chatClient;
    /** 轻量模型，用于压缩历史对话、改写中文  */
    private final ChatClient lightChatClient;

    public LiabilityAgent(ChatModel deepSeekChatModel, ChatModel ollamaChatModel,CustomJdbcChatMemoryRepository customJdbcChatMemoryRepository) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(customJdbcChatMemoryRepository).maxMessages(MAX_MESSAGE).build();
        chatClient = ChatClient.builder(deepSeekChatModel)
                // .defaultSystem()
                .defaultAdvisors(
                        new LoggerAdvisor()
                        , new TokenUsageAdvisor()
                        , MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();

        lightChatClient = ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(
                        new LoggerAdvisor()
                        , new TokenUsageAdvisor()
                        , MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
        this.ollamaChatModel = ollamaChatModel;
    }

    /** 结构化输出template */
    private final String template = """
            originalUserPrompt:{userPrompt}.
            Identified attributes from the originalUserPrompt:
            orderNo:{orderNo},channelName:{customerName},supplierName:{supplierName}.
            """;


    public ExtractResult extract(PreprocessResult preprocessResult, String chatId) {
        ExtractResult result = chatClient.prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))
                .system(documentLoader.loadResource("classpath:docs/extractPrompt.txt"))
                .user(promptUserSpec -> promptUserSpec.text(template)
                        .param("userPrompt", preprocessResult.getNormalizedInput())
                        .param("orderNo", preprocessResult.getOrderNoHint())
                        .param("customerName", preprocessResult.getCustomerHint())
                        .param("supplierName", preprocessResult.getSupplierHint()))
                .call()
                .entity(ExtractResult.class
                        , spec -> spec.validateSchema() //失败带错误反馈重试，默认最多 3 次
                        .useProviderStructuredOutput() // 要求上游强制结构化输出,这里上游DS模型没有实现接口，先不管。
                );
        return result;
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
