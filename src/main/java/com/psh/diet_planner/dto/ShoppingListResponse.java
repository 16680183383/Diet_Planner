package com.psh.diet_planner.dto;

import lombok.Data;
import com.psh.diet_planner.model.Ingredient;
import java.util.List;

@Data
public class ShoppingListResponse {
    private List<Ingredient> ingredients;
    private List<String> items;
    private Integer totalItems;
    private Long generatedAt;
}