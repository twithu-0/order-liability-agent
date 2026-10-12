package com.twithu.orderliabilityagent.rag;

import com.twithu.orderliabilityagent.rag.postretrieval.PriorityRerankPostProcessor;
import com.twithu.orderliabilityagent.rag.preretrieval.HistoryCompressionTransformer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.List;

/**
 * RAG（检索增强）Advisor工厂
 * <p>完整 modular RAG 链路：Query Transform（多轮压缩+改写）→ 向量检索（dimension 过滤）
 * → 优先级重排 → 上下文增强。基线重载不带 Query Transform，供召回评测对比。</p>
 */
public class RetrievalAdvisorFactory {

    /** 候选池 topK：先宽召回，再由重排截断到 5 条；参数待召回评测（Hit@K/MRR）调优 */
    private static final int RETRIEVAL_TOP_K = 10;
    /** 相似度 = 1 - COSINE_DISTANCE；候选池阈值放宽，精排交给重排器 */
    private static final double SIMILARITY_THRESHOLD = 0.50;

    private static final PromptTemplate REWRITE_TEMPLATE = new PromptTemplate("""
            你是酒店订单判责系统的查询改写模块。请把用户的口语化投诉改写为适合在{target}中检索的查询。
            要求：
            1. 保留投诉核心事实：争议信息（床型/早餐/电话等）、承诺值、实际情况
            2. 补充规则领域的规范术语，如"床型不一致""早餐未提供""酒店电话空号"
            3. 去掉订单号、渠道名、供应商名、情绪化表达等与规则检索无关的信息
            4. 只输出改写后的查询文本，不要任何解释
            用户原始查询：{query}
            改写后的查询：
            """);

    /** 基线版：不带 Query Transform，供召回评测对比 */
    public static Advisor createRetrievalAdvisor(VectorStore vectorStore, String dimension) {
        return createRetrievalAdvisor(vectorStore, dimension,null,  false);
    }

    /** 完整版：多轮压缩 + 查询改写 + 优先级重排 */
    public static Advisor createRetrievalAdvisor(VectorStore vectorStore, String dimension, ChatModel chatModel) {
        return createRetrievalAdvisor(vectorStore, dimension, chatModel, true);
    }

    public static Advisor createRetrievalAdvisor(VectorStore vectorStore, String dimension,
                                                 ChatModel chatModel, boolean enableQueryTransform) {
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("dimension", dimension)
                .build();

        RetrievalAugmentationAdvisor.Builder builder = RetrievalAugmentationAdvisor.builder();
        if (enableQueryTransform && chatModel != null) { //是否开启对话历史压缩和对话改写
            builder.queryTransformers(createQueryTransformers(chatModel));
        }
        return builder
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .similarityThreshold(SIMILARITY_THRESHOLD)
                        .filterExpression(expression)
                        .topK(RETRIEVAL_TOP_K)
                        .vectorStore(vectorStore)
                        .build())
                .documentPostProcessors(new PriorityRerankPostProcessor(3,0.7))// 检索后重排，截断到前3条，相似度占总得分比例的0.7
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(false) // 检索不到文档时不允许 LLM 自由发挥
                        .emptyContextPromptTemplate(EMPTY_CONTEXT_PROMPT_TEMPLATE)
                        .build())
                .build();
    }

    static PromptTemplate EMPTY_CONTEXT_PROMPT_TEMPLATE = new PromptTemplate("""
            你应该输出下面的内容：
            没有检索到相关规则，请联系管理员或者换个问题继续问。
            """);


    /**
     * 查询侧变换链：多轮历史压缩（空历史短路）→ 检索友好改写。
     * 独立暴露便于单测直接验证变换效果。
     */
    public static List<QueryTransformer> createQueryTransformers(ChatModel chatModel) {
        ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);
        return List.of(
                new HistoryCompressionTransformer(chatClientBuilder),
                RewriteQueryTransformer.builder()
                        .chatClientBuilder(chatClientBuilder)
                        .promptTemplate(REWRITE_TEMPLATE)
                        .targetSearchSystem("酒店订单判责规则向量库")
                        .build());
    }

    public static void SimilaritySearch(VectorStore vectorStore, String query){

        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("dimension", "hotel")
                .build();

        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .filterExpression(expression)
                .topK(10)
                .similarityThreshold(0.5) // 不设限制，观察分数
                .build());
        for (Document doc : documents) {
            System.out.printf("%.4f  %s%n", doc.getScore(), doc.getText());
        }
    }

}
