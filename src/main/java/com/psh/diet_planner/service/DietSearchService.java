package com.psh.diet_planner.service;

import com.psh.diet_planner.dto.AllAspectsResponse;
import com.psh.diet_planner.dto.DietQueryRequest;
import com.psh.diet_planner.dto.DietSearchResponse;
import com.psh.diet_planner.dto.RecipeDTO;
import com.psh.diet_planner.dto.RelationExploreRequest;
import com.psh.diet_planner.dto.RelationExploreResponse;
import java.util.List;
import com.psh.diet_planner.model.SearchIntent;

import java.util.List;

public interface DietSearchService {
    List<DietSearchResponse> comprehensiveSearch(DietQueryRequest request);

    AllAspectsResponse fullAnalysis(DietQueryRequest request);

    String generateAiAdvice(String foodName, SearchIntent intent, List<DietSearchResponse> results, Long userId);

    List<RecipeDTO> findSimilarRecipes(String recipeName);
    RelationExploreResponse exploreRelations(RelationExploreRequest request);
}
