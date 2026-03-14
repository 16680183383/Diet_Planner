package com.psh.diet_planner.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationPathDTO {
    private String relationType;
    private String targetFood;
    private Double score;
    private List<String> relatedRecipes;
}

