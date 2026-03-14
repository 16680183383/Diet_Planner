package com.psh.diet_planner.service.impl;

import com.psh.diet_planner.dto.RecipeDTO;
import com.psh.diet_planner.model.Recipe;
import com.psh.diet_planner.service.RecipeService;
import com.psh.diet_planner.service.support.mcp.Neo4jMcpAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecipeServiceImpl implements RecipeService {
    private static final int DEFAULT_LIMIT = 10;
    private static final int DEFAULT_VECTOR_SEARCH_LIMIT = 50;

    private final Neo4jMcpAdapter neo4jMcpAdapter;
    
    @Override
    public List<Recipe> recommendRecipes(RecipeDTO recipeDTO) {
        // 统一入口：默认使用路径推荐，调用方应优先使用 recommendPathBased / recommendEmbeddingBased
        return neo4jMcpAdapter.recommendPathBasedRecipes("default", DEFAULT_LIMIT);
    }

    @Override
    public List<Recipe> recommendPathBased(String userId, int limit) {
        int finalLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        return neo4jMcpAdapter.recommendPathBasedRecipes(userId, finalLimit);
    }

    @Override
    public List<Recipe> recommendEmbeddingBased(String userId, int limit, int searchLimit) {
        int finalLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        int finalSearchLimit = searchLimit > 0 ? searchLimit : DEFAULT_VECTOR_SEARCH_LIMIT;
        return neo4jMcpAdapter.recommendEmbeddingBasedRecipes(userId, finalLimit, finalSearchLimit);
    }
    
    @Override
    public List<Recipe> searchRecipes(String keyword) {
        return neo4jMcpAdapter.searchRecipesByName(keyword);
    }
    
    @Override
    public Recipe getRecipeById(String id) {
        return neo4jMcpAdapter.findRecipeByIdOrName(id);
    }
    
    @Override
    public List<Recipe> getRecommendationHistory(Long userId) {
        // 可接入日志/历史表；当前返回空列表以避免空指针
        return List.of();
    }
}