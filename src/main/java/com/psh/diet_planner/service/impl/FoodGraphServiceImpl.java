package com.psh.diet_planner.service.impl;

import com.psh.diet_planner.ai.UserMemoryContext;
import com.psh.diet_planner.dto.CreativeCookingRequest;
import com.psh.diet_planner.dto.CreativeCookingResponse;
import com.psh.diet_planner.dto.FoodPairingRequest;
import com.psh.diet_planner.dto.PairingCandidate;
import com.psh.diet_planner.dto.PairingInsightResponse;
import com.psh.diet_planner.dto.PairingResponse;
import com.psh.diet_planner.dto.SafetyCheckRequest;
import com.psh.diet_planner.dto.SafetyCheckResponse;
import com.psh.diet_planner.dto.SemanticSearchResponse;
import com.psh.diet_planner.dto.SmartMealPlanRequest;
import com.psh.diet_planner.dto.SmartMealPlanResponse;
import com.psh.diet_planner.dto.UserMemoryResponse;
import com.psh.diet_planner.exception.CustomException;
import com.psh.diet_planner.service.FoodGraphService;
import com.psh.diet_planner.service.ReviewService;
import com.psh.diet_planner.service.support.mcp.Neo4jMcpAdapter;
import com.psh.diet_planner.service.support.FoodJsonWarningService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
public class FoodGraphServiceImpl implements FoodGraphService {

    private static final int DEFAULT_LIMIT = 5;
    private static final int DEFAULT_MEAL_PAIRINGS = 3;
    private static final int DEFAULT_RECIPE_LIMIT = 3;
    private static final String MEAL_PLAN_PROMPT = """
            你是一位资深的临床营养师兼创意名厨。
            主食材：{main}
            搭配候选：{pairings}
            知识图谱中已存在的菜谱参考：{recipes}
            {userProfile}

            请完成以下任务：
            1. 判断这些食材在营养素互补、风味搭配或烹饪技法上的协同逻辑。
            2. 提供 1 个菜名以及 3 条烹饪要点（火候、调味或摆盘即可）。
            3. 若参考菜谱为空，请说明需要原创方案。
            字数控制在 250 字以内，以第二人称输出。
            """;
    private static final String CREATIVE_PROMPT = """
            我的冰箱里只有：{leftovers}。
            {userProfile}
            请严格执行：
            1. 先判断这些食材的味型与质地，补齐一两个必要的基础调味料即可。
            2. 若组合罕见，请基于图谱味型逻辑创造新菜名，并解释灵感来源。
            3. 输出分步烹饪指令，步骤不超过 6 条。
            4. 最终口吻需兼具专业与鼓励。
            """;

    private final Neo4jMcpAdapter neo4jMcpAdapter;
    private final FoodJsonWarningService foodJsonWarningService;
    private final ReviewService reviewService;
    private final ChatClient chatClient;

