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
public class PairingResponse {
    private String sourceFood;
    private List<String> suggestions;
    private int total;
}
