package com.psh.diet_planner.service.support.mcp.json;

import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.JsonSchemaValidatorSupplier;

public class NoOpJsonSchemaValidatorSupplier implements JsonSchemaValidatorSupplier {

    @Override
    public JsonSchemaValidator get() {
        return new NoOpJsonSchemaValidator();
    }
}
