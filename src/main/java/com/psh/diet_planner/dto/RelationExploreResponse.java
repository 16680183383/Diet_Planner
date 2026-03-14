package com.psh.diet_planner.dto;

import com.psh.diet_planner.model.SearchIntent;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationExploreResponse {
    private String sourceFood;
    private SearchIntent intent;
    private List<RelationPathDTO> relations;
}
