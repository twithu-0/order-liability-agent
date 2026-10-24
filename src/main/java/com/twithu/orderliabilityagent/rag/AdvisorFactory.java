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
 */
public class AdvisorFactory {

    /** 候选池 topK：先宽召回，再由重排截断到 5 条；参数待召回评测（Hit@K/MRR）调优 */
    private static final int RETRIEVAL_TOP_K = 10;
    /** 相似度 = 1 - COSINE_DISTANCE；候选池阈值放宽，精排交给重排器 */
    private static final double SIMILARITY_THRESHOLD = 0.50;

    private static final PromptTemplate REWRITE_TEMPLATE = new PromptTemplate("""
            你是酒店订单判责系统的查询改写模块。请把用户的口语化投诉改写为适合在{target}中检索的查询。
            要求：
            1. 保留投诉核心事实：争议字段（床型/早餐/电话等）、承诺值、实际情况
            2. 适当补充（不要过多补充）规则领域的规范术语，如"床型不一致""早餐未提供""酒店电话空号"
            3. 去掉具体酒店名、电话号码、订单号、渠道名、供应商名、情绪化表达等与规则检索无关的信息
            3. 涉及到“渠道”“供应商”“酒店”等作为主语，应保留主语字段
            4. 只输出改写后的查询文本，不要任何解释，不要加多余的文本（如原输入只描述了问题，不要自行加“导致下单错误”、“建议xx判定规则”类似字样）
            用户原始查询：{query}
            改写后的查询：
            """);

    /**
     * query 压缩对话历史、规范改写用户输入的Advisor
     * @param chatModel
     * @return
     */
    public static Advisor createRewriteAdvisor(ChatModel chatModel) {
        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(createRewriteTransformer(chatModel))
                .build();
    }


    /**
     * 查询侧变换链：多轮历史压缩（空历史短路）→ 检索友好改写。
     * 独立暴露便于单测直接验证变换效果。
     * 此处的 chatModel 只服务查询侧，与判责模型无关，可传轻量模型降低成本。
     */
    public static QueryTransformer createCompressionTransformer(ChatModel chatModel) {
        ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);
        return new HistoryCompressionTransformer(chatClientBuilder);
    }

    public static QueryTransformer createRewriteTransformer(ChatModel chatModel) {
        ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);
        return RewriteQueryTransformer.builder()
                        .chatClientBuilder(chatClientBuilder)
                        .promptTemplate(REWRITE_TEMPLATE)
                        .targetSearchSystem("酒店订单判责规则向量库")
                        .build();
    }

    public static Advisor createRetrievalAdvisor(VectorStore vectorStore, String dimension) {
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("dimension", dimension)
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .similarityThreshold(SIMILARITY_THRESHOLD)
                        .filterExpression(expression)
                        .topK(RETRIEVAL_TOP_K)
                        .vectorStore(vectorStore)
                        .build())
                .documentPostProcessors(new PriorityRerankPostProcessor(3, 0.7))// 检索后重排，截断到前3条，相似度占总得分比例的0.7
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
