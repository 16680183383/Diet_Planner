package com.psh.diet_planner.model;

import java.util.List;
import lombok.Data;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

@Data
@Node("Recipe")
public class Recipe {
    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;
    
    private String name;
    private String ingredients;
    // 具体食材（含用量/单位等原始文本，作为补充详情保存）
    private String detailedIngredients;
    private String steps;

    @Property("rfr_embedding")
    private List<Double> rfrEmbedding;

    @Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
    private List<Recipe> relatedRecipes;

    @Relationship(type = "CONTAINS", direction = Relationship.Direction.OUTGOING)
    private List<Food> foods;
}