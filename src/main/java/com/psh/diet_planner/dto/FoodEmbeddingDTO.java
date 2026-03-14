package com.psh.diet_planner.dto;

public class FoodEmbeddingDTO {
    private final String name;
    private final boolean hasEmbedding;
    private final boolean hasCompEmbedding;
    private final boolean hasIncompEmbedding;
    private final boolean hasOverlapEmbedding;

    public FoodEmbeddingDTO(String name, boolean hasEmbedding, boolean hasCompEmbedding, boolean hasIncompEmbedding, boolean hasOverlapEmbedding) {
        this.name = name;
        this.hasEmbedding = hasEmbedding;
        this.hasCompEmbedding = hasCompEmbedding;
        this.hasIncompEmbedding = hasIncompEmbedding;
        this.hasOverlapEmbedding = hasOverlapEmbedding;
    }

    public String getName() {
        return name;
    }

    public boolean hasEmbedding() {
        return hasEmbedding;
    }

    public boolean hasCompEmbedding() {
        return hasCompEmbedding;
    }

    public boolean hasIncompEmbedding() {
        return hasIncompEmbedding;
    }

    public boolean hasOverlapEmbedding() {
        return hasOverlapEmbedding;
    }
}