            public FoodGraphServiceImpl(Neo4jMcpAdapter neo4jMcpAdapter,
                                FoodJsonWarningService foodJsonWarningService,
                                ReviewService reviewService,
                                ChatClient.Builder chatClientBuilder) {
        this.neo4jMcpAdapter = neo4jMcpAdapter;
        this.foodJsonWarningService = foodJsonWarningService;
        this.reviewService = reviewService;
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public List<SemanticSearchResponse> searchSemanticSubstitutes(String foodName, int limit) {
        String normalized = normalizeFoodName(foodName);
        ensureFoodExists(normalized);
        int queryLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        List<Map<String, Object>> projections = neo4jMcpAdapter.findSemanticSubstitutes(normalized, queryLimit);
        List<SemanticSearchResponse> results = (projections == null ? List.<Map<String, Object>>of() : projections)
            .stream()
            .map(item -> SemanticSearchResponse.builder()
                .name((String) item.get("name"))
                .score(item.get("score") instanceof Number number ? number.doubleValue() : 0D)
                .build())
            .filter(item -> item.getName() != null && !item.getName().equalsIgnoreCase(normalized))
            .toList();
        if (results.isEmpty()) {
            throw new CustomException(404, "暂未检索到语义相似的食材");
        }
        return results;
    }

    @Override
    public PairingResponse recommendPairings(FoodPairingRequest request) {
        String normalized = normalizeFoodName(request.getFoodName());
        ensureFoodExists(normalized);
        int desired = request.getLimit() != null && request.getLimit() > 1 ? request.getLimit() : DEFAULT_LIMIT;
        List<String> rawPairings = neo4jMcpAdapter.findSmartPairings(normalized, desired * 2);
        List<String> pairings = (rawPairings == null ? List.<String>of() : rawPairings)
            .stream()
            .filter(name -> name != null && !name.equalsIgnoreCase(normalized))
            .distinct()
            .limit(desired)
            .toList();
        if (pairings.isEmpty()) {
            throw new CustomException(404, "暂未找到智能配伍结果");
        }
        return PairingResponse.builder()
            .sourceFood(normalized)
            .suggestions(pairings)
            .total(pairings.size())
            .build();
    }

    @Override
    public PairingInsightResponse recommendPairingsWithReason(FoodPairingRequest request) {
        String normalized = normalizeFoodName(request.getFoodName());
        ensureFoodExists(normalized);
        int desired = request.getLimit() != null && request.getLimit() > 1 ? request.getLimit() : DEFAULT_LIMIT;
        List<Map<String, Object>> rawCandidates = neo4jMcpAdapter.findPairingCandidates(normalized, desired * 2);
        List<PairingCandidate> candidates = deduplicateCandidates(rawCandidates, normalized);
        List<PairingCandidate> limited = candidates.stream().limit(desired).toList();
        if (limited.isEmpty()) {
            throw new CustomException(404, "暂无智能搭档推荐");
        }
        return PairingInsightResponse.builder()
            .sourceFood(normalized)
            .recommendations(limited)
            .total(limited.size())
            .build();
    }

    @Override
    public SmartMealPlanResponse generateMealPlan(SmartMealPlanRequest request) {
        try {
            String normalized = normalizeFoodName(request.getFoodName());
            ensureFoodExists(normalized);
            int pairingLimit = request.getPairingLimit() != null && request.getPairingLimit() > 1
                ? request.getPairingLimit()
                : DEFAULT_MEAL_PAIRINGS;
            List<String> rawPairings = neo4jMcpAdapter.findSmartPairings(normalized, pairingLimit * 2);
            List<String> pairings = (rawPairings == null ? List.<String>of() : rawPairings)
                .stream()
                .filter(name -> name != null && !name.equalsIgnoreCase(normalized))
                .distinct()
                .limit(pairingLimit)
                .toList();
            if (pairings.isEmpty()) {
                throw new CustomException(404, "GraphSAGE 暂无法给出搭配建议");
            }
            List<String> allIngredients = new ArrayList<>();
            allIngredients.add(normalized);
            allIngredients.addAll(pairings);
            List<String> recipes = neo4jMcpAdapter.findRecipesByIngredients(allIngredients, DEFAULT_RECIPE_LIMIT);
            List<String> safeRecipes = recipes == null ? List.of() : recipes;
            UserMemoryContext memCtx = loadMemoryContext(request.getUserId());
            String advice = callMealPlanModel(normalized, pairings, safeRecipes, memCtx);
            return SmartMealPlanResponse.builder()
                .mainIngredient(normalized)
                .recommendedPairings(pairings)
                .suggestedRecipes(safeRecipes)
                .nutritionExpertAdvice(advice)
                .build();
        } catch (IllegalStateException ex) {
            throw new CustomException(500, "MCP工具调用失败: " + ex.getMessage());
        }
    }

    @Override
    public SafetyCheckResponse checkSafety(SafetyCheckRequest request) {
        List<String> foods = sanitizeStrings(request.getFoods());
        if (foods.size() < 2) {
            throw new CustomException(400, "请至少提供两种食材以检测禁忌");
        }
        List<String> graphWarnings = neo4jMcpAdapter.checkIncompatibilities(foods);
        Set<String> warnings = new LinkedHashSet<>(graphWarnings == null ? List.of() : graphWarnings);
        
        List<String> jsonWarnings = foodJsonWarningService.findWarnings(foods);
        if (jsonWarnings != null && !jsonWarnings.isEmpty()) {
            warnings = new LinkedHashSet<>(jsonWarnings);
        }

        // 个人过敏原 & 疾病禁忌检测
        UserMemoryContext memCtx = loadMemoryContext(request.getUserId());
        if (!memCtx.isEmpty()) {
            UserMemoryResponse mem = memCtx.getRawMemory();
            if (mem != null) {
                List<String> allergens = mem.getAllergens();
                if (allergens != null) {
                    for (String food : foods) {
                        for (String allergen : allergens) {
                            if (allergen != null && food.contains(allergen)) {
                                warnings.add("⚠️ 个人过敏原警告：您对「" + allergen + "」过敏，食材「" + food + "」可能含有该过敏原");
                            }
                        }
                    }
                }
                List<String> illnesses = mem.getIllnesses();
                if (illnesses != null && !illnesses.isEmpty()) {
                    warnings.add("⚠️ 健康提示：您有「" + String.join("、", illnesses) + "」，请注意相关饮食禁忌");
                }
            }
        }

        return SafetyCheckResponse.builder()
            .safe(warnings.isEmpty())
            .warnings(new ArrayList<>(warnings))
            .build();
    }

    @Override
    public CreativeCookingResponse createCreativeIdea(CreativeCookingRequest request) {
        List<String> ingredients = sanitizeStrings(request.getIngredients());
        if (CollectionUtils.isEmpty(ingredients)) {
            throw new CustomException(400, "请提供至少一种剩余食材");
        }
        String joined = String.join("、", ingredients);
        UserMemoryContext memCtx = loadMemoryContext(request.getUserId());
        String profileText = memCtx.isEmpty() ? "" : memCtx.toPromptSection();
        try {
            String concept = chatClient.prompt()
                .system("你是一个精通食材逻辑的创意厨师。")
                .user(user -> user.text(CREATIVE_PROMPT)
                    .param("leftovers", joined)
                    .param("userProfile", profileText))
                .call()
                .content();
            return CreativeCookingResponse.builder()
                .inputs(ingredients)
                .concept(concept)
                .build();
        } catch (Exception ex) {
            throw new CustomException(500, "创意菜生成失败: " + ex.getMessage());
        }
    }

    private List<PairingCandidate> deduplicateCandidates(List<Map<String, Object>> projections, String normalized) {
        if (projections == null || projections.isEmpty()) {
            return List.of();
        }
        return projections.stream()
            .map(this::toPairingCandidate)
            .filter(candidate -> candidate.getName() != null && !candidate.getName().equalsIgnoreCase(normalized))
            .collect(Collectors.collectingAndThen(
                Collectors.toMap(PairingCandidate::getName, candidate -> candidate, (first, second) -> first, LinkedHashMap::new),
                map -> new ArrayList<>(map.values())
            ));
    }

    private PairingCandidate toPairingCandidate(Map<String, Object> projection) {
        String name = projection == null ? null : (String) projection.getOrDefault("name", null);
        double score = 0.0;
        Object scoreObj = projection == null ? null : projection.get("similarityScore");
        if (scoreObj instanceof Number) {
            score = ((Number) scoreObj).doubleValue();
        }
        boolean hasExpertEdge = false;
        Object expertObj = projection == null ? null : projection.get("expertLinked");
        if (expertObj instanceof Boolean) {
            hasExpertEdge = (Boolean) expertObj;
        }
        String reason = projection == null ? "" : (String) projection.getOrDefault("reason", "");
        return PairingCandidate.builder()
            .name(name)
            .similarityScore(score)
            .reason(reason)
            .expertLinked(hasExpertEdge)
            .build();
    }

    private String callMealPlanModel(String main, List<String> pairings, List<String> recipes,
                                     UserMemoryContext memCtx) {
        String pairingText = pairings.isEmpty() ? "暂无配伍" : String.join("、", pairings);
        String recipeText = recipes.isEmpty() ? "暂无参考菜谱" : String.join("、", recipes);
        String profileText = memCtx != null && !memCtx.isEmpty() ? memCtx.toPromptSection() : "";
        try {
            return chatClient.prompt()
                .system("你是一位遵循循证营养学的助手。")
                .user(user -> user.text(MEAL_PLAN_PROMPT)
                    .param("main", main)
                    .param("pairings", pairingText)
                    .param("recipes", recipeText)
                    .param("userProfile", profileText))
                .call()
                .content();
        } catch (Exception ex) {
            throw new CustomException(500, "AI 分析失败: " + ex.getMessage());
        }
    }

    private void ensureFoodExists(String foodName) {
        Map<String, Object> food = neo4jMcpAdapter.findFoodByName(foodName);
        Object name = food == null ? null : food.get("name");
        boolean exists = food != null && !food.isEmpty() && name != null && StringUtils.hasText(name.toString());
        if (!exists) {
            throw new CustomException(404, "未找到食材: " + foodName);
        }
    }

    private String normalizeFoodName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new CustomException(400, "食材名称不能为空");
        }
        return name.trim();
    }

    private List<String> sanitizeStrings(List<String> inputs) {
        if (inputs == null) {
            return List.of();
        }
        Set<String> sanitized = inputs.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (sanitized.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(sanitized);
    }

    private UserMemoryContext loadMemoryContext(Long userId) {
        if (userId == null) {
            return UserMemoryContext.empty();
        }
        try {
            UserMemoryResponse memory = reviewService.loadUserMemory(userId);
            return UserMemoryContext.fromMemory(memory);
        } catch (Exception e) {
            return UserMemoryContext.empty();
        }
    }
}
