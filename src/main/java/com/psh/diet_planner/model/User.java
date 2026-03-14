package com.psh.diet_planner.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import java.util.List;

@Data
@Setter
@Getter
@Node("User")
public class User {
    @Id
    @GeneratedValue
    private Long id;
    
    private String username;
    private String password;
    private Integer age;
    private String gender;
    private Double weight;
    private Double height;
    private String preferences;
    private String restrictions;

    private String activityLevel;
    private String goal;
    private List<String> allergens;
    private List<String> illnesses;
    private String flavorPref;
}