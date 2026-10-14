package com.twithu.orderliabilityagent.rag.eval;

import com.twithu.orderliabilityagent.MyTest;
import com.twithu.orderliabilityagent.rag.AdvisorFactory;
import com.twithu.orderliabilityagent.rag.postretrieval.PriorityRerankPostProcessor;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 召回评测运行器。前置条件：向量库已灌入最新规则（先正常启动一次应用，测试环境不灌库）。
 * 三个测试由浅入深：
 *   baselineGridSearch：纯检索参数网格，不调 LLM，秒级~分钟级，先跑这个
 *   productionPathWithRerank：对比"纯检索"与"检索+重排"的完整生产路径
 *   queryTransformComparison：对比查询改写前后的召回，每条样本调一次 LLM
 */
@MyTest
class RetrievalEvaluationTest {

    @Resource
    PgVectorStore pgVectorStore;

    @Resource
    ChatModel ollamaChatModel;

    /**
     * 模型分级使用：查询侧生产上应挂轻量模型降本，判责侧用强模型保质量。
     * 当前本地 qwen2.5:3b 改写偏离原义不可用，评测暂用 DeepSeek 验证改写收益上限；
     * 换更强的本地模型（如 qwen3:4b-instruct）后可切回，createQueryTransformers 接口不变。
     */
    @Resource
    ChatModel deepSeekChatModel;

    private List<EvalCase> loadCases() throws IOException {
        try (InputStream in = new ClassPathResource("eval/retrieval_eval.json").getInputStream()) {
            return JsonMapper.builder().build().readValue(in, new TypeReference<List<EvalCase>>() {});
        }
    }

    /** 1. 参数网格：topK × threshold，选 Hit@K 与 MRR 综合最优的配置 */
    @Test
    void baselineGridSearch() throws IOException {
        List<EvalCase> cases = loadCases();
        RetrievalEvaluator evaluator = new RetrievalEvaluator(pgVectorStore);

        List<EvalReport> reports = evaluator.gridSearch(cases,
                new int[]{3, 5}, new double[]{0.6, 0.7, 0.8}, null);
        System.out.println("==== 纯检索参数网格（样本数 " + cases.size() + "）====");
        RetrievalEvaluator.printTable(reports);

        // 生产当前配置（k10,t0.5）的失败明细与分维度命中率
        EvalReport production = evaluator.evaluate("raw(k10,t0.5)", cases, 10, 0.5);
        System.out.println("分维度命中率: " + production.hitByDimension());
        RetrievalEvaluator.printFailures(production);
    }

    /** 2. 生产路径：检索 + 优先级重排(截断3条, 相似度权重0.7)，与 AdvisorFactory 当前配置一致 */
    @Test
    void productionPathWithRerank() throws IOException {
        List<EvalCase> cases = loadCases();
        RetrievalEvaluator evaluator = new RetrievalEvaluator(pgVectorStore);

        EvalReport raw = evaluator.evaluate("raw(k5,t0.65)", cases, 5, 0.65);
        EvalReport reranked = evaluator.evaluate("rerank(k5,t0.65)", cases, 5, 0.65,
                new PriorityRerankPostProcessor(3, 0.7));
        RetrievalEvaluator.printTable(List.of(raw, reranked));
        RetrievalEvaluator.printFailures(reranked);
    }

    /** 3. 查询改写对比：每条样本先经 RewriteQueryTransformer 改写再检索（DeepSeek，样本数次调用） */
    @Test
    void queryTransformComparison() throws IOException {
        List<EvalCase> cases = loadCases();
        RetrievalEvaluator evaluator = new RetrievalEvaluator(pgVectorStore);
        List<QueryTransformer> transformers = AdvisorFactory.createQueryTransformers(deepSeekChatModel);

        List<EvalCase> rewritten = new ArrayList<>();
        for (EvalCase c : cases) {
            Query query = Query.builder().text(c.query()).build();
            System.out.println("原输入: " + c.query());
            for (QueryTransformer t : transformers) {
                query = t.transform(query);
            }
            System.out.println("压缩改写后: " + query.text() + "\n");
            rewritten.add(new EvalCase(query.text(), c.dimension(), c.expected(), c.note()));
        }

        EvalReport before = evaluator.evaluate("before(k5,t0.65)", cases, 5, 0.60);
        EvalReport after = evaluator.evaluate("rewritten(k5,t0.65)", rewritten, 5, 0.60);
        RetrievalEvaluator.printTable(List.of(before, after));
        System.out.println("改写前未命中:");
        RetrievalEvaluator.printFailures(before);
        System.out.println("改写后仍未命中:");
        RetrievalEvaluator.printFailures(after);
    }
}
