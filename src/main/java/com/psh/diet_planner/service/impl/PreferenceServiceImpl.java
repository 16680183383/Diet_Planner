package com.psh.diet_planner.service.impl;

import com.psh.diet_planner.model.IngredientPreference;
import com.psh.diet_planner.model.RecipePreference;
import com.psh.diet_planner.repository.IngredientPreferenceRepository;
import com.psh.diet_planner.repository.RecipePreferenceRepository;
import com.psh.diet_planner.service.PreferenceService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferenceServiceImpl implements PreferenceService {

    private static final Logger log = LoggerFactory.getLogger(PreferenceServiceImpl.class);

    private final RecipePreferenceRepository recipePreferenceRepository;
    private final IngredientPreferenceRepository ingredientPreferenceRepository;
    private final Neo4jPreferenceService neo4jPreferenceService;

    public PreferenceServiceImpl(RecipePreferenceRepository recipePreferenceRepository,
                                 IngredientPreferenceRepository ingredientPreferenceRepository,
                                 Neo4jPreferenceService neo4jPreferenceService) {
        this.recipePreferenceRepository = recipePreferenceRepository;
        this.ingredientPreferenceRepository = ingredientPreferenceRepository;
        this.neo4jPreferenceService = neo4jPreferenceService;
    }

    /** 尝试更新 Neo4j 关系，失败则仅记录日志，不影响 MySQL 写入 */
    private void tryNeo4j(Runnable neo4jOp, String desc) {
        try {
            neo4jOp.run();
        } catch (Exception e) {
            log.warn("Neo4j {} 操作跳过（节点不存在或连接异常）: {}", desc, e.getMessage());
        }
    }

    @Override
    @Transactional
    public void likeRecipe(String userId, String recipeId) {
        // 1. MySQL 持久化（无条件执行）
        RecipePreference pref = recipePreferenceRepository
            .findByUserIdAndRecipeId(userId, recipeId)
            .orElseGet(RecipePreference::new);
        pref.setUserId(userId);
        pref.setRecipeId(recipeId);
        pref.setPreference(RecipePreference.PreferenceType.LIKE);
        pref.setUpdatedAt(LocalDateTime.now());
        pref.setReason(null);
        recipePreferenceRepository.save(pref);
        // 2. 尝试通过 MCP 同步图偏好关系
        tryNeo4j(() -> neo4jPreferenceService.likeRecipe(userId, recipeId), "likeRecipe");
    }

    @Override
    @Transactional
    public void dislikeRecipe(String userId, String recipeId) {
        RecipePreference pref = recipePreferenceRepository
            .findByUserIdAndRecipeId(userId, recipeId)
            .orElseGet(RecipePreference::new);
        pref.setUserId(userId);
        pref.setRecipeId(recipeId);
        pref.setPreference(RecipePreference.PreferenceType.NOT_INTERESTED);
        pref.setUpdatedAt(LocalDateTime.now());
        recipePreferenceRepository.save(pref);
        tryNeo4j(() -> neo4jPreferenceService.dislikeRecipe(userId, recipeId), "dislikeRecipe");
    }

    @Override
    @Transactional
    public void favoriteIngredient(String userId, String ingredientName) {
        IngredientPreference pref = ingredientPreferenceRepository
            .findByUserIdAndIngredientName(userId, ingredientName)
            .orElseGet(IngredientPreference::new);
        pref.setUserId(userId);
        pref.setIngredientName(ingredientName);
        pref.setPreference(IngredientPreference.PreferenceType.FAVORITE);
        pref.setUpdatedAt(LocalDateTime.now());
        pref.setReason(null);
        ingredientPreferenceRepository.save(pref);
        tryNeo4j(() -> neo4jPreferenceService.favoriteIngredient(userId, ingredientName), "favoriteIngredient");
    }

    @Override
    @Transactional
    public void dislikeIngredient(String userId, String ingredientName, String reason) {
        IngredientPreference pref = ingredientPreferenceRepository
            .findByUserIdAndIngredientName(userId, ingredientName)
            .orElseGet(IngredientPreference::new);
        pref.setUserId(userId);
        pref.setIngredientName(ingredientName);
        pref.setPreference(IngredientPreference.PreferenceType.DISLIKES);
        pref.setUpdatedAt(LocalDateTime.now());
        pref.setReason(reason);
        ingredientPreferenceRepository.save(pref);
        tryNeo4j(() -> neo4jPreferenceService.dislikeIngredient(userId, ingredientName, reason), "dislikeIngredient");
    }

    @Override
    public Map<String, Object> getUserPreferences(String userId) {
        List<RecipePreference> recipePrefs = recipePreferenceRepository.findByUserId(userId);
        List<IngredientPreference> ingredientPrefs = ingredientPreferenceRepository.findByUserId(userId);
        return Map.of(
            "recipePreferences", recipePrefs,
            "ingredientPreferences", ingredientPrefs
        );
    }
}
