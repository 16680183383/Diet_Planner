package com.psh.diet_planner.dto;

import java.util.List;
import lombok.Data;

@Data
public class ShoppingListRequest {
    private List<String> recipeIds;
    private String userId;
    /** 单菜谱名称（前端从菜谱详情弹窗加入时使用） */
    private String recipeName;
    /** 份数，默认 2 */
    private Integer servings;
}




