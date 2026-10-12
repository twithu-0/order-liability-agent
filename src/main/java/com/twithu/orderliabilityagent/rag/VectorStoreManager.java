package com.twithu.orderliabilityagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class VectorStoreManager {

    @Resource
    DocumentLoader documentLoader;

    @Bean
    @Profile("!test") // 非测试环境激活
    public ApplicationRunner ruleDocumentInitializer(PgVectorStore pgVectorStore
            , @Qualifier("pgJdbcTemplate") JdbcTemplate pgJdbcTemplate) {
        return args -> {
            List<Document> documents = documentLoader.loadRules();

            // 启动前先查询一次向量数据库已有规则，跳过已有规则，避免每次启动都全量Embedding
            Map<String, String> existingHashes = new HashMap<>();
            pgJdbcTemplate.query("SELECT id, metadata->>'content_hash' AS content_hash FROM rule",
                    rs -> {
                        existingHashes.put(rs.getString("id"), rs.getString("content_hash"));
                    });

            List<Document> toUpsert = documents.stream()
                    .filter(doc -> !Objects.equals(existingHashes.get(doc.getId()),
                            doc.getMetadata().get("content_hash")))
                    .toList();

            if (toUpsert.isEmpty()) {
                log.info("规则向量库已是最新，跳过 embedding，共 {} 条", documents.size());
            } else {
                log.info("增量写入规则向量库：{}/{} 条", toUpsert.size(), documents.size());
                pgVectorStore.add(toUpsert); // 先embedding、后upset
            }
        };
    }

    /** 根据 ruleId 删除向量库规则 */ //TODO 抛到接口层去，设置管理员可调用
    public void deleteVectorStore(PgVectorStore pgVectorStore, List<String> ruleIdList) {
        try {
            List<String> uuidList = ruleIdList.stream()
                    .map(ruleId -> UUID.nameUUIDFromBytes(ruleId.getBytes(StandardCharsets.UTF_8)).toString())
                    .collect(Collectors.toList());

            pgVectorStore.delete(uuidList);
        }catch (Exception e){
            log.error("删除向量库规则时出错,ruleIdList:{},{}", ruleIdList, e.getMessage());
        }
    }

}
