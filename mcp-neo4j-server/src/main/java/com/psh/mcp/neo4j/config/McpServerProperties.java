package com.psh.mcp.neo4j.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mcp.server")
public class McpServerProperties {

    private String endpoint = "/mcp";
    private String toolCypherQuery = "neo4j.cypher.query";
    private String toolCypherWrite = "neo4j.cypher.write";
    private String toolCypherTransaction = "neo4j.cypher.transaction";

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getToolCypherQuery() {
        return toolCypherQuery;
    }

    public void setToolCypherQuery(String toolCypherQuery) {
        this.toolCypherQuery = toolCypherQuery;
    }

    public String getToolCypherWrite() {
        return toolCypherWrite;
    }

    public void setToolCypherWrite(String toolCypherWrite) {
        this.toolCypherWrite = toolCypherWrite;
    }

    public String getToolCypherTransaction() {
        return toolCypherTransaction;
    }

    public void setToolCypherTransaction(String toolCypherTransaction) {
        this.toolCypherTransaction = toolCypherTransaction;
    }
}
