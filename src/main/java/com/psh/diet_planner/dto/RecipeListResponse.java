package com.psh.diet_planner.dto;

import lombok.Data;
import java.util.List;

@Data
public class RecipeListResponse {
    private List<RecipeResponse> recipes;
}