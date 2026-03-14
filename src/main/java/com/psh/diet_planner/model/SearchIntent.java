package com.psh.diet_planner.model;

public enum SearchIntent {
    COMP,
    INCOMP,
    OVERLAP,
    RECIPE,
    ALL;

    public boolean isRecipe() {
        return this == RECIPE || this == ALL;
    }
}
