package com.psh.diet_planner.service.support.mcp.json;

import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import java.util.Map;

public class NoOpJsonSchemaValidator implements JsonSchemaValidator {

    @Override
    public ValidationResponse validate(Map<String, Object> jsonSchema, Object object) {
        return ValidationResponse.asValid("{}");
    }
}
