package com.psh.diet_planner.controller;

import com.psh.diet_planner.dto.ApiResponse;
import com.psh.diet_planner.dto.PreferenceRequest;
import com.psh.diet_planner.service.PreferenceService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/preferences")
public class PreferenceController {

    private final PreferenceService preferenceService;

    public PreferenceController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @PostMapping("/recipes/like")
    public ApiResponse<Void> likeRecipe(@RequestBody PreferenceRequest request) {
        try {
            preferenceService.likeRecipe(request.getUserId(), request.getRecipeName());
            return ApiResponse.success(null, "已收藏/喜欢该菜谱");
        } catch (Exception e) {
            return ApiResponse.error("操作失败: " + e.getMessage());
        }
    }

    @PostMapping("/recipes/dislike")
    public ApiResponse<Void> dislikeRecipe(@RequestBody PreferenceRequest request) {
        try {
            preferenceService.dislikeRecipe(request.getUserId(), request.getRecipeName());
            return ApiResponse.success(null, "已标记不感兴趣");
        } catch (Exception e) {
            return ApiResponse.error("操作失败: " + e.getMessage());
        }
    }

    @PostMapping("/ingredients/favorite")
    public ApiResponse<Void> favoriteIngredient(@RequestBody PreferenceRequest request) {
        try {
            preferenceService.favoriteIngredient(request.getUserId(), request.getIngredientName());
            return ApiResponse.success(null, "已标记喜爱食材");
        } catch (Exception e) {
            return ApiResponse.error("操作失败: " + e.getMessage());
        }
    }

    @PostMapping("/ingredients/dislike")
    public ApiResponse<Void> dislikeIngredient(@RequestBody PreferenceRequest request) {
        try {
            preferenceService.dislikeIngredient(request.getUserId(), request.getIngredientName(), request.getReason());
            return ApiResponse.success(null, "已标记避雷食材");
        } catch (Exception e) {
            return ApiResponse.error("操作失败: " + e.getMessage());
        }
    }

    @GetMapping("/user")
    public ApiResponse<Map<String, Object>> getUserPreferences(@RequestParam String userId) {
        try {
            return ApiResponse.success(preferenceService.getUserPreferences(userId), "获取用户偏好成功");
        } catch (Exception e) {
            return ApiResponse.error("获取失败: " + e.getMessage());
        }
    }
}
