package com.psh.diet_planner.service.support.mcp;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class McpStartupHealthLogger implements ApplicationRunner {

    private final McpHealthCheckService mcpHealthCheckService;

    public McpStartupHealthLogger(McpHealthCheckService mcpHealthCheckService) {
        this.mcpHealthCheckService = mcpHealthCheckService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, Object> health = mcpHealthCheckService.check();
        String status = String.valueOf(health.getOrDefault("status", "UNKNOWN"));
        String color = String.valueOf(health.getOrDefault("color", "GRAY"));
        Object latency = health.getOrDefault("latencyMs", -1);
        Object message = health.getOrDefault("message", "");

        if ("UP".equals(status)) {
            log.info("MCP startup self-check: status={}, color={}, latencyMs={}, message={}", status, color, latency, message);
        } else {
            log.warn("MCP startup self-check: status={}, color={}, latencyMs={}, message={}", status, color, latency, message);
        }
    }
}
