package com.psh.diet_planner.model;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Data
@Node("Ingredient")
public class Ingredient {
    @Id
    @GeneratedValue
    private Long id;
    
    private String name;
    private String category;
    private Double quantity;
}