package com.psh.diet_planner.service.support.mcp;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class McpHealthCheckService {

    private final Neo4jMcpAdapter neo4jMcpAdapter;

    public McpHealthCheckService(Neo4jMcpAdapter neo4jMcpAdapter) {
        this.neo4jMcpAdapter = neo4jMcpAdapter;
    }

    public Map<String, Object> check() {
        Map<String, Object> details = neo4jMcpAdapter.healthCheck();
        return details == null ? new LinkedHashMap<>() : details;
    }
}
