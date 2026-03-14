package com.psh.diet_planner.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import com.psh.diet_planner.service.support.mcp.Neo4jMcpAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * ONNX Runtime 推理服务：利用训练好的 RGCN 模型为新食材生成 sage_embedding。
 * 解决冷启动问题：新食材通过邻居聚合获得有意义的向量表示。
 */
@Slf4j
@Service
public class OnnxInferenceService {

    private static final int FEATURE_DIM = 256;     // 4×64 metapath 拼接
    private static final int EMBEDDING_DIM = 128;    // sage_embedding 维度
    private static final int NUM_RELS = 4;           // 关系类型数

    // 关系索引与 diet_preparer.py 中 rel_map 一致
    private static final int REL_CONTAINS = 0;
    private static final int REL_COMPLEMENTARY = 1;
    private static final int REL_INCOMPATIBLE = 2;
    private static final int REL_OVERLAP = 3;

    private static final String[] EMB_KEYS = {"comp", "incomp", "overlap", "rfr"};
    private static final int EMB_DIM = 64;
    private static final long GLOBAL_MEAN_CACHE_TTL_MS = 5 * 60 * 1000L;

    private final Neo4jMcpAdapter neo4jMcpAdapter;

    @Value("${onnx.model.path:../GraphSAGE/rgcn_inference.onnx}")
    private String modelPath;

    private OrtEnvironment env;
    private OrtSession session;
    private volatile float[] globalMeanFeaturesCache;
    private volatile long globalMeanCacheTimeMs;

    public OnnxInferenceService(Neo4jMcpAdapter neo4jMcpAdapter) {
        this.neo4jMcpAdapter = neo4jMcpAdapter;
    }

    @PostConstruct
    public void init() {
        loadModel();
    }

    /**
     * 加载或重新加载 ONNX 模型
     */
    public synchronized void loadModel() {
        Path path = Paths.get(modelPath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            log.info("ONNX 模型尚未生成: {}，请先执行 GraphSAGE 训练", path);
            return;
        }
        try {
            // 关闭旧 session
            if (session != null) {
                session.close();
            }
            env = OrtEnvironment.getEnvironment();
            session = env.createSession(path.toString());
            log.info("ONNX 模型已加载: {}", path);
        } catch (OrtException e) {
            log.error("加载 ONNX 模型失败: {}", e.getMessage());
        }
    }

    public boolean isModelLoaded() {
        return session != null;
    }

    /**
     * 为指定食材生成 sage_embedding 并写入 Neo4j
     * @return 生成的 128 维向量，如果失败返回 null
     */
    public List<Double> generateAndSave(String foodName) {
        float[] embedding = generateEmbedding(foodName);
        if (embedding == null) return null;

        List<Double> result = new ArrayList<>(EMBEDDING_DIM);
        for (float v : embedding) result.add((double) v);

        // 写入 Neo4j
        neo4jMcpAdapter.saveSageEmbedding(foodName, result);
        log.info("已为食材 [{}] 生成并写入 sage_embedding (128d)", foodName);
        return result;
    }

    /**
     * 批量为多个食材生成 sage_embedding
     */
    public int generateBatch(List<String> foodNames) {
        int count = 0;
        for (String name : foodNames) {
            try {
                if (generateAndSave(name) != null) count++;
            } catch (Exception e) {
                log.warn("为 [{}] 生成 embedding 失败: {}", name, e.getMessage());
            }
        }
        return count;
    }

    /**
     * 为所有缺少 sage_embedding 的 Food 节点生成向量
     */
    public int generateForMissing() {
        if (!isModelLoaded()) {
            throw new IllegalStateException("ONNX 模型未加载，请先执行 GraphSAGE 训练");
        }
        List<String> missing = neo4jMcpAdapter.findFoodsMissingSageEmbedding();
        log.info("发现 {} 个食材缺少 sage_embedding，开始生成...", missing.size());
        return generateBatch(missing);
    }

    /**
     * 核心推理：根据食材名称，从 Neo4j 获取特征和邻居信息，运行 ONNX 模型
     */
    public float[] generateEmbedding(String foodName) {
        if (!isModelLoaded()) {
            throw new IllegalStateException("ONNX 模型未加载，请先执行 GraphSAGE 训练");
        }

        // 1. 获取目标节点自身的 metapath 特征
        float[] selfFeatures = getSelfFeatures(foodName);

        // 2. 获取各关系类型的邻居平均特征
        float[][] neighborFeatures = new float[NUM_RELS][FEATURE_DIM];
        fillNeighborFeatures(foodName, "COMPLEMENTARY", REL_COMPLEMENTARY, neighborFeatures);
        fillNeighborFeatures(foodName, "INCOMPATIBLE", REL_INCOMPATIBLE, neighborFeatures);
        fillNeighborFeatures(foodName, "OVERLAP", REL_OVERLAP, neighborFeatures);
        // CONTAINS: Food 节点没有出边（只有 Recipe→Food），所以保持零向量

        // 3. 运行 ONNX 推理
        try {
            float[][] nodeInput = new float[][] { selfFeatures };
            OnnxTensor nodeTensor = OnnxTensor.createTensor(env, nodeInput);
            OnnxTensor neighborTensor = OnnxTensor.createTensor(env, neighborFeatures);

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("node_feat", nodeTensor);
            inputs.put("rel_neighbor_feat", neighborTensor);

            try (OrtSession.Result result = session.run(inputs)) {
                float[][] output = (float[][]) result.get(0).getValue();
                return output[0]; // [128]
            } finally {
                nodeTensor.close();
                neighborTensor.close();
            }
        } catch (OrtException e) {
            log.error("ONNX 推理失败 [{}]: {}", foodName, e.getMessage());
            return null;
        }
    }

