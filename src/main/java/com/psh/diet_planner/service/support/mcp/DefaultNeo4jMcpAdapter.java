package com.psh.diet_planner.service.support.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psh.diet_planner.config.McpNeo4jProperties;
import com.psh.diet_planner.dto.RelationExploreDTO;
import com.psh.diet_planner.model.Recipe;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class DefaultNeo4jMcpAdapter implements Neo4jMcpAdapter {

    private static final Set<String> ALLOWED_REL_TYPES = Set.of("COMPLEMENTARY", "INCOMPATIBLE", "OVERLAP");

    private final McpNeo4jProperties properties;
    private final ObjectMapper objectMapper;
    private final Semaphore concurrencySemaphore;

    private volatile McpSyncClient client;

    public DefaultNeo4jMcpAdapter(McpNeo4jProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.concurrencySemaphore = new Semaphore(Math.max(1, properties.getMaxConcurrency()));
    }

    @Override
    public List<Map<String, Object>> executeCypher(String query, Map<String, Object> params) {
        Map<String, Object> payload = callToolForPayload(
            properties.getToolCypherQuery(),
            Map.of("query", query, "params", safeParams(params))
        );
        return toRows(payload);
    }

    @Override
    public int executeWrite(String query, Map<String, Object> params) {
        Map<String, Object> payload = callToolForPayload(
            properties.getToolCypherWrite(),
            Map.of("query", query, "params", safeParams(params))
        );
        return asInt(payload.getOrDefault("updated", 0));
    }

    @Override
    public List<Map<String, Object>> executeInTransaction(List<CypherStatement> statements) {
        List<Map<String, Object>> statementList = statements.stream()
            .map(stmt -> Map.<String, Object>of(
                "query", stmt.query(),
                "params", safeParams(stmt.params())
            ))
            .toList();

        Map<String, Object> payload = callToolForPayload(
            properties.getToolCypherTransaction(),
            Map.of("statements", statementList)
        );
        return toRows(payload);
    }

    @Override
    public int executeBatchWrites(List<CypherStatement> statements) {
        Map<String, Object> payload = callToolForPayload(
            properties.getToolCypherTransaction(),
            Map.of(
                "statements", statements.stream()
                    .map(stmt -> Map.<String, Object>of(
                        "query", stmt.query(),
                        "params", safeParams(stmt.params())
                    ))
                    .toList(),
                "writeOnly", true
            )
        );
        return asInt(payload.getOrDefault("updated", 0));
    }

    @Override
    public Map<String, Object> healthCheck() {
        return callToolForPayload(
            properties.getToolDietDispatch(),
            Map.of("op", "healthCheck", "args", Map.of())
        );
    }

    @Override
    public List<Map<String, Object>> findPairingCandidates(String foodName, int limit) {
        return callRows("findPairingCandidates", Map.of("foodName", foodName, "limit", limit));
    }

    @Override
    public List<String> findSmartPairings(String foodName, int limit) {
        return callRows("findSmartPairings", Map.of("foodName", foodName, "limit", limit)).stream()
            .map(row -> (String) row.get("name"))
            .filter(StringUtils::hasText)
            .toList();
    }

    @Override
    public List<String> findRecipesByIngredients(List<String> ingredients, int limit) {
        return callRows("findRecipesByIngredients", Map.of("ingredients", ingredients, "limit", limit)).stream()
            .map(row -> (String) row.get("name"))
            .filter(StringUtils::hasText)
            .toList();
    }

    @Override
    public List<String> checkIncompatibilities(List<String> foodNames) {
        return callRows("checkIncompatibilities", Map.of("foodNames", foodNames)).stream()
            .map(row -> (String) row.get("warning"))
            .filter(StringUtils::hasText)
            .toList();
    }

    @Override
    public void saveSageEmbedding(String foodName, List<Double> embedding) {
        callUpdated("saveSageEmbedding", Map.of("foodName", foodName, "embedding", embedding));
    }

    @Override
    public List<String> findFoodsMissingSageEmbedding() {
        return callRows("findFoodsMissingSageEmbedding", Map.of()).stream()
            .map(row -> (String) row.get("name"))
            .filter(StringUtils::hasText)
            .toList();
    }

    @Override
    public Map<String, Object> findFoodMetapathEmbeddings(String foodName) {
        List<Map<String, Object>> rows = callRows("findFoodMetapathEmbeddings", Map.of("foodName", foodName));
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    @Override
    public List<Map<String, Object>> findAllFoodMetapathEmbeddings() {
        return callRows("findAllFoodMetapathEmbeddings", Map.of());
    }

    @Override
    public List<Map<String, Object>> findNeighborMetapathEmbeddings(String foodName, String relationType) {
        if (!ALLOWED_REL_TYPES.contains(relationType)) {
            throw new IllegalArgumentException("不支持的关系类型: " + relationType);
        }
        return callRows("findNeighborMetapathEmbeddings", Map.of("foodName", foodName, "relationType", relationType));
    }

    @Override
    public List<Map<String, Object>> findRecipeNeighborEmbeddings(String foodName) {
        return callRows("findRecipeNeighborEmbeddings", Map.of("foodName", foodName));
    }

    @Override
    public Map<String, Object> loadFoodEmbeddings(String foodName) {
        List<Map<String, Object>> rows = callRows("loadFoodEmbeddings", Map.of("foodName", foodName));
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    @Override
    public List<Map<String, Object>> findFoodSimilarityByIndex(String foodName, String indexName, int limit) {
        return callRows("findFoodSimilarityByIndex", Map.of(
            "foodName", foodName,
            "indexName", indexName,
            "limit", limit
        ));
    }

    @Override
    public List<RelationExploreDTO> exploreFoodRelations(String foodName, String indexName, String relationType, int limit) {
        List<Map<String, Object>> rows = callRows("exploreFoodRelations", Map.of(
            "foodName", foodName,
            "indexName", indexName,
            "relationType", relationType,
            "limit", limit
        ));
        return rows.stream().map(this::toRelationExploreDto).toList();
    }

    @Override
    public List<String> findRelationsBetween(String sourceFood, String targetFood) {
        return callRows("findRelationsBetween", Map.of("sourceFood", sourceFood, "targetFood", targetFood)).stream()
            .map(row -> asString(row.get("relation")))
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
    }

    @Override
    public List<Map<String, Object>> findSimilarRecipesByName(String recipeName, int limit) {
        return callRows("findSimilarRecipesByName", Map.of("recipeName", recipeName, "limit", limit));
    }

    @Override
    public List<Recipe> recommendPathBasedRecipes(String userId, int limit) {
        List<Map<String, Object>> rows = callRows("recommendPathBasedRecipes", Map.of("userId", userId, "limit", limit));
        return rows.stream().map(this::toRecipe).toList();
    }

    @Override
    public List<Recipe> recommendEmbeddingBasedRecipes(String userId, int limit, int searchLimit) {
        List<Map<String, Object>> rows = callRows("recommendEmbeddingBasedRecipes", Map.of(
            "userId", userId,
            "limit", limit,
            "searchLimit", searchLimit
        ));
        return rows.stream().map(this::toRecipe).toList();
    }

    @Override
    public List<Recipe> searchRecipesByName(String keyword) {
        List<Map<String, Object>> rows = callRows("searchRecipesByName", Map.of("keyword", keyword));
        return rows.stream().map(this::toRecipe).toList();
    }

    @Override
    public Recipe findRecipeByIdOrName(String idOrName) {
        List<Map<String, Object>> rows = callRows("findRecipeByIdOrName", Map.of("idOrName", idOrName));
        if (rows.isEmpty()) {
            return null;
        }
        return toRecipe(rows.get(0));
    }

    @Override
    public void likeRecipe(String userId, String recipeId) {
        callUpdated("likeRecipe", Map.of("userId", userId, "recipeId", recipeId));
    }

    @Override
    public void dislikeRecipe(String userId, String recipeId) {
        callUpdated("dislikeRecipe", Map.of("userId", userId, "recipeId", recipeId));
    }

    @Override
    public void favoriteIngredient(String userId, String ingredientName) {
        callUpdated("favoriteIngredient", Map.of("userId", userId, "ingredientName", ingredientName));
    }

    @Override
    public void dislikeIngredient(String userId, String ingredientName, String reason) {
        callUpdated("dislikeIngredient", Map.of(
            "userId", userId,
            "ingredientName", ingredientName,
            "reason", reason
        ));
    }

    @Override
    public Map<String, Object> findFoodByName(String name) {
        List<Map<String, Object>> rows = callRows("findFoodByName", Map.of("name", name));
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    @Override
    public void mergeFoodNode(String name) {
        callUpdated("mergeFoodNode", Map.of("name", name));
    }

    @Override
    public void upsertFoodDetails(String name,
                                  String nutritionalValue,
                                  String healthBenefits,
                                  String suitableFor,
                                  String contraindications,
                                  Map<String, String> nutrients) {
        callUpdated("upsertFoodDetails", Map.of(
            "name", name,
            "nutritionalValue", safeString(nutritionalValue),
            "healthBenefits", safeString(healthBenefits),
            "suitableFor", safeString(suitableFor),
            "contraindications", safeString(contraindications),
            "nutrients", nutrients == null ? Map.of() : nutrients
        ));
    }

    @Override
    public void deleteFoodByName(String name) {
        callUpdated("deleteFoodByName", Map.of("name", name));
    }

    @Override
    public long countFoods() {
        return singleCountFromOp("countFoods");
    }

    @Override
    public long countRecipes() {
        return singleCountFromOp("countRecipes");
    }

    @Override
    public long countComplementary() {
        return singleCountFromOp("countComplementary");
    }

    @Override
    public long countIncompatible() {
        return singleCountFromOp("countIncompatible");
    }

    @Override
    public long countContainsRelations() {
        return singleCountFromOp("countContainsRelations");
    }

    @Override
    public Map<String, Object> loadRelationSummary(String name) {
        List<Map<String, Object>> rows = callRows("loadRelationSummary", Map.of("name", name));
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    @Override
    public List<RelationExploreDTO> exploreGeneralRelations(String foodName, int limit) {
        List<Map<String, Object>> rows = callRows("exploreGeneralRelations", Map.of("foodName", foodName, "limit", limit));
        return rows.stream().map(this::toRelationExploreDto).toList();
    }

    @Override
    public void mergeComplementaryRelation(String source, String target, String description) {
        callUpdated("mergeComplementaryRelation", Map.of(
            "source", source,
            "target", target,
            "description", safeString(description)
        ));
    }

    @Override
    public void mergeIncompatibleRelation(String source, String target, String description) {
        callUpdated("mergeIncompatibleRelation", Map.of(
            "source", source,
            "target", target,
            "description", safeString(description)
        ));
    }

    @Override
    public void mergeOverlapRelation(String source, String target, String description) {
        callUpdated("mergeOverlapRelation", Map.of(
            "source", source,
            "target", target,
            "description", safeString(description)
        ));
    }

    @Override
    public Recipe upsertRecipe(String name, String ingredients, String detailedIngredients, String steps) {
        List<Map<String, Object>> rows = callRows("upsertRecipe", Map.of(
            "name", name,
            "ingredients", safeString(ingredients),
            "detailedIngredients", safeString(detailedIngredients),
            "steps", safeString(steps)
        ));
        return rows.isEmpty() ? null : toRecipe(rows.get(0));
    }

    @Override
    public void linkContains(String recipeName, String foodName) {
        callUpdated("linkContains", Map.of("recipeName", recipeName, "foodName", foodName));
    }

    private List<Map<String, Object>> callRows(String op, Map<String, Object> args) {
        Map<String, Object> payload = callToolForPayload(
            properties.getToolDietDispatch(),
            Map.of("op", op, "args", safeParams(args))
        );
        return toRows(payload);
    }

    private int callUpdated(String op, Map<String, Object> args) {
        Map<String, Object> payload = callToolForPayload(
            properties.getToolDietDispatch(),
            Map.of("op", op, "args", safeParams(args))
        );
        return asInt(payload.getOrDefault("updated", 0));
    }

    private long singleCountFromOp(String op) {
        List<Map<String, Object>> rows = callRows(op, Map.of());
        if (rows == null || rows.isEmpty()) {
            return 0L;
        }
        return asLong(rows.get(0).get("c"));
    }

    private McpSyncClient ensureClient() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("MCP Neo4j 适配器已禁用，请检查 app.mcp.neo4j.enabled 配置");
        }
        McpSyncClient existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (client != null) {
                return client;
            }
            HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(properties.getBaseUrl())
                .endpoint(properties.getEndpoint())
                .connectTimeout(Duration.ofMillis(Math.max(500, properties.getConnectTimeoutMs())))
                .customizeRequest(builder -> applyHeaders(builder, properties.getApiKey()))
                .build();

            McpSyncClient created = McpClient.sync(transport)
                .requestTimeout(Duration.ofMillis(Math.max(500, properties.getRequestTimeoutMs())))
                .initializationTimeout(Duration.ofMillis(Math.max(500, properties.getInitializationTimeoutMs())))
                .enableCallToolSchemaCaching(true)
                .build();
            created.initialize();
            this.client = created;
            return created;
        }
    }

    private void applyHeaders(HttpRequest.Builder builder, String apiKey) {
        if (StringUtils.hasText(apiKey)) {
            builder.header("Authorization", "Bearer " + apiKey.trim());
            builder.header("X-Api-Key", apiKey.trim());
        }
    }

    private Map<String, Object> callToolForPayload(String toolName, Map<String, Object> args) {
        Exception last = null;
        for (int attempt = 1; attempt <= Math.max(1, properties.getMaxRetries()); attempt++) {
            boolean acquired = false;
            try {
                acquired = concurrencySemaphore.tryAcquire(Math.max(500, properties.getRequestTimeoutMs()), TimeUnit.MILLISECONDS);
                if (!acquired) {
                    throw new IllegalStateException("MCP 并发已达上限，等待超时");
                }
                McpSchema.CallToolResult result = ensureClient().callTool(new McpSchema.CallToolRequest(toolName, args));
                if (result == null) {
                    throw new IllegalStateException("MCP 工具返回 null 结果: " + toolName);
                }
                if (log.isDebugEnabled()) {
                    try {
                        log.debug("MCP callTool '{}' raw content: {}", toolName, objectMapper.writeValueAsString(result.content()));
                    } catch (Exception e) {
                        log.debug("MCP callTool '{}' raw content toString: {}", toolName, result.content(), e);
                    }
                }
                if (Boolean.TRUE.equals(result.isError())) {
                    throw new IllegalStateException("MCP 工具执行失败: " + extractText(result));
                }
                String txt = extractText(result);
                if (!StringUtils.hasText(txt)) {
                    log.warn("MCP 工具 '{}' 返回空内容（可能没有 outputSchema 或执行异常）", toolName);
                    throw new IllegalStateException("MCP 工具返回空内容: " + toolName);
                }
                return extractPayload(result);
            } catch (Exception ex) {
                last = ex;
                if (attempt >= Math.max(1, properties.getMaxRetries())) {
                    break;
                }
                sleepBackoff(attempt);
            } finally {
                if (acquired) {
                    concurrencySemaphore.release();
                }
            }
        }
        throw new IllegalStateException("调用 MCP 工具失败: " + toolName, last);
    }

    private void sleepBackoff(int attempt) {
        long base = Math.max(50L, properties.getRetryBackoffMs());
        long wait = Math.min(3000L, base * attempt);
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MCP 重试等待被中断", e);
        }
    }

    private String extractText(McpSchema.CallToolResult result) {
        if (result == null || result.content() == null || result.content().isEmpty()) {
            return "";
        }
        Object first = result.content().get(0);
        if (first instanceof McpSchema.TextContent textContent) {
            return textContent.text();
        }
        try {
            // 尝试将非文本的结构化内容序列化为 JSON 字符串，便于后续解析
            return objectMapper.writeValueAsString(first);
        } catch (Exception e) {
            // 回退到使用 toString，避免空返回导致不必要的重试
            log.debug("无法将 MCP 返回的首项内容序列化为 JSON，使用 toString 回退", e);
            return String.valueOf(first);
        }
    }

    private Map<String, Object> extractPayload(McpSchema.CallToolResult result) {
        String text = extractText(result);
        if (!StringUtils.hasText(text)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
        } catch (Exception parseMapError) {
            try {
                List<Map<String, Object>> rows = objectMapper.readValue(text, new TypeReference<List<Map<String, Object>>>() {});
                return Map.of("rows", rows);
            } catch (Exception parseListError) {
                log.debug("MCP 返回内容非 JSON，按纯文本处理: {}", text);
                return Map.of("text", text);
            }
        }
    }

    private List<Map<String, Object>> toRows(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return List.of();
        }
        Object rowsObj = payload.get("rows");
        if (rowsObj instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        row.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    rows.add(row);
                }
            }
            return rows;
        }
        if (payload.containsKey("name") || payload.containsKey("score") || payload.containsKey("warning")) {
            return List.of(payload);
        }
        return Collections.emptyList();
    }

    private Map<String, Object> safeParams(Map<String, Object> params) {
        return params == null ? Map.of() : params;
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Recipe toRecipe(Map<String, Object> row) {
        Recipe recipe = new Recipe();
        recipe.setId(asString(row.get("id")));
        recipe.setName(asString(row.get("name")));
        recipe.setIngredients(asString(row.get("ingredients")));
        recipe.setDetailedIngredients(asString(row.get("detailedIngredients")));
        recipe.setSteps(asString(row.get("steps")));
        return recipe;
    }

    private RelationExploreDTO toRelationExploreDto(Map<String, Object> row) {
        List<String> relatedRecipes = List.of();
        Object recipeObj = row.get("relatedRecipes");
        if (recipeObj instanceof List<?> list) {
            List<String> converted = new ArrayList<>();
            for (Object item : list) {
                String value = asString(item);
                if (StringUtils.hasText(value)) {
                    converted.add(value);
                }
            }
            relatedRecipes = converted;
        }

        RelationExploreDTO dto = new RelationExploreDTO();
        dto.setRelationType(asString(row.get("relationType")));
        dto.setTarget(asString(row.get("target")));
        Object scoreObj = row.get("score");
        dto.setScore(scoreObj instanceof Number number ? number.doubleValue() : 0D);
        dto.setRelatedRecipes(relatedRecipes);
        return dto;
    }
}
