package com.psh.diet_planner.service.support.mcp.json;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import java.io.IOException;

public class Jackson2McpJsonMapper implements McpJsonMapper {

    private final ObjectMapper objectMapper;

    public Jackson2McpJsonMapper() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public <T> T readValue(String content, Class<T> valueType) throws IOException {
        return objectMapper.readValue(content, valueType);
    }

    @Override
    public <T> T readValue(byte[] src, Class<T> valueType) throws IOException {
        return objectMapper.readValue(src, valueType);
    }

    @Override
    public <T> T readValue(String content, TypeRef<T> valueTypeRef) throws IOException {
        JavaType javaType = objectMapper.getTypeFactory().constructType(valueTypeRef.getType());
        return objectMapper.readValue(content, javaType);
    }

    @Override
    public <T> T readValue(byte[] src, TypeRef<T> valueTypeRef) throws IOException {
        JavaType javaType = objectMapper.getTypeFactory().constructType(valueTypeRef.getType());
        return objectMapper.readValue(src, javaType);
    }

    @Override
    public <T> T convertValue(Object fromValue, Class<T> toValueType) {
        return objectMapper.convertValue(fromValue, toValueType);
    }

    @Override
    public <T> T convertValue(Object fromValue, TypeRef<T> toValueTypeRef) {
        JavaType javaType = objectMapper.getTypeFactory().constructType(toValueTypeRef.getType());
        return objectMapper.convertValue(fromValue, javaType);
    }

    @Override
    public String writeValueAsString(Object value) throws IOException {
        return objectMapper.writeValueAsString(value);
    }

    @Override
    public byte[] writeValueAsBytes(Object value) throws IOException {
        return objectMapper.writeValueAsBytes(value);
    }
}
