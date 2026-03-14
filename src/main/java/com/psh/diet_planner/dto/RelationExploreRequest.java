package com.psh.diet_planner.dto;

import com.psh.diet_planner.model.SearchIntent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RelationExploreRequest {
    @NotBlank(message = "食材名称不能为空")
    private String foodName;

    @NotNull(message = "intent 仅支持 COMP/INCOMP/OVERLAP/RECIPE")
    private SearchIntent intent;

    @NotNull
    @Min(1)
    @Max(50)
    private Integer topK = 5;

    @NotNull
    @Min(0)
    @Max(10)
    private Integer recipeLimit = 3;
}
