package com.twithu.orderliabilityagent.rag;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DocumentLoaderTest {

    @Autowired
    DocumentLoader documentLoader;

    @Test
    void loadJsonAsDocuments() {
        List<Document> documents = documentLoader.loadJsonAsDocuments("content_md","dimension","rule_id");
        Assertions.assertNotNull(documents);
    }
}