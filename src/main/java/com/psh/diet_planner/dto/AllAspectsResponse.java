package com.psh.diet_planner.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AllAspectsResponse {
    private String foodName;
    private List<DietSearchResponse> complementary;
    private List<DietSearchResponse> incompatible;
    private List<DietSearchResponse> overlap;
    private List<DietSearchResponse> general;
    private String aiAnalysis;
}

