package com.twithu.orderliabilityagent.rag;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.List;

/**
 * RAG（检索增强）Advisor工厂
 */
public class RetrievalAdvisorFactory {

    public static Advisor createRetrievalAdvisor(VectorStore vectorStore, String dimension) {
        // 过滤条件
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("dimension", dimension)
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .similarityThreshold(0.50) //相似度阈值 pgVectorStore的距离类型默认为COSINE_DISTANCE，在此规则下相似度=1-距离
                        .filterExpression(expression)
                        .topK(3) // 返回的文档数量
                        .vectorStore(vectorStore)
                        .build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(false)
                        .emptyContextPromptTemplate(EMPTY_CONTEXT_PROMPT_TEMPLATE) // 如果允许上下文为空，设置的回答prompt模版
                        .build())
                .build();

    }

    static PromptTemplate EMPTY_CONTEXT_PROMPT_TEMPLATE = new PromptTemplate("""
            你应该输出下面的内容：
            你似乎没有输入任何内容，请检查一下哦~
            """);

    public static void SimilaritySearch(VectorStore vectorStore, String query){
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(20)
                .similarityThreshold(0) // 不设限制，观察分数
                .build());
        for (Document doc : documents) {
            System.out.printf("%.4f  %s%n", doc.getScore(), doc.getText());
        }
    }

}
