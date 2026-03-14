package com.psh.diet_planner.model;

import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.data.annotation.Transient;
import org.springframework.data.neo4j.core.schema.CompositeProperty;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

@Data
@Node("Food")
public class Food {
    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;
    
    private String name;
    private String nutritionalValue;
    private String healthBenefits;
    private String suitableFor;
    private String contraindications;

    private List<Double> embedding;

    @Property("metapath_embedding")
    private List<Double> metapathEmbedding;

    @Property("sage_embedding")
    private List<Double> sageEmbedding;

    @Property("comp_embedding")
    private List<Double> compEmbedding;

    @Property("incomp_embedding")
    private List<Double> incompEmbedding;

    @Property("overlap_embedding")
    private List<Double> overlapEmbedding;

    @Property("rfr_embedding")
    private List<Double> generalEmbedding;

    @Transient
    private Double score;

    // 营养成分，如蛋白质、脂肪、碳水化合物等（以复合属性展开为多个 primitive 属性）
    @CompositeProperty(prefix = "nutrients")
    private Map<String, String> nutrients;
    
    @Relationship(type = "COMPLEMENTARY", direction = Relationship.Direction.OUTGOING)
    private List<FoodRelationship> complementary;
    
    @Relationship(type = "INCOMPATIBLE", direction = Relationship.Direction.OUTGOING)
    private List<FoodRelationship> incompatible;
    
    @Relationship(type = "OVERLAP")
    private List<FoodRelationship> overlap;
}