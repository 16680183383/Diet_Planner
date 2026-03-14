# mcp-neo4j-server

独立 MCP 服务端项目（Java + Spring Boot），作为 Diet_Planner 与 Neo4j 的桥梁。

## 提供的 MCP Tools

- `neo4j.cypher.query`: 读查询，返回 `{ "rows": [...] }`
- `neo4j.cypher.write`: 写查询，返回 `{ "updated": <int> }`
- `neo4j.cypher.transaction`: 事务执行，支持多语句与 `writeOnly`

## 启动

1. 配置 Neo4j 连接（`src/main/resources/application.properties`）
2. 在本目录执行：

```bash
mvnw.cmd spring-boot:run
```

默认监听 `http://localhost:3001`，MCP 端点为 `/mcp`。

## 与 Diet_Planner 对齐

Diet_Planner 中已配置：

- `app.mcp.neo4j.base-url=http://localhost:3001`
- `app.mcp.neo4j.endpoint=/mcp`
- `app.mcp.neo4j.tool-cypher-query=neo4j.cypher.query`
- `app.mcp.neo4j.tool-cypher-write=neo4j.cypher.write`
- `app.mcp.neo4j.tool-cypher-transaction=neo4j.cypher.transaction`

只要本服务启动，Diet_Planner 的 MCP 适配层即可连通。
