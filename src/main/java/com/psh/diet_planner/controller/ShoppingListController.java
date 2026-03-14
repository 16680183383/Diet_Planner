package com.psh.diet_planner.controller;

import com.psh.diet_planner.dto.ApiResponse;
import com.psh.diet_planner.dto.ShoppingListRequest;
import com.psh.diet_planner.dto.ShoppingListResponse;
import com.psh.diet_planner.model.Recipe;
import com.psh.diet_planner.model.ShoppingListItem;
import com.psh.diet_planner.repository.ShoppingListItemRepository;
import com.psh.diet_planner.service.RecipeService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shoppinglist")
public class ShoppingListController {

    private final RecipeService recipeService;
    private final ShoppingListItemRepository shopRepo;

    public ShoppingListController(RecipeService recipeService, ShoppingListItemRepository shopRepo) {
        this.recipeService = recipeService;
        this.shopRepo = shopRepo;
    }

    @PostMapping("/generate")
    public ApiResponse<ShoppingListResponse> generate(@RequestBody ShoppingListRequest request) {
        // 兼容两种调用方式：recipeIds 列表 或 单个 recipeName
        List<String> names = new ArrayList<>();
        if (request.getRecipeIds() != null && !request.getRecipeIds().isEmpty()) {
            names.addAll(request.getRecipeIds());
        } else if (request.getRecipeName() != null && !request.getRecipeName().isBlank()) {
            names.add(request.getRecipeName());
        } else {
            return ApiResponse.error("请至少提供一个菜谱名称");
        }

        if (request.getUserId() == null || request.getUserId().isBlank()) {
            return ApiResponse.error("缺少 userId");
        }
        Long uid;
        try { uid = Long.parseLong(request.getUserId()); }
        catch (NumberFormatException e) { return ApiResponse.error("userId 必须是数字"); }

        int servings = request.getServings() != null && request.getServings() > 0
                ? request.getServings()
                : 1;

        List<String> uniqueIngredients = new ArrayList<>();
        for (String recipeName : names) {
            Recipe recipe = recipeService.getRecipeById(recipeName);
            if (recipe != null && recipe.getIngredients() != null) {
                for (String ing : recipe.getIngredients().split("[,，、\\n]+")) {
                    String trimmed = ing.trim();
                    if (!trimmed.isEmpty() && !uniqueIngredients.contains(trimmed)) {
                        uniqueIngredients.add(trimmed);
                    }
                }
            }
        }

        // 按份数持久化到 MySQL。当前没有精确克重数据，因此按“份数=件数倍数”处理。
        String recipeName = names.get(0);
        List<String> responseItems = new ArrayList<>();
        for (String ing : uniqueIngredients) {
            for (int i = 0; i < servings; i++) {
                ShoppingListItem item = new ShoppingListItem();
                item.setUserId(uid);
                item.setRecipeName(recipeName);
                item.setIngredientName(ing);
                item.setCreatedAt(LocalDateTime.now());
                shopRepo.save(item);
                responseItems.add(ing);
            }
        }

        ShoppingListResponse response = new ShoppingListResponse();
        response.setTotalItems(responseItems.size());
        response.setGeneratedAt(System.currentTimeMillis());
        response.setItems(responseItems);
        return ApiResponse.success(response, "购物清单生成成功");
    }

    @GetMapping("/by-user")
    public ApiResponse<ShoppingListResponse> getByUser(@RequestParam String userId) {
        Long uid;
        try { uid = Long.parseLong(userId); }
        catch (NumberFormatException e) { return ApiResponse.error("userId 必须是数字"); }

        List<ShoppingListItem> rows = shopRepo.findByUserIdOrderByCreatedAtDesc(uid);
        ShoppingListResponse resp = new ShoppingListResponse();
        List<String> items = new ArrayList<>();
        for (ShoppingListItem r : rows) {
            items.add(r.getIngredientName());
        }
        resp.setItems(items);
        resp.setTotalItems(items.size());
        resp.setGeneratedAt(System.currentTimeMillis());
        return ApiResponse.success(resp, rows.isEmpty() ? "暂无购物清单" : "获取购物清单成功");
    }

    @DeleteMapping("/clear")
    public ApiResponse<Void> clear(@RequestParam String userId) {
        Long uid;
        try { uid = Long.parseLong(userId); }
        catch (NumberFormatException e) { return ApiResponse.error("userId 必须是数字"); }
        shopRepo.deleteByUserId(uid);
        return ApiResponse.success(null, "购物清单已清空");
    }
}
