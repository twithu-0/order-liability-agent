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
        // 结构化字段全量进 metadata：dimension 供检索过滤，priority 供重排，issue_type/evidence_required 供判责与评测
        List<Document> documents = loadJsonAsDocuments("content_md",
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
