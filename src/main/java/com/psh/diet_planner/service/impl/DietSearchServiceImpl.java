package com.psh.diet_planner.service.impl;

import com.psh.diet_planner.ai.RecipeAiService;
import com.psh.diet_planner.ai.UserMemoryContext;
import com.psh.diet_planner.dto.AllAspectsResponse;
import com.psh.diet_planner.dto.DietQueryRequest;
import com.psh.diet_planner.dto.DietSearchResponse;
import com.psh.diet_planner.dto.RecipeDTO;
import com.psh.diet_planner.dto.RelationExploreDTO;
import com.psh.diet_planner.dto.RelationExploreRequest;
import com.psh.diet_planner.dto.RelationExploreResponse;
import com.psh.diet_planner.dto.RelationPathDTO;
import com.psh.diet_planner.dto.UserMemoryResponse;
import com.psh.diet_planner.exception.CustomException;
import com.psh.diet_planner.model.SearchIntent;
import com.psh.diet_planner.service.DietSearchService;
import com.psh.diet_planner.service.ReviewService;
import com.psh.diet_planner.service.support.mcp.Neo4jMcpAdapter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DietSearchServiceImpl implements DietSearchService, DisposableBean {

    private final Neo4jMcpAdapter neo4jMcpAdapter;
    private final RecipeAiService recipeAiService;
    private final ReviewService reviewService;
    private final ExecutorService dietExecutor = Executors.newFixedThreadPool(4);

    @Override
    public List<DietSearchResponse> comprehensiveSearch(DietQueryRequest request) {
        validateRequest(request.getFoodName(), request.getIntent(), request.getTopK());
        SearchIntent intent = request.getIntent();
        if (intent == SearchIntent.ALL || intent == SearchIntent.RECIPE) {
            throw new CustomException(400, "analyze 仅支持 COMP/INCOMP/OVERLAP 检索");
        }
        int limit = normalizeTopK(request.getTopK());
        Map<String, Object> embeddingInfo = loadEmbeddingOrThrow(request.getFoodName());
        ensureEmbedding(intent, embeddingInfo, true);
        List<DietSearchResponse> results = searchByIntent(request.getFoodName(), intent, limit);
        // 不在此处把 AI 建议注入到每条结果，改为由 Controller 在顶层返回单一 aiAnalysis 字段
        return results;
    }

    @Override
    public AllAspectsResponse fullAnalysis(DietQueryRequest request) {
        validateRequest(request.getFoodName(), SearchIntent.ALL, request.getTopK());
        int limit = normalizeTopK(request.getTopK());
        Map<String, Object> embeddingInfo = loadEmbeddingOrThrow(request.getFoodName());
        List<DietSearchResponse> complementary = supports(embeddingInfo.get("hasCompEmbedding"))
            ? searchByIntent(request.getFoodName(), SearchIntent.COMP, limit)
            : List.of();
        List<DietSearchResponse> incompatible = supports(embeddingInfo.get("hasIncompEmbedding"))
            ? searchByIntent(request.getFoodName(), SearchIntent.INCOMP, limit)
            : List.of();
        List<DietSearchResponse> overlap = supports(embeddingInfo.get("hasOverlapEmbedding"))
            ? searchByIntent(request.getFoodName(), SearchIntent.OVERLAP, limit)
            : List.of();
        List<DietSearchResponse> general = supports(embeddingInfo.get("hasEmbedding"))
            ? searchByIntent(request.getFoodName(), SearchIntent.RECIPE, limit)
            : List.of();

        List<DietSearchResponse> merged = Stream.of(complementary, incompatible, overlap, general)
            .flatMap(List::stream)
            .collect(Collectors.toList());
        String aiAdvice;
        if (request.isIncludeAiAdvice() && !merged.isEmpty()) {
            UserMemoryContext memCtx = loadMemoryContext(request.getUserId());
            aiAdvice = recipeAiService.generateDietAdvice(request.getFoodName(), SearchIntent.ALL, merged, memCtx);
            merged.forEach(resp -> resp.setAiAnalysis(aiAdvice));
        } else {
            aiAdvice = null;
        }

        return AllAspectsResponse.builder()
            .foodName(request.getFoodName())
            .complementary(complementary)
            .incompatible(incompatible)
            .overlap(overlap)
            .general(general)
            .aiAnalysis(aiAdvice)
            .build();
    }

    @Override
    public List<RecipeDTO> findSimilarRecipes(String recipeName) {
        if (recipeName == null || recipeName.isBlank()) {
            throw new CustomException(400, "菜谱名称不能为空");
        }
        List<Map<String, Object>> rows = neo4jMcpAdapter.findSimilarRecipesByName(recipeName, 5);
        return rows.stream().map(row -> {
            RecipeDTO dto = new RecipeDTO();
            dto.setRecipeId(asString(row.get("recipeId")));
            dto.setName(asString(row.get("name")));
            dto.setSteps(asString(row.get("steps")));
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public RelationExploreResponse exploreRelations(RelationExploreRequest request) {
        validateRequest(request.getFoodName(), request.getIntent(), request.getTopK());
        List<RelationExploreDTO> projections = switch (request.getIntent()) {
            case COMP -> neo4jMcpAdapter.exploreFoodRelations(request.getFoodName(), "idx_food_comp", "COMPLEMENTARY", request.getTopK());
            case INCOMP -> neo4jMcpAdapter.exploreFoodRelations(request.getFoodName(), "idx_food_incomp", "INCOMPATIBLE", request.getTopK());
            case OVERLAP -> neo4jMcpAdapter.exploreFoodRelations(request.getFoodName(), "idx_food_overlap", "OVERLAP", request.getTopK());
            default -> throw new CustomException(400, "仅支持 COMP/INCOMP/OVERLAP 关系探索");
        };
        List<RelationPathDTO> relations = projections
            .stream()
            .map(row -> RelationPathDTO.builder()
                .relationType(row.getRelationType())
                .targetFood(row.getTarget())
                .score(row.getScore())
                .relatedRecipes(limitRecipes(row.getRelatedRecipes(), request.getRecipeLimit()))
                .build())
            .collect(Collectors.toList());
        return RelationExploreResponse.builder()
            .sourceFood(request.getFoodName())
            .intent(request.getIntent())
            .relations(relations)
            .build();
    }

    @Override
    public void destroy() {
        dietExecutor.shutdownNow();
    }

    private List<String> limitRecipes(List<String> recipes, Integer limit) {
        if (recipes == null || recipes.isEmpty()) {
            return List.of();
        }
        int bound = limit == null ? recipes.size() : Math.min(recipes.size(), Math.max(0, limit));
        return recipes.stream().limit(bound).collect(Collectors.toList());
    }

    private void validateRequest(String foodName, SearchIntent intent, Integer topK) {
        if (foodName == null || foodName.isBlank()) {
            throw new CustomException(400, "食材名称不能为空");
        }
        if (intent == null) {
            throw new CustomException(400, "intent 不能为空");
        }
        if (intent == SearchIntent.ALL && topK == null) {
            throw new CustomException(400, "ALL 模式下 topK 不能为空");
        }
        if (topK == null || topK < 1 || topK > 50) {
            throw new CustomException(400, "topK 范围需在 1-50");
        }
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null) {
            return 5;
        }
        return Math.min(50, Math.max(1, topK));
    }

    private Map<String, Object> loadEmbeddingOrThrow(String foodName) {
        Map<String, Object> embedding = neo4jMcpAdapter.loadFoodEmbeddings(foodName);
        if (embedding.isEmpty() || !embedding.containsKey("name")) {
            throw new CustomException(404, "未找到对应食材: " + foodName);
        }
        return embedding;
    }

    private void ensureEmbedding(SearchIntent intent, Map<String, Object> embedding, boolean strict) {
        boolean available = switch (intent) {
            case COMP -> supports(embedding.get("hasCompEmbedding"));
            case INCOMP -> supports(embedding.get("hasIncompEmbedding"));
            case OVERLAP -> supports(embedding.get("hasOverlapEmbedding"));
            case RECIPE, ALL -> supports(embedding.get("hasEmbedding"));
        };
        if (!available && strict) {
            throw new CustomException(400, "该食材缺少所需的向量索引，无法完成检索: " + intent);
        }
    }

    private boolean supports(Object flag) {
        return flag instanceof Boolean bool && bool;
    }

    private List<DietSearchResponse> searchByIntent(String foodName, SearchIntent intent, int limit) {
        try {
            List<Map<String, Object>> rows = switch (intent) {
                case COMP -> neo4jMcpAdapter.findFoodSimilarityByIndex(foodName, "idx_food_comp", limit);
                case INCOMP -> neo4jMcpAdapter.findFoodSimilarityByIndex(foodName, "idx_food_incomp", limit);
                case OVERLAP -> neo4jMcpAdapter.findFoodSimilarityByIndex(foodName, "idx_food_overlap", limit);
                case RECIPE, ALL -> neo4jMcpAdapter.findFoodSimilarityByIndex(foodName, "idx_recipe_rfr", limit);
            };
            return toResponses(foodName, rows);
        } catch (CustomException ex) {
            throw ex;
        } catch (Exception ex) {
            String detail = extractExceptionDetail(ex);
            String lower = detail.toLowerCase();
            if (lower.contains("vector") || lower.contains("index") || lower.contains("idx_food_") || lower.contains("idx_recipe_rfr")) {
                throw new CustomException(400, "向量索引未就绪，请先完成训练并构建索引");
            }
            if (detail.contains("调用 MCP 工具失败") || lower.contains("mcp")) {
                throw new CustomException(503, "图谱检索服务暂不可用，请确认 Neo4j 与 MCP 服务已启动");
            }
            throw new CustomException(500, "饮食分析失败: " + detail);
        }
    }

    private String extractExceptionDetail(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = ex;
        int depth = 0;
        while (cur != null && depth < 6) {
            String msg = cur.getMessage();
            if (msg != null && !msg.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" | caused by: ");
                }
                sb.append(msg);
            }
            cur = cur.getCause();
            depth++;
        }
        return Objects.toString(sb, "");
    }

    private List<DietSearchResponse> toResponses(String sourceFood, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        // 去重：按 name 保留首次出现的顺序
        Map<String, DietSearchResponse> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String name = asString(row.get("name"));
            if (name == null || name.isBlank()) continue;
            if (map.containsKey(name)) continue;
            DietSearchResponse resp = DietSearchResponse.builder()
                .name(name)
                .similarityScore(asDouble(row.get("score")))
                .knownRelations(fetchRelations(sourceFood, name))
                .build();
            map.put(name, resp);
        }
        if (map.size() < rows.size()) {
            log.info("搜索结果包含重复项，原始数量={} 去重后数量={} source={}", rows.size(), map.size(), sourceFood);
        }
        return map.values().stream().collect(Collectors.toList());
    }

    private List<String> fetchRelations(String sourceFood, String targetFood) {
        try {
            List<String> relations = neo4jMcpAdapter.findRelationsBetween(sourceFood, targetFood);
            if (relations == null || relations.isEmpty()) {
                return List.of();
            }
            return relations.stream().filter(item -> item != null && !item.isBlank()).distinct().toList();
        } catch (Exception ex) {
            log.warn("查询已知关系失败 source={} target={}: {}", sourceFood, targetFood, ex.getMessage());
            return List.of();
        }
    }

    private void attachAiAdvice(String foodName, SearchIntent intent, boolean includeAiAdvice,
                               List<DietSearchResponse> results, UserMemoryContext memCtx) {
        if (!includeAiAdvice || results == null || results.isEmpty()) {
            return;
        }
        String advice = recipeAiService.generateDietAdvice(foodName, intent, results, memCtx);
        results.forEach(resp -> resp.setAiAnalysis(advice));
    }

    @Override
    public String generateAiAdvice(String foodName, SearchIntent intent, List<DietSearchResponse> results, Long userId) {
        if (foodName == null || foodName.isBlank() || results == null || results.isEmpty()) {
            return null;
        }
        UserMemoryContext memCtx = loadMemoryContext(userId);
        try {
            return recipeAiService.generateDietAdvice(foodName, intent, results, memCtx);
        } catch (Exception ex) {
            log.warn("生成 AI 建议失败 food={} userId={} : {}", foodName, userId, ex.getMessage());
            return null;
        }
    }

    private UserMemoryContext loadMemoryContext(Long userId) {
        if (userId == null) {
            return UserMemoryContext.empty();
        }
        try {
            UserMemoryResponse memory = reviewService.loadUserMemory(userId);
            return UserMemoryContext.fromMemory(memory);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("加载用户画像失败 userId={}: {}", userId, e.getMessage());
            return UserMemoryContext.empty();
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0D;
    }

}
