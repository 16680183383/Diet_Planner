package com.psh.diet_planner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PairingCandidate {
    private String name;
    private double similarityScore;
    private String reason;
    private boolean expertLinked;
}
