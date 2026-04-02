package com.psh.diet_planner.service.support.mcp.json;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.McpJsonMapperSupplier;

public class Jackson2McpJsonMapperSupplier implements McpJsonMapperSupplier {

    @Override
    public McpJsonMapper get() {
        return new Jackson2McpJsonMapper();
    }
}
