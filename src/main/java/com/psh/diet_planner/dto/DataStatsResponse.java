package com.psh.diet_planner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DataStatsResponse {
    private long foodCount;
    private long recipeCount;
    private long complementaryCount;
    private long incompatibleCount;
    private long containsCount;
}
