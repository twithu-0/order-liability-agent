package com.twithu.orderliabilityagent.config;

import com.twithu.orderliabilityagent.rag.DocumentLoader;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 *  PgVectorStore 配置类
 */
@Slf4j
@Configuration
public class PgVectorStoreConfig {

    @Resource
    private DocumentLoader documentLoader;

    @Bean
    public PgVectorStore pgVectorStore(@Qualifier("pgJdbcTemplate") JdbcTemplate pgJdbcTemplate,
                                       EmbeddingModel embeddingModel){
        PgVectorStore vectorStore = PgVectorStore.builder(pgJdbcTemplate, embeddingModel)
                .dimensions(1024)  // 必须等于 qwen3-embedding:0.6b 的输出维度
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)  // 首次启动自动建表；生产建议改为手动 DDL
                .schemaName("public")
                .vectorTableName("rule")
                .maxDocumentBatchSize(10000)
                .build();

        return vectorStore;
    }
}
