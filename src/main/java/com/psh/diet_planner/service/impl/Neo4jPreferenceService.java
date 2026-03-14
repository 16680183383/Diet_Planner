package com.psh.diet_planner.service.impl;

import com.psh.diet_planner.service.support.mcp.Neo4jMcpAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 专门负责偏好关系写入，通过 MCP 网桥写入 Neo4j。
 * 必须与 PreferenceServiceImpl 分离，避免在 JPA 事务中耦合图数据库访问。
 */
@Service
public class Neo4jPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(Neo4jPreferenceService.class);

    private final Neo4jMcpAdapter neo4jMcpAdapter;

    public Neo4jPreferenceService(Neo4jMcpAdapter neo4jMcpAdapter) {
        this.neo4jMcpAdapter = neo4jMcpAdapter;
    }

    public void likeRecipe(String userId, String recipeId) {
        log.info("Neo4j: merging LIKES for user={}, recipe={}", userId, recipeId);
        neo4jMcpAdapter.likeRecipe(userId, recipeId);
        log.info("Neo4j: LIKES merged successfully");
    }

    public void dislikeRecipe(String userId, String recipeId) {
        log.info("Neo4j: merging NOT_INTERESTED for user={}, recipe={}", userId, recipeId);
        neo4jMcpAdapter.dislikeRecipe(userId, recipeId);
    }

    public void favoriteIngredient(String userId, String ingredientName) {
        log.info("Neo4j: merging FAVORITE for user={}, ingredient={}", userId, ingredientName);
        neo4jMcpAdapter.favoriteIngredient(userId, ingredientName);
    }

    public void dislikeIngredient(String userId, String ingredientName, String reason) {
        log.info("Neo4j: merging DISLIKES for user={}, ingredient={}", userId, ingredientName);
        neo4jMcpAdapter.dislikeIngredient(userId, ingredientName, reason);
    }
}
