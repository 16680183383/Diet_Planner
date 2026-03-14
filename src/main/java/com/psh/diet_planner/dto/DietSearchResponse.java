package com.psh.diet_planner.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DietSearchResponse {
    private String name;
    private Double similarityScore;
    private List<String> knownRelations;
    private String aiAnalysis;
}
