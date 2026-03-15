package com.psh.diet_planner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.psh.diet_planner.model.Food;
import com.psh.diet_planner.model.FoodRelationship;
import com.psh.diet_planner.model.Recipe;
import com.psh.diet_planner.dto.ImportProgress;
import com.psh.diet_planner.service.support.mcp.Neo4jMcpAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class FoodDataService {

    private static final Logger log = LoggerFactory.getLogger(FoodDataService.class);
    
    @Autowired
    private Neo4jMcpAdapter neo4jMcpAdapter;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private OnnxInferenceService onnxInferenceService;

    
    /**
     * 从JSON文件导入食物数据到Neo4j数据库
     */
    public void importFoodDataFromJson(String jsonFilePath) {
        importFoodDataFromJson(jsonFilePath, null, false);
    }

    /**
     * 从JSON文件导入食物数据（带进度跟踪和逐条容错）
     */
    public void importFoodDataFromJson(String jsonFilePath, ImportProgress progress, boolean autoEmbed) {
        if (progress != null) {
            progress.setPhase("导入食物: " + new File(jsonFilePath).getName());
        }
        try (java.io.InputStream in = new java.io.FileInputStream(new File(jsonFilePath))) {
            ObjectReader reader = objectMapper.readerFor(JsonNode.class);
            MappingIterator<JsonNode> it = reader.readValues(in);
            while (it.hasNext()) {
                JsonNode node = it.next();
                if (node.isArray()) {
                    for (JsonNode item : node) {
                        if (progress != null && progress.isCancelled()) return;
                        try {
                            importSingleFood(item, autoEmbed);
                            if (progress != null) {
                                progress.setSuccessCount(progress.getSuccessCount() + 1);
                                progress.setCurrentFileProcessedItems(progress.getCurrentFileProcessedItems() + 1);
                            }
                        } catch (Exception e) {
                            log.warn("导入食物失败: {}", e.getMessage());
                            if (progress != null) {
                                progress.setFailCount(progress.getFailCount() + 1);
                                progress.setCurrentFileProcessedItems(progress.getCurrentFileProcessedItems() + 1);
                                progress.addError("食物导入失败: " + e.getMessage());
                            }
                        }
                    }
                } else {
                    if (progress != null && progress.isCancelled()) return;
                    try {
                        importSingleFood(node, autoEmbed);
                        if (progress != null) {
                            progress.setSuccessCount(progress.getSuccessCount() + 1);
                            progress.setCurrentFileProcessedItems(progress.getCurrentFileProcessedItems() + 1);
                        }
                    } catch (Exception e) {
                        log.warn("导入食物失败: {}", e.getMessage());
                        if (progress != null) {
                            progress.setFailCount(progress.getFailCount() + 1);
                            progress.setCurrentFileProcessedItems(progress.getCurrentFileProcessedItems() + 1);
                            progress.addError("食物导入失败: " + e.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to import food data: " + e.getMessage(), e);
        }
    }
    
    /**
     * 批量导入食物数据
     * @param jsonDirectory 包含多个JSON文件的目录
     */
    public void importBatchFoodData(String jsonDirectory) throws IOException {
        File dir = new File(jsonDirectory);
        if (dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    importFoodDataFromJson(file.getAbsolutePath(), null, false);
                }
            }
        }
    }
    
    /**
     * 导入单个食物数据
     * @param foodNode JSON节点
     */
    private void importSingleFood(JsonNode foodNode, boolean autoEmbed) {
        String name = foodNode.get("食物名称").asText();
        Map<String, Object> existingFood = loadFoodByName(name);
        String existingNv = asString(existingFood.get("nutritionalValue"));
        String existingHb = asString(existingFood.get("healthBenefits"));
        String existingSf = asString(existingFood.get("suitableFor"));
        String existingCi = asString(existingFood.get("contraindications"));
        Map<String, String> existingNutrients = asStringMap(existingFood.get("nutrients"));

        // 合并基本属性：选择最长非空值
        String nv = getNonEmptyLongest(existingNv, textOf(foodNode, "营养价值"));
        String hb = getNonEmptyLongest(existingHb, textOf(foodNode, "食用功效"));
        String sf = getNonEmptyLongest(existingSf, textOf(foodNode, "适用人群"));
        String ci = getNonEmptyLongest(existingCi, textOf(foodNode, "禁忌人群"));

        // 合并营养成分 nutrients.* 到 CompositeProperty
        JsonNode nutrientsNode = foodNode.get("营养成分");
        Map<String, String> nutrients = existingNutrients != null ? new HashMap<>(existingNutrients) : new HashMap<>();
        if (nutrientsNode != null && nutrientsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = nutrientsNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                String val = entry.getValue().asText();
                String merged = getNonEmptyLongest(nutrients.get(key), val);
                if (merged != null && !merged.isEmpty()) {
                    nutrients.put(key, merged);
                }
            }
        }

        neo4jMcpAdapter.upsertFoodDetails(
            name,
            nullToEmpty(nv),
            nullToEmpty(hb),
            nullToEmpty(sf),
            nullToEmpty(ci),
            nutrients
        );

        // 处理食物关系（追加到现有列表，避免重复）
        JsonNode relationsNode = foodNode.get("食物关系");
        List<FoodRelationship> complementaryIncoming = new ArrayList<>();
        List<FoodRelationship> incompatibleIncoming = new ArrayList<>();
        List<FoodRelationship> overlapIncoming = new ArrayList<>();
        if (relationsNode != null) {
            JsonNode complementaryNode = relationsNode.get("互补");
            complementaryIncoming = parseFoodRelationships(complementaryNode);

            JsonNode incompatibleNode = relationsNode.get("互斥");
            incompatibleIncoming = parseFoodRelationships(incompatibleNode);

            // 处理重叠（功效重叠）关系
            JsonNode overlapNode = relationsNode.get("重叠");
            overlapIncoming = parseFoodRelationships(overlapNode);
        }

        // 关系改为 append-only：仅 MERGE 新关系，不覆盖、不删除历史关系
        mergeRelations(name, complementaryIncoming, incompatibleIncoming, overlapIncoming);

        // 冷启动：如果 ONNX 模型已加载，且开启自动补全，生成 sage_embedding
        if (autoEmbed) {
            tryGenerateEmbedding(name);
        }
    }

    private void mergeRelations(String sourceName,
                                List<FoodRelationship> complementary,
                                List<FoodRelationship> incompatible,
                                List<FoodRelationship> overlap) {
        for (FoodRelationship rel : complementary) {
            String target = rel.getTargetFood() == null ? null : rel.getTargetFood().getName();
            if (target != null && !target.isBlank()) {
                neo4jMcpAdapter.mergeComplementaryRelation(sourceName, target, nullToEmpty(rel.getDescription()));
            }
        }
        for (FoodRelationship rel : incompatible) {
            String target = rel.getTargetFood() == null ? null : rel.getTargetFood().getName();
            if (target != null && !target.isBlank()) {
                neo4jMcpAdapter.mergeIncompatibleRelation(sourceName, target, nullToEmpty(rel.getDescription()));
            }
        }
        for (FoodRelationship rel : overlap) {
            String target = rel.getTargetFood() == null ? null : rel.getTargetFood().getName();
            if (target != null && !target.isBlank()) {
                neo4jMcpAdapter.mergeOverlapRelation(sourceName, target, nullToEmpty(rel.getDescription()));
            }
        }
    }

    /**
     * 尝试用 ONNX 模型为食材生成 sage_embedding（静默失败）
     */
    private void tryGenerateEmbedding(String foodName) {
        if (onnxInferenceService == null || !onnxInferenceService.isModelLoaded()) return;
        try {
            onnxInferenceService.generateAndSave(foodName);
        } catch (Exception e) {
            log.debug("自动生成 embedding 跳过 [{}]: {}", foodName, e.getMessage());
        }
    }

    private String textOf(JsonNode node, String key) {
        JsonNode v = node.get(key);
        return v == null || v.isNull() ? null : v.asText();
    }

    private String getNonEmptyLongest(String a, String b) {
        String aa = (a == null) ? "" : a;
        String bb = (b == null) ? "" : b;
        if (aa.isEmpty()) return bb;
        if (bb.isEmpty()) return aa;
        return aa.length() >= bb.length() ? aa : bb;
    }

    /**
     * 解析食物关系
     * @param relationNode 关系节点
     * @return 食物关系列表
     */
    private List<FoodRelationship> parseFoodRelationships(JsonNode relationNode) {
        List<FoodRelationship> relationships = new ArrayList<>();
        if (relationNode != null && relationNode.isArray()) {
            for (JsonNode node : relationNode) {
                FoodRelationship relationship = new FoodRelationship();
                relationship.setDescription(node.path("描述").asText());
                
                // 查找或创建目标食物
                String targetFoodName = node.path("食物名称").asText();
                if (targetFoodName == null || targetFoodName.isBlank()) {
                    continue;
                }
                neo4jMcpAdapter.mergeFoodNode(targetFoodName);
                Food targetFood = new Food();
                targetFood.setName(targetFoodName);
                
                relationship.setTargetFood(targetFood);
                relationships.add(relationship);
            }
        }
        return relationships;
    }
    
    /**
     * 更新现有食物数据
     * @param jsonFilePath JSON文件路径
     */
    public void updateFoodDataFromJson(String jsonFilePath) throws IOException {
        JsonNode rootNode = objectMapper.readTree(new File(jsonFilePath));
        // 只做增量导入，不删除历史关系
        if (rootNode.isArray()) {
            for (JsonNode node : rootNode) {
                        importSingleFood(node, autoEmbed);
            }
        } else {
            importSingleFood(rootNode);
        }
    }

    /**
     * 从食谱JSON文件导入食谱与其所含食材（关联到Food）
     */
    public void importRecipesFromJson(String jsonFilePath) {
        importRecipesFromJson(jsonFilePath, null);
    }

    /**
     * 从食谱JSON文件导入食谱（带进度跟踪和逐条容错）
     */
    public void importRecipesFromJson(String jsonFilePath, ImportProgress progress) {
        if (progress != null) {
            progress.setPhase("导入食谱: " + new File(jsonFilePath).getName());
        }
        try (java.io.InputStream in = new java.io.FileInputStream(new File(jsonFilePath))) {
            com.fasterxml.jackson.databind.ObjectReader reader = objectMapper.readerFor(JsonNode.class);
            com.fasterxml.jackson.databind.MappingIterator<JsonNode> it = reader.readValues(in);
            while (it.hasNext()) {
                JsonNode node = it.next();
                if (node.isArray()) {
                    for (JsonNode recipeNode : node) {
                        if (progress != null && progress.isCancelled()) return;
                        try {
                            processSingleRecipe(recipeNode);
                            if (progress != null) {
                                progress.setSuccessCount(progress.getSuccessCount() + 1);
                                progress.setCurrentFileProcessedItems(progress.getCurrentFileProcessedItems() + 1);
                            }
                        } catch (Exception e) {
                            log.warn("导入食谱失败: {}", e.getMessage());
                            if (progress != null) {
                                progress.setFailCount(progress.getFailCount() + 1);
                                progress.setCurrentFileProcessedItems(progress.getCurrentFileProcessedItems() + 1);
                                progress.addError("食谱导入失败: " + e.getMessage());
                            }
                        }
                    }
                } else {
                    if (progress != null && progress.isCancelled()) return;
                    try {
                        processSingleRecipe(node);
                        if (progress != null) {
                            progress.setSuccessCount(progress.getSuccessCount() + 1);
                            progress.setCurrentFileProcessedItems(progress.getCurrentFileProcessedItems() + 1);
                        }
                    } catch (Exception e) {
                        log.warn("导入食谱失败: {}", e.getMessage());
                        if (progress != null) {
                            progress.setFailCount(progress.getFailCount() + 1);
                            progress.setCurrentFileProcessedItems(progress.getCurrentFileProcessedItems() + 1);
                            progress.addError("食谱导入失败: " + e.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to import recipes: " + e.getMessage(), e);
        }
    }

    private void processSingleRecipe(JsonNode recipeNode) {
        Recipe recipe = new Recipe();
        recipe.setName(getText(recipeNode, "名称", "菜谱名"));
        // 支持 ingredients 为字符串或数组/对象列表
        recipe.setIngredients(getTextOrJoinArray(recipeNode, new String[]{"ingredients","食材","配料","原料","ingredient_list"}, ","));
        // 保存“具体食材”详情（如有）
        recipe.setDetailedIngredients(getTextOrJoinArray(recipeNode, new String[]{"具体食材","详细食材","用料"}, " | "));
        // 步骤/做法介绍可能是数组，使用换行拼接
        recipe.setSteps(getTextOrJoinArray(recipeNode, new String[]{"steps","步骤","instructions","做法介绍"}, "\n"));

        // 将食材字符串拆分，尝试匹配到已存在的Food，再单独建 CONTAINS 关系
        String ingredientsStr = recipe.getIngredients();
        // 若“食材”缺失，尝试用“具体食材”作为后备，提取可能的食材名
        if (ingredientsStr == null || ingredientsStr.isEmpty()) {
            ingredientsStr = recipe.getDetailedIngredients();
        }

        if (recipe.getName() == null || recipe.getName().isEmpty()) {
            return;
        }

        neo4jMcpAdapter.upsertRecipe(
            recipe.getName(),
            nullToEmpty(recipe.getIngredients()),
            nullToEmpty(recipe.getDetailedIngredients()),
            nullToEmpty(recipe.getSteps())
        );

        if (ingredientsStr != null) {
            String[] parts = ingredientsStr.split("[,，;；、\\n]\\s*");
            for (String raw : parts) {
                String name = raw.trim();
                if (name.isEmpty()) continue;
                neo4jMcpAdapter.linkContains(recipe.getName(), name);
            }
        }
    }

    private Map<String, Object> loadFoodByName(String name) {
        return neo4jMcpAdapter.findFoodByName(name);
    }

    private Map<String, String> asStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> converted = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                converted.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return converted;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String getText(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && !v.isNull()) return v.asText();
        }
        return null;
    }

    private String getTextOrJoinArray(JsonNode node, String[] keys, String delimiter) {
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v == null || v.isNull()) continue;
            if (v.isArray()) {
                List<String> parts = new ArrayList<>();
                for (JsonNode item : v) {
                    if (item.isTextual()) {
                        parts.add(item.asText());
                    } else if (item.isObject()) {
                        // 常见结构：{"name":"..."} 或包含名称字段
                        String n = getText(item, "name", "食材", "原料", "label");
                        if (n != null && !n.isEmpty()) parts.add(n);
                    }
                }
                return String.join(delimiter, parts);
            } else {
                return v.asText();
            }
        }
        return null;
    }

    /**
     * 一键导入目录下所有食物和食谱 JSON 文件（带进度跟踪）
     * 规则：food*.json → 食物数据，recipe*.json → 食谱数据，其余忽略
     */
    public void importAllFromDataDir(String dataDir) {
        importAllFromDataDir(dataDir, null, false);
    }

    public void importAllFromDataDir(String dataDir, ImportProgress progress, boolean autoEmbed) {
        File dir = new File(dataDir);
        if (!dir.isDirectory()) {
            throw new RuntimeException("数据目录不存在: " + dataDir);
        }

        File[] jsonFiles = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (jsonFiles == null || jsonFiles.length == 0) {
            throw new RuntimeException("数据目录中没有 JSON 文件: " + dataDir);
        }

        // 先导入所有 food*.json，再导入所有 recipe*.json
        List<File> foodFiles = new ArrayList<>();
        List<File> recipeFiles = new ArrayList<>();
        for (File f : jsonFiles) {
            String lower = f.getName().toLowerCase();
            if (lower.startsWith("food")) {
                foodFiles.add(f);
            } else if (lower.startsWith("recipe")) {
                recipeFiles.add(f);
            } else {
                log.info("跳过未识别的文件: {}", f.getName());
            }
        }

        List<File> importFiles = new ArrayList<>();
        importFiles.addAll(foodFiles);
        importFiles.addAll(recipeFiles);
        if (progress != null) {
            progress.setTotalFiles(importFiles.size());
            progress.setProcessedFiles(0);
            progress.setFailedFiles(0);
            progress.setSuccessCount(0);
            progress.setFailCount(0);
            progress.setCurrentFileItems(0);
            progress.setCurrentFileProcessedItems(0);
            progress.setTotalItems(countTotalItems(importFiles));
            progress.setPhase(importFiles.isEmpty() ? "未发现可导入文件" : "已扫描到 " + importFiles.size() + " 个文件，准备开始导入");
        }

        for (File f : foodFiles) {
            if (progress != null) {
                if (progress.isCancelled()) {
                    return;
                }
                progress.setCurrentFileItems(countItemsInJson(f));
                progress.setCurrentFileProcessedItems(0);
                progress.setPhase("正在导入文件: " + f.getName());
            }
            log.info("导入食物文件: {}", f.getName());
            try {
                importFoodDataFromJson(f.getAbsolutePath(), progress, autoEmbed);
            } catch (RuntimeException e) {
                if (progress != null) {
                    progress.setFailedFiles(progress.getFailedFiles() + 1);
                    progress.addError("文件导入失败: " + f.getName() + " - " + e.getMessage());
                }
                throw e;
            } finally {
                if (progress != null) {
                    progress.setProcessedFiles(progress.getProcessedFiles() + 1);
                    progress.setCurrentFileProcessedItems(progress.getCurrentFileItems());
                }
            }
        }
        for (File f : recipeFiles) {
            if (progress != null) {
                if (progress.isCancelled()) {
                    return;
                }
                progress.setCurrentFileItems(countItemsInJson(f));
                progress.setCurrentFileProcessedItems(0);
                progress.setPhase("正在导入文件: " + f.getName());
            }
            log.info("导入食谱文件: {}", f.getName());
            try {
                importRecipesFromJson(f.getAbsolutePath(), progress);
            } catch (RuntimeException e) {
                if (progress != null) {
                    progress.setFailedFiles(progress.getFailedFiles() + 1);
                    progress.addError("文件导入失败: " + f.getName() + " - " + e.getMessage());
                }
                throw e;
            } finally {
                if (progress != null) {
                    progress.setProcessedFiles(progress.getProcessedFiles() + 1);
                    progress.setCurrentFileProcessedItems(progress.getCurrentFileItems());
                }
            }
        }
    }

    private int countTotalItems(List<File> files) {
        int total = 0;
        for (File file : files) {
            total += countItemsInJson(file);
        }
        return total;
    }

    public int countItemsInJsonFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return 0;
        }
        return countItemsInJson(new File(filePath));
    }

    private int countItemsInJson(File file) {
        int count = 0;
        try (java.io.InputStream in = new java.io.FileInputStream(file)) {
            ObjectReader reader = objectMapper.readerFor(JsonNode.class);
            MappingIterator<JsonNode> it = reader.readValues(in);
            while (it.hasNext()) {
                JsonNode node = it.next();
                if (node.isArray()) {
                    count += node.size();
                } else {
                    count += 1;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to count items: " + file.getName() + " - " + e.getMessage(), e);
        }
        return count;
    }

    
}
