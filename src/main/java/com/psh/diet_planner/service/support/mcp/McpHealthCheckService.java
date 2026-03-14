package com.psh.diet_planner.service.support.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class McpHealthCheckService {

    private final Neo4jMcpAdapter neo4jMcpAdapter;

    public McpHealthCheckService(Neo4jMcpAdapter neo4jMcpAdapter) {
        this.neo4jMcpAdapter = neo4jMcpAdapter;
    }

    public Map<String, Object> check() {
        long start = System.currentTimeMillis();
        Map<String, Object> details = new LinkedHashMap<>();

        try {
            List<Map<String, Object>> queryRows = neo4jMcpAdapter.executeCypher("RETURN 1 AS ok", Map.of());
            boolean queryOk = queryRows != null && !queryRows.isEmpty();
            details.put("queryTool", queryOk ? "ok" : "failed");

            int writeUpdated = neo4jMcpAdapter.executeWrite("RETURN 1 AS ok", Map.of());
            details.put("writeTool", "ok");
            details.put("writeUpdated", writeUpdated);

            List<Map<String, Object>> txRows = neo4jMcpAdapter.executeInTransaction(
                List.of(new Neo4jMcpAdapter.CypherStatement("RETURN 1 AS ok", Map.of()))
            );
            boolean txOk = txRows != null && !txRows.isEmpty();
            details.put("transactionTool", txOk ? "ok" : "failed");

            long latency = System.currentTimeMillis() - start;
            details.put("status", "UP");
            details.put("color", "GREEN");
            details.put("latencyMs", latency);
            details.put("message", "MCP bridge to Neo4j is healthy");
            return details;
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            details.put("status", "DOWN");
            details.put("color", "RED");
            details.put("latencyMs", latency);
            details.put("message", ex.getMessage());
            return details;
        }
    }
}
