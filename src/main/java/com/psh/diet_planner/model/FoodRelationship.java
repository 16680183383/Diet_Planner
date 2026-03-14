package com.psh.diet_planner.model;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

@Data
@RelationshipProperties
public class FoodRelationship {
    
    @Id
    @GeneratedValue
    private Long id;

    @Property("description")
    private String description;
    
    @TargetNode
    private Food targetFood;
}