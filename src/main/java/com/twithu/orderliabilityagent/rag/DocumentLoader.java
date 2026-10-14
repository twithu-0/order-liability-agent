package com.twithu.orderliabilityagent.rag;

import com.twithu.orderliabilityagent.rag.transform.RuleMetadataEnricher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@Slf4j
public class DocumentLoader {

    private final ResourcePatternResolver resourcePatternResolver;
    private final RuleMetadataEnricher ruleMetadataEnricher;

    DocumentLoader(ResourcePatternResolver resourcePatternResolver, RuleMetadataEnricher ruleMetadataEnricher){
        this.resourcePatternResolver = resourcePatternResolver;
        this.ruleMetadataEnricher = ruleMetadataEnricher;
    }


    public List<Document> loadRules(){
        // 检索表示与判责表示分离：只嵌入场景描述(name)+口语别名，机器字段进 metadata 不参与向量化；
        // 规则全文 content_md 留在 MySQL，P7 判责时按 rule_id 回查注入 prompt
        List<Document> documents = loadJsonAsDocuments("name",
                "dimension", "rule_id", "issue_type", "priority", "evidence_required");
        // 领域 Transform：口语别名扩充文本（在计算 content_hash 之前，保证幂等键反映最终入库内容）
        documents = ruleMetadataEnricher.apply(documents);
        // 给每个chunk设置id，防止重复插入数据库
        List<Document> retList = new ArrayList<>();
        for (Document doc : documents) {
            String id = doc.getMetadata().get("rule_id").toString();
            String uuid = UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)).toString();//必须设置为uuid
            String contentHash = DigestUtils.md5DigestAsHex(doc.getText().getBytes(StandardCharsets.UTF_8)); // 内容和id幂等性校验
            retList.add(doc.mutate().id(uuid).metadata("content_hash", contentHash).build());
        }
        return retList;
    }

    /**
     * 加载json对象或json数组的某个属性值为Document对象
     * @param targetKey 目标属性值的key
     * @param metadataKeys 设置为元数据的json key
     * */
    List<Document> loadJsonAsDocuments(String targetKey, String... metadataKeys) {
        Resource resource = loadResource("classpath:docs/rule.json");
        JsonReader jsonReader = new JsonReader(resource
                , json -> {
            Map<String, Object> map = new HashMap<>();
            for (String metadataKey : metadataKeys) {
                map.put(metadataKey, json.get(metadataKey));
            }
            return map;
        }
                , targetKey);
        return jsonReader.get();
    }

    public Resource loadResource(String location){
        return resourcePatternResolver.getResource(location);
    }

}
