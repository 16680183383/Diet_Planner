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
    public List<Map<String, Object>> findSemanticSubstitutes(String foodName, int limit) {
        String query = """
            MATCH (f:Food {name: $foodName})
            CALL db.index.vector.queryNodes('food_sage_embeddings', $limit + 1, f.sage_embedding)
            YIELD node, score
            WHERE node.name <> $foodName
            RETURN node.name AS name, score
            LIMIT $limit
            """;
        return executeCypher(query, Map.of("foodName", foodName, "limit", limit));
    }

    @Override
    public List<Map<String, Object>> findPairingCandidates(String foodName, int limit) {
        String query = """
            MATCH (f1:Food {name: $foodName})
            CALL db.index.vector.queryNodes('food_sage_embeddings', 50, f1.sage_embedding)
            YIELD node AS f2, score AS sim
            WHERE f2.name <> $foodName AND sim > 0
            OPTIONAL MATCH (f1)-[r:COMPLEMENTARY]-(f2)
            ORDER BY CASE WHEN r IS NOT NULL THEN 1 ELSE 2 END ASC, sim DESC
            RETURN f2.name AS name,
                   sim AS similarityScore,
                   CASE WHEN r IS NOT NULL THEN '专家推荐(已有关系)' ELSE 'AI预测(隐藏搭档)' END AS reason,
                   r IS NOT NULL AS expertLinked
            LIMIT $limit
            """;
        return executeCypher(query, Map.of("foodName", foodName, "limit", limit));
    }

    @Override
    public List<String> findSmartPairings(String foodName, int limit) {
        String query = """
            MATCH (f1:Food {name: $foodName})
            CALL db.index.vector.queryNodes('food_sage_embeddings', 100, f1.sage_embedding)
            YIELD node AS f2, score AS sim
            WHERE f2.name <> $foodName AND sim > 0.85
              AND NOT (f1)-[:COMPLEMENTARY]-(f2)
            RETURN f2.name AS name
            LIMIT $limit
            """;
        return executeCypher(query, Map.of("foodName", foodName, "limit", limit)).stream()
            .map(row -> (String) row.get("name"))
            .filter(StringUtils::hasText)
            .toList();
    }

    @Override
    public List<String> findRecipesByIngredients(List<String> ingredients, int limit) {
        String query = """
            MATCH (r:Recipe)
            WHERE ALL(ingredient IN $ingredients WHERE (r)-[:CONTAINS]->(:Food {name: ingredient}))
            RETURN r.name AS name
            LIMIT $limit
            """;
        return executeCypher(query, Map.of("ingredients", ingredients, "limit", limit)).stream()
            .map(row -> (String) row.get("name"))
            .filter(StringUtils::hasText)
            .toList();
    }

    @Override
    public List<String> checkIncompatibilities(List<String> foodNames) {
        String query = """
            MATCH (f:Food)-[r:INCOMPATIBLE]-(target:Food)
            WHERE f.name IN $foodNames AND target.name IN $foodNames AND f.name < target.name
            RETURN f.name + '与' + target.name + '相克：' + coalesce(r.reason, '暂无具体原因') AS warning
            """;
        return executeCypher(query, Map.of("foodNames", foodNames)).stream()
            .map(row -> (String) row.get("warning"))
            .filter(StringUtils::hasText)
            .toList();
    }

    @Override
    public void saveSageEmbedding(String foodName, List<Double> embedding) {
        executeWrite(
            "MATCH (f:Food {name: $name}) SET f.sage_embedding = $emb",
            Map.of("name", foodName, "emb", embedding)
        );
    }

    @Override
    public List<String> findFoodsMissingSageEmbedding() {
        return executeCypher(
            "MATCH (f:Food) WHERE f.sage_embedding IS NULL AND f.name IS NOT NULL RETURN f.name AS name",
            Map.of()
        ).stream()
            .map(row -> (String) row.get("name"))
            .filter(StringUtils::hasText)
            .toList();
    }

    @Override
    public Map<String, Object> findFoodMetapathEmbeddings(String foodName) {
        List<Map<String, Object>> rows = executeCypher(
            """
            MATCH (f:Food {name: $name})
            RETURN f.comp_embedding AS comp,
                   f.incomp_embedding AS incomp,
                   f.overlap_embedding AS overlap,
                   f.rfr_embedding AS rfr
            """,
            Map.of("name", foodName)
        );
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    @Override
    public List<Map<String, Object>> findAllFoodMetapathEmbeddings() {
        return executeCypher(
            """
            MATCH (f:Food)
            RETURN f.comp_embedding AS comp,
                   f.incomp_embedding AS incomp,
                   f.overlap_embedding AS overlap,
                   f.rfr_embedding AS rfr
            """,
            Map.of()
        );
    }

    @Override
    public List<Map<String, Object>> findNeighborMetapathEmbeddings(String foodName, String relationType) {
        if (!ALLOWED_REL_TYPES.contains(relationType)) {
            throw new IllegalArgumentException("不支持的关系类型: " + relationType);
        }
        String query = """
            MATCH (f:Food {name: $name})-[:__REL__]->(n:Food)
            RETURN n.comp_embedding AS comp,
                   n.incomp_embedding AS incomp,
                   n.overlap_embedding AS overlap,
                   n.rfr_embedding AS rfr
            """.replace("__REL__", relationType);
        return executeCypher(query, Map.of("name", foodName));
    }

    @Override
    public Map<String, Object> loadFoodEmbeddings(String foodName) {
        List<Map<String, Object>> rows = executeCypher(
            """
            MATCH (f:Food {name: $name})
            RETURN f.name AS name,
                   size(coalesce(f.rfr_embedding, [])) > 0 AS hasEmbedding,
                   size(coalesce(f.comp_embedding, [])) > 0 AS hasCompEmbedding,
                   size(coalesce(f.incomp_embedding, [])) > 0 AS hasIncompEmbedding,
                   size(coalesce(f.overlap_embedding, [])) > 0 AS hasOverlapEmbedding
            LIMIT 1
            """,
            Map.of("name", foodName)
        );
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    @Override
    public List<Map<String, Object>> findFoodSimilarityByIndex(String foodName, String indexName, int limit) {
        String query = """
            MATCH (f:Food {name: $name})
            CALL db.index.vector.queryNodes($indexName, $limit,
                CASE
                    WHEN $indexName = 'idx_food_comp' THEN f.comp_embedding
                    WHEN $indexName = 'idx_food_incomp' THEN f.incomp_embedding
                    WHEN $indexName = 'idx_food_overlap' THEN f.overlap_embedding
                    ELSE f.rfr_embedding
                END)
            YIELD node, score
            WHERE node <> f
            RETURN node.name AS name, toFloat(score) AS score
            ORDER BY score DESC
            """;
        return executeCypher(query, Map.of("name", foodName, "indexName", indexName, "limit", limit));
    }

    @Override
    public List<RelationExploreDTO> exploreFoodRelations(String foodName, String indexName, String relationType, int limit) {
        String query = """
            MATCH (target:Food {name: $name})
            CALL db.index.vector.queryNodes($indexName, $limit,
                CASE
                    WHEN $indexName = 'idx_food_comp' THEN target.comp_embedding
                    WHEN $indexName = 'idx_food_incomp' THEN target.incomp_embedding
                    WHEN $indexName = 'idx_food_overlap' THEN target.overlap_embedding
                    ELSE target.rfr_embedding
                END)
            YIELD node, score
            WHERE node <> target
            OPTIONAL MATCH (target)-[rel]-(node)
            WHERE $relationType = 'ANY' OR type(rel) = $relationType
            OPTIONAL MATCH (node)<-[:CONTAINS]-(recipe:Recipe)
            RETURN coalesce(type(rel), $defaultType) AS relationType,
                   node.name AS target,
                   toFloat(score) AS score,
                   collect(DISTINCT recipe.name) AS relatedRecipes
            ORDER BY score DESC
            """;

        String defaultType = switch (relationType) {
            case "COMPLEMENTARY" -> "COMP_VECTOR";
            case "INCOMPATIBLE" -> "INCOMP_VECTOR";
            case "OVERLAP" -> "OVERLAP_VECTOR";
            default -> "GENERAL_VECTOR";
        };

        List<Map<String, Object>> rows = executeCypher(query, Map.of(
            "name", foodName,
            "indexName", indexName,
            "limit", limit,
            "relationType", relationType,
            "defaultType", defaultType
        ));

        return rows.stream().map(this::toRelationExploreDto).toList();
    }

    @Override
    public List<String> findRelationsBetween(String sourceFood, String targetFood) {
        return executeCypher(
            """
            MATCH (f1:Food {name: $source})-[r:COMPLEMENTARY|INCOMPATIBLE|OVERLAP]-(f2:Food {name: $target})
            RETURN DISTINCT type(r) AS relation
            """,
            Map.of("source", sourceFood, "target", targetFood)
        ).stream()
            .map(row -> asString(row.get("relation")))
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
    }

    @Override
    public List<Map<String, Object>> findSimilarRecipesByName(String recipeName, int limit) {
        return executeCypher(
            """
            MATCH (r:Recipe {name: $name})
            CALL db.index.vector.queryNodes('idx_recipe_rfr', $limit, r.rfr_embedding)
            YIELD node, score
            RETURN elementId(node) AS recipeId,
                   node.name AS name,
                   CASE WHEN node.steps IS NULL THEN '' ELSE node.steps END AS steps,
                   toFloat(score) AS score
            """,
            Map.of("name", recipeName, "limit", limit)
        );
    }

    @Override
    public List<Recipe> recommendPathBasedRecipes(String userId, int limit) {
        List<Map<String, Object>> rows = executeCypher(
            """
            MATCH (u:User {id: $userId})
            MATCH (u)-[:LIKES]->(:Recipe)-[:CONTAINS]->(i:Food)
            WITH u, i, count(i) AS ingredient_weight
            MATCH (r2:Recipe)-[:CONTAINS]->(i)
            WHERE NOT (u)-[:LIKES]->(r2)
              AND NOT (u)-[:NOT_INTERESTED]->(r2)
              AND NOT EXISTS {
                    MATCH (u)-[:DISLIKES]->(:Food)<-[:CONTAINS]-(r2)
              }
            WITH r2, sum(ingredient_weight) AS score
            RETURN elementId(r2) AS id,
                   r2.name AS name,
                   r2.ingredients AS ingredients,
                   r2.detailedIngredients AS detailedIngredients,
                   r2.steps AS steps
            ORDER BY score DESC
            LIMIT $limit
            """,
            Map.of("userId", userId, "limit", limit)
        );
        return rows.stream().map(this::toRecipe).toList();
    }

    @Override
    public List<Recipe> recommendEmbeddingBasedRecipes(String userId, int limit, int searchLimit) {
        List<Map<String, Object>> rows = executeCypher(
            """
            MATCH (u:User {id: $userId})-[:LIKES]->(r:Recipe)
            WHERE r.rfr_embedding IS NOT NULL
            WITH u, r ORDER BY coalesce(r.timestamp, 0) DESC LIMIT 5
            CALL db.index.vector.queryNodes('recipe_vector_index', $searchLimit, r.rfr_embedding)
            YIELD node, score
            WITH DISTINCT u, node, max(score) AS maxScore
            WHERE NOT (u)-[:LIKES]->(node)
              AND NOT (u)-[:NOT_INTERESTED]->(node)
              AND NOT EXISTS {
                   MATCH (u)-[:DISLIKES]->(:Food)<-[:CONTAINS]-(node)
              }
            RETURN elementId(node) AS id,
                   node.name AS name,
                   node.ingredients AS ingredients,
                   node.detailedIngredients AS detailedIngredients,
                   node.steps AS steps
            ORDER BY maxScore DESC
            LIMIT $limit
            """,
            Map.of("userId", userId, "limit", limit, "searchLimit", searchLimit)
        );
        return rows.stream().map(this::toRecipe).toList();
    }

    @Override
    public List<Recipe> searchRecipesByName(String keyword) {
        List<Map<String, Object>> rows = executeCypher(
            """
            MATCH (r:Recipe)
            WHERE toLower(r.name) CONTAINS toLower($keyword)
            RETURN elementId(r) AS id,
                   r.name AS name,
                   r.ingredients AS ingredients,
                   r.detailedIngredients AS detailedIngredients,
                   r.steps AS steps
            ORDER BY r.name
            """,
            Map.of("keyword", keyword)
        );
        return rows.stream().map(this::toRecipe).toList();
    }

    @Override
    public Recipe findRecipeByIdOrName(String idOrName) {
        List<Map<String, Object>> rows = executeCypher(
            """
            MATCH (r:Recipe)
            WHERE elementId(r) = $value OR r.id = $value OR r.name = $value
            RETURN elementId(r) AS id,
                   r.name AS name,
                   r.ingredients AS ingredients,
                   r.detailedIngredients AS detailedIngredients,
                   r.steps AS steps
            LIMIT 1
            """,
            Map.of("value", idOrName)
        );
        if (rows.isEmpty()) {
            return null;
        }
        return toRecipe(rows.get(0));
    }

    @Override
    public void likeRecipe(String userId, String recipeId) {
        executeWrite(
            """
            MERGE (u:User {id: $userId})
            WITH u
            MATCH (r:Recipe {name: $recipeId})
            MERGE (u)-[rel:LIKES]->(r)
            ON CREATE SET rel.timestamp = datetime()
            ON MATCH SET rel.timestamp = datetime()
            WITH u, r
            OPTIONAL MATCH (u)-[old:NOT_INTERESTED]->(r)
            DELETE old
            """,
            Map.of("userId", userId, "recipeId", recipeId)
        );
    }

    @Override
    public void dislikeRecipe(String userId, String recipeId) {
        executeWrite(
            """
            MERGE (u:User {id: $userId})
            WITH u
            MATCH (r:Recipe {name: $recipeId})
            MERGE (u)-[rel:NOT_INTERESTED]->(r)
            ON CREATE SET rel.timestamp = datetime()
            WITH u, r
            OPTIONAL MATCH (u)-[old:LIKES]->(r)
            DELETE old
            """,
            Map.of("userId", userId, "recipeId", recipeId)
        );
    }

    @Override
    public void favoriteIngredient(String userId, String ingredientName) {
        executeWrite(
            """
            MERGE (u:User {id: $userId})
            WITH u
            MATCH (i:Food {name: $ingName})
            MERGE (u)-[rel:FAVORITE]->(i)
            ON CREATE SET rel.timestamp = datetime()
            WITH u, i
            OPTIONAL MATCH (u)-[old:DISLIKES]->(i)
            DELETE old
            """,
            Map.of("userId", userId, "ingName", ingredientName)
        );
    }

    @Override
    public void dislikeIngredient(String userId, String ingredientName, String reason) {
        executeWrite(
            """
            MERGE (u:User {id: $userId})
            WITH u
            MATCH (i:Food {name: $ingName})
            MERGE (u)-[rel:DISLIKES]->(i)
            ON CREATE SET rel.timestamp = datetime(), rel.reason = $reason
            WITH u, i
            OPTIONAL MATCH (u)-[old:FAVORITE]->(i)
            DELETE old
            """,
            Map.of("userId", userId, "ingName", ingredientName, "reason", reason)
        );
    }

    @Override
    public Map<String, Object> findFoodByName(String name) {
        List<Map<String, Object>> rows = executeCypher(
            """
            MATCH (f:Food {name: $name})
            RETURN f.name AS name,
                   f.nutritionalValue AS nutritionalValue,
                   f.healthBenefits AS healthBenefits,
                   f.suitableFor AS suitableFor,
                   f.contraindications AS contraindications,
                   f.nutrients AS nutrients
            LIMIT 1
            """,
            Map.of("name", name)
        );
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    @Override
    public void mergeFoodNode(String name) {
        executeWrite(
            "MERGE (f:Food {name: $name})",
            Map.of("name", name)
        );
    }

    @Override
    public void upsertFoodDetails(String name,
                                  String nutritionalValue,
                                  String healthBenefits,
                                  String suitableFor,
                                  String contraindications,
                                  Map<String, String> nutrients) {
        executeWrite(
            """
            MERGE (f:Food {name: $name})
            SET f.nutritionalValue = $nutritionalValue,
                f.healthBenefits = $healthBenefits,
                f.suitableFor = $suitableFor,
                f.contraindications = $contraindications,
                f.nutrients = $nutrients
            """,
            Map.of(
                "name", name,
                "nutritionalValue", safeString(nutritionalValue),
                "healthBenefits", safeString(healthBenefits),
                "suitableFor", safeString(suitableFor),
                "contraindications", safeString(contraindications),
                "nutrients", nutrients == null ? Map.of() : nutrients
            )
        );
    }

    @Override
    public void deleteFoodByName(String name) {
        executeWrite("MATCH (f:Food) WHERE f.name = $name DETACH DELETE f", Map.of("name", name));
    }

    @Override
    public long countFoods() {
        return singleCount("MATCH (f:Food) RETURN count(f) AS c");
    }

    @Override
    public long countRecipes() {
        return singleCount("MATCH (r:Recipe) RETURN count(r) AS c");
    }

    @Override
    public long countComplementary() {
        return singleCount("MATCH ()-[r:COMPLEMENTARY]-() RETURN count(r) AS c");
    }

    @Override
    public long countIncompatible() {
        return singleCount("MATCH ()-[r:INCOMPATIBLE]-() RETURN count(r) AS c");
    }

    @Override
    public long countContainsRelations() {
        return singleCount("MATCH (:Recipe)-[r:CONTAINS]->(:Food) RETURN count(r) AS c");
    }

    @Override
    public Map<String, Object> loadRelationSummary(String name) {
        List<Map<String, Object>> rows = executeCypher(
            """
            MATCH (f:Food {name: $name})
            OPTIONAL MATCH (f)-[:COMPLEMENTARY]-(comp:Food)
            OPTIONAL MATCH (f)-[:INCOMPATIBLE]-(incomp:Food)
            OPTIONAL MATCH (f)-[:OVERLAP]-(overlap:Food)
            RETURN f.name AS sourceName,
                   collect(DISTINCT comp.name) AS complementary,
                   collect(DISTINCT incomp.name) AS incompatible,
                   collect(DISTINCT overlap.name) AS overlap
            LIMIT 1
            """,
            Map.of("name", name)
        );
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    @Override
    public List<RelationExploreDTO> exploreGeneralRelations(String foodName, int limit) {
        return exploreFoodRelations(foodName, "idx_recipe_rfr", "ANY", limit);
    }

    @Override
    public void mergeComplementaryRelation(String source, String target, String description) {
        executeWrite(
            """
            MATCH (s:Food {name: $source}), (t:Food {name: $target})
            OPTIONAL MATCH (s)-[existing:COMPLEMENTARY]-(t)
            WITH s, t, existing
            WHERE existing IS NULL
            WITH CASE WHEN s.name <= t.name THEN s ELSE t END AS a,
                 CASE WHEN s.name <= t.name THEN t ELSE s END AS b,
                 $description AS description
            CREATE (a)-[r:COMPLEMENTARY]->(b)
            SET r.description = description
            """,
            Map.of("source", source, "target", target, "description", safeString(description))
        );
    }

    @Override
    public void mergeIncompatibleRelation(String source, String target, String description) {
        executeWrite(
            """
            MATCH (s:Food {name: $source}), (t:Food {name: $target})
            OPTIONAL MATCH (s)-[existing:INCOMPATIBLE]-(t)
            WITH s, t, existing
            WHERE existing IS NULL
            WITH CASE WHEN s.name <= t.name THEN s ELSE t END AS a,
                 CASE WHEN s.name <= t.name THEN t ELSE s END AS b,
                 $description AS description
            CREATE (a)-[r:INCOMPATIBLE]->(b)
            SET r.description = description
            """,
            Map.of("source", source, "target", target, "description", safeString(description))
        );
    }

    @Override
    public void mergeOverlapRelation(String source, String target, String description) {
        executeWrite(
            """
            MATCH (s:Food {name: $source}), (t:Food {name: $target})
            OPTIONAL MATCH (s)-[existing:OVERLAP]-(t)
            WITH s, t, existing
            WHERE existing IS NULL
            WITH CASE WHEN s.name <= t.name THEN s ELSE t END AS a,
                 CASE WHEN s.name <= t.name THEN t ELSE s END AS b,
                 $description AS description
            CREATE (a)-[r:OVERLAP]->(b)
            SET r.description = description
            """,
            Map.of("source", source, "target", target, "description", safeString(description))
        );
    }

    @Override
    public Recipe upsertRecipe(String name, String ingredients, String detailedIngredients, String steps) {
        List<Map<String, Object>> rows = executeCypher(
            """
            MERGE (r:Recipe {name: $name})
            SET r.ingredients = $ingredients,
                r.detailedIngredients = $detailedIngredients,
                r.steps = $steps
            RETURN elementId(r) AS id,
                   r.name AS name,
                   r.ingredients AS ingredients,
                   r.detailedIngredients AS detailedIngredients,
                   r.steps AS steps
            """,
            Map.of(
                "name", name,
                "ingredients", safeString(ingredients),
                "detailedIngredients", safeString(detailedIngredients),
                "steps", safeString(steps)
            )
        );
        return rows.isEmpty() ? null : toRecipe(rows.get(0));
    }

    @Override
    public void linkContains(String recipeName, String foodName) {
        executeWrite(
            """
            MERGE (f:Food {name: $foodName})
            WITH f
            MATCH (r:Recipe {name: $recipeName})
            MERGE (r)-[:CONTAINS]->(f)
            """,
            Map.of("recipeName", recipeName, "foodName", foodName)
        );
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
                if (Boolean.TRUE.equals(result.isError())) {
                    throw new IllegalStateException("MCP 工具执行失败: " + extractText(result));
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
        return String.valueOf(first);
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

    private long singleCount(String query) {
        List<Map<String, Object>> rows = executeCypher(query, Map.of());
        if (rows == null || rows.isEmpty()) {
            return 0L;
        }
        return asLong(rows.get(0).get("c"));
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
