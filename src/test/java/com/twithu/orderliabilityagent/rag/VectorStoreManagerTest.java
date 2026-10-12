package com.twithu.orderliabilityagent.rag;

import com.twithu.orderliabilityagent.MyTest;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@MyTest
class VectorStoreManagerTest {
    @Resource
    VectorStoreManager vectorStoreManager;

    @Resource
    PgVectorStore pgVectorStore;
    @Test
    void deleteVectorStore() {
        vectorStoreManager.deleteVectorStore(pgVectorStore, List.of("H-01","H-02","H-03","xxxxt"));
    }
}