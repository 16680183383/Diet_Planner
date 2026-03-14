package com.psh.diet_planner.dto;

import com.psh.diet_planner.model.Ingredient;
import lombok.Data;

import java.util.List;

@Data
public class ShoppingListDTO {
    private List<Ingredient> ingredients;
    private Integer totalItems;
    private Long generatedAt;
}
