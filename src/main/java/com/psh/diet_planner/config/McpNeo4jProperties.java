package com.psh.diet_planner.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.mcp.neo4j")
public class McpNeo4jProperties {

    private boolean enabled = true;
    private String baseUrl = "http://localhost:3001";
    private String endpoint = "/mcp";
    private String apiKey;

    private int connectTimeoutMs = 3000;
    private int requestTimeoutMs = 10000;
    private int initializationTimeoutMs = 10000;

    private int maxRetries = 3;
    private long retryBackoffMs = 300;
    private int maxConcurrency = 16;

    private String toolCypherQuery = "neo4j.cypher.query";
    private String toolCypherWrite = "neo4j.cypher.write";
    private String toolCypherTransaction = "neo4j.cypher.transaction";
}