    /**
     * 获取食材自身的 256 维特征（4×64 metapath embedding 拼接）
     */
    private float[] getSelfFeatures(String foodName) {
        float[] features = new float[FEATURE_DIM];
        Map<String, Object> row = neo4jMcpAdapter.findFoodMetapathEmbeddings(foodName);
        if (row.isEmpty()) {
            return getGlobalMeanFeatures();
        }

        int offset = 0;
        boolean hasAnyMetapath = false;
        float[] globalMean = null;
        for (String key : EMB_KEYS) {
            List<?> values = asList(row.get(key));
            boolean hasThisEmbedding = values != null && !values.isEmpty();
            if (hasThisEmbedding) {
                hasAnyMetapath = true;
            } else if (globalMean == null) {
                globalMean = getGlobalMeanFeatures();
            }

            float[] partial = hasThisEmbedding
                ? parseEmbedding(values, EMB_DIM)
                : Arrays.copyOfRange(globalMean, offset, offset + EMB_DIM);
            System.arraycopy(partial, 0, features, offset, 64);
            offset += EMB_DIM;
        }

        if (!hasAnyMetapath) {
            return globalMean != null ? globalMean : getGlobalMeanFeatures();
        }
        return features;
    }

    /**
     * 获取全局均值特征（4×64），用于冷启动节点缺失 metapath 特征时回退。
     */
    private float[] getGlobalMeanFeatures() {
        long now = System.currentTimeMillis();
        float[] cached = globalMeanFeaturesCache;
        if (cached != null && (now - globalMeanCacheTimeMs) < GLOBAL_MEAN_CACHE_TTL_MS) {
            return cached;
        }
        synchronized (this) {
            cached = globalMeanFeaturesCache;
            if (cached != null && (now - globalMeanCacheTimeMs) < GLOBAL_MEAN_CACHE_TTL_MS) {
                return cached;
            }
            float[] computed = computeGlobalMeanFeatures();
            globalMeanFeaturesCache = computed;
            globalMeanCacheTimeMs = System.currentTimeMillis();
            return computed;
        }
    }

    /**
     * 从 Neo4j 计算全局均值特征：对 comp/incomp/overlap/rfr 各 64 维分别求均值。
     */
    private float[] computeGlobalMeanFeatures() {
        float[] sums = new float[FEATURE_DIM];
        int[] counts = new int[FEATURE_DIM];

        List<Map<String, Object>> records = neo4jMcpAdapter.findAllFoodMetapathEmbeddings();

        for (Map<String, Object> r : records) {
            int offset = 0;
            for (String key : EMB_KEYS) {
                List<?> list = asList(r.get(key));
                int len = Math.min(list.size(), EMB_DIM);
                for (int i = 0; i < len; i++) {
                    Object obj = list.get(i);
                    if (obj instanceof Number number) {
                        sums[offset + i] += number.floatValue();
                        counts[offset + i]++;
                    }
                }
                offset += EMB_DIM;
            }
        }

        float[] mean = new float[FEATURE_DIM];
        for (int i = 0; i < FEATURE_DIM; i++) {
            mean[i] = counts[i] > 0 ? (sums[i] / counts[i]) : 0f;
        }
        return mean;
    }

    /**
     * 获取指定关系类型的邻居特征并计算均值，填入 neighborFeatures 对应行
     */
    private void fillNeighborFeatures(String foodName, String relType, int relIndex, float[][] neighborFeatures) {
        List<Map<String, Object>> records = neo4jMcpAdapter.findNeighborMetapathEmbeddings(foodName, relType);

        if (records.isEmpty()) return; // 保持零向量

        // 累加所有邻居的特征
        float[] sum = new float[FEATURE_DIM];
        for (Map<String, Object> r : records) {
            int offset = 0;
            for (String key : EMB_KEYS) {
                float[] partial = parseEmbedding(asList(r.get(key)), 64);
                for (int i = 0; i < 64; i++) {
                    sum[offset + i] += partial[i];
                }
                offset += 64;
            }
        }

        // 计算均值
        int count = records.size();
        for (int i = 0; i < FEATURE_DIM; i++) {
            neighborFeatures[relIndex][i] = sum[i] / count;
        }
    }

    /**
     * 从 embedding 列表解析为 float 数组
     */
    private float[] parseEmbedding(List<?> list, int dim) {
        float[] result = new float[dim];
        if (list == null) {
            return result;
        }
        for (int i = 0; i < Math.min(list.size(), dim); i++) {
            Object value = list.get(i);
            if (value instanceof Number number) {
                result[i] = number.floatValue();
            }
        }
        return result;
    }

    private List<?> asList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (session != null) session.close();
        } catch (OrtException e) {
            log.warn("关闭 ONNX session 异常: {}", e.getMessage());
        }
    }
}
