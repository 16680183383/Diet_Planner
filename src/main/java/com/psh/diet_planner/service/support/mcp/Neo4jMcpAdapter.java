package com.psh.diet_planner.service.support.mcp;

import java.util.List;
import java.util.Map;
import com.psh.diet_planner.dto.RelationExploreDTO;
import com.psh.diet_planner.model.Recipe;

public interface Neo4jMcpAdapter {

    record CypherStatement(String query, Map<String, Object> params) {}

    List<Map<String, Object>> executeCypher(String query, Map<String, Object> params);

    int executeWrite(String query, Map<String, Object> params);

    List<Map<String, Object>> executeInTransaction(List<CypherStatement> statements);

    int executeBatchWrites(List<CypherStatement> statements);

    Map<String, Object> healthCheck();

    List<Map<String, Object>> findSemanticSubstitutes(String foodName, int limit);

    List<Map<String, Object>> findPairingCandidates(String foodName, int limit);

    List<String> findSmartPairings(String foodName, int limit);

    List<String> findRecipesByIngredients(List<String> ingredients, int limit);

    List<String> checkIncompatibilities(List<String> foodNames);

    void saveSageEmbedding(String foodName, List<Double> embedding);

    List<String> findFoodsMissingSageEmbedding();

    Map<String, Object> findFoodMetapathEmbeddings(String foodName);

    List<Map<String, Object>> findAllFoodMetapathEmbeddings();

    List<Map<String, Object>> findNeighborMetapathEmbeddings(String foodName, String relationType);

    List<Map<String, Object>> findRecipeNeighborEmbeddings(String foodName);

    Map<String, Object> loadFoodEmbeddings(String foodName);

    List<Map<String, Object>> findFoodSimilarityByIndex(String foodName, String indexName, int limit);

    List<RelationExploreDTO> exploreFoodRelations(String foodName, String indexName, String relationType, int limit);

    List<String> findRelationsBetween(String sourceFood, String targetFood);

    List<Map<String, Object>> findSimilarRecipesByName(String recipeName, int limit);

    List<Recipe> recommendPathBasedRecipes(String userId, int limit);

    List<Recipe> recommendEmbeddingBasedRecipes(String userId, int limit, int searchLimit);

    List<Recipe> searchRecipesByName(String keyword);

    Recipe findRecipeByIdOrName(String idOrName);

    void likeRecipe(String userId, String recipeId);

    void dislikeRecipe(String userId, String recipeId);

    void favoriteIngredient(String userId, String ingredientName);

    void dislikeIngredient(String userId, String ingredientName, String reason);

    Map<String, Object> findFoodByName(String name);

    void mergeFoodNode(String name);

    void upsertFoodDetails(String name,
                           String nutritionalValue,
                           String healthBenefits,
                           String suitableFor,
                           String contraindications,
                           Map<String, String> nutrients);

    void deleteFoodByName(String name);

    long countFoods();

    long countRecipes();

    long countComplementary();

    long countIncompatible();

    long countContainsRelations();

    Map<String, Object> loadRelationSummary(String name);

    List<RelationExploreDTO> exploreGeneralRelations(String foodName, int limit);

    void mergeComplementaryRelation(String source, String target, String description);

    void mergeIncompatibleRelation(String source, String target, String description);

    void mergeOverlapRelation(String source, String target, String description);

    Recipe upsertRecipe(String name, String ingredients, String detailedIngredients, String steps);

    void linkContains(String recipeName, String foodName);
}
