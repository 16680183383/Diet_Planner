package com.psh.mcp.neo4j.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.psh.mcp.neo4j.service.CypherExecutionService;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServlet;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(McpServerProperties.class)
public class McpServerConfig {

    @Bean
    public HttpServletStreamableServerTransportProvider streamableTransportProvider(McpServerProperties properties) {
        return HttpServletStreamableServerTransportProvider.builder()
            .mcpEndpoint(properties.getEndpoint())
            .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServlet> mcpServletRegistration(
        HttpServletStreamableServerTransportProvider provider,
        McpServerProperties properties
    ) {
        String endpoint = properties.getEndpoint();
        String mapping = endpoint.endsWith("/*") ? endpoint : endpoint + "/*";
        return new ServletRegistrationBean<>(provider, mapping);
    }

    @Bean
    public McpSyncServer mcpSyncServer(
        HttpServletStreamableServerTransportProvider provider,
        McpServerProperties properties,
        CypherExecutionService executionService,
        ObjectMapper objectMapper
    ) {
        McpSchema.Tool queryTool = McpSchema.Tool.builder()
            .name(properties.getToolCypherQuery())
            .description("Execute read-only Cypher query and return rows.")
            .inputSchema(simpleInputSchema())
            .build();

        McpSchema.Tool writeTool = McpSchema.Tool.builder()
            .name(properties.getToolCypherWrite())
            .description("Execute write Cypher query and return updated count.")
            .inputSchema(simpleInputSchema())
            .build();

        McpSchema.Tool txTool = McpSchema.Tool.builder()
            .name(properties.getToolCypherTransaction())
            .description("Execute Cypher statements in a single transaction.")
            .inputSchema(transactionInputSchema())
            .build();

        return McpServer.sync(provider)
            .serverInfo("neo4j-mcp-bridge", "1.0.0")
            .toolCall(queryTool, (exchange, request) -> toResult(objectMapper, executionService.query(request.arguments())))
            .toolCall(writeTool, (exchange, request) -> toResult(objectMapper, executionService.write(request.arguments())))
            .toolCall(txTool, (exchange, request) -> toResult(objectMapper, executionService.transaction(request.arguments())))
            .build();
    }

    private McpSchema.CallToolResult toResult(ObjectMapper objectMapper, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return McpSchema.CallToolResult.builder()
                .addTextContent(json)
                .isError(false)
                .build();
        } catch (Exception e) {
            return McpSchema.CallToolResult.builder()
                .addTextContent("{\"error\":\"" + safeJson(e.getMessage()) + "\"}")
                .isError(true)
                .build();
        }
    }

    private String safeJson(String text) {
        if (text == null) {
            return "unknown";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private McpSchema.JsonSchema simpleInputSchema() {
        return new McpSchema.JsonSchema(
            "object",
            Map.of(
                "query", Map.of("type", "string"),
                "params", Map.of("type", "object")
            ),
            List.of("query"),
            true,
            Map.of(),
            Map.of()
        );
    }

    private McpSchema.JsonSchema transactionInputSchema() {
        return new McpSchema.JsonSchema(
            "object",
            Map.of(
                "statements", Map.of("type", "array"),
                "writeOnly", Map.of("type", "boolean")
            ),
            List.of("statements"),
            true,
            Map.of(),
            Map.of()
        );
    }
}
