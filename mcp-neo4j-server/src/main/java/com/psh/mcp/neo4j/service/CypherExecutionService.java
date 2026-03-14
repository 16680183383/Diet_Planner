package com.psh.mcp.neo4j.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.driver.summary.SummaryCounters;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.stereotype.Service;

@Service
public class CypherExecutionService {

    private final Driver driver;

    public CypherExecutionService(Driver driver) {
        this.driver = driver;
    }

    public Map<String, Object> query(Map<String, Object> args) {
        String cypher = requireString(args, "query");
        Map<String, Object> params = asMap(args.get("params"));

        try (Session session = driver.session()) {
            List<Map<String, Object>> rows = session.executeRead(tx -> {
                List<Map<String, Object>> list = new ArrayList<>();
                List<Record> records = tx.run(cypher, params).list();
                for (Record record : records) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String key : record.keys()) {
                        row.put(key, convertValue(record.get(key)));
                    }
                    list.add(row);
                }
                return list;
            });
            return Map.of("rows", rows);
        }
    }

    public Map<String, Object> write(Map<String, Object> args) {
        String cypher = requireString(args, "query");
        Map<String, Object> params = asMap(args.get("params"));

        try (Session session = driver.session()) {
            int updated = session.executeWrite(tx -> {
                ResultSummary summary = tx.run(cypher, params).consume();
                return toUpdatedCount(summary.counters());
            });
            return Map.of("updated", updated);
        }
    }

    public Map<String, Object> transaction(Map<String, Object> args) {
        List<Map<String, Object>> statements = asStatementList(args.get("statements"));
        boolean writeOnly = Boolean.TRUE.equals(args.get("writeOnly"));

        try (Session session = driver.session()) {
            if (writeOnly) {
                int updated = session.executeWrite(tx -> {
                    int total = 0;
                    for (Map<String, Object> stmt : statements) {
                        String query = requireString(stmt, "query");
                        Map<String, Object> params = asMap(stmt.get("params"));
                        ResultSummary summary = tx.run(query, params).consume();
                        total += toUpdatedCount(summary.counters());
                    }
                    return total;
                });
                return Map.of("updated", updated);
            }

            List<Map<String, Object>> rows = session.executeWrite(tx -> {
                List<Map<String, Object>> mergedRows = new ArrayList<>();
                for (Map<String, Object> stmt : statements) {
                    String query = requireString(stmt, "query");
                    Map<String, Object> params = asMap(stmt.get("params"));
                    mergedRows.addAll(runAndCollectRows(tx, query, params));
                }
                return mergedRows;
            });
            return Map.of("rows", rows);
        }
    }

    private List<Map<String, Object>> runAndCollectRows(TransactionContext tx, String query, Map<String, Object> params) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Record> records = tx.run(query, params).list();
        for (Record record : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String key : record.keys()) {
                row.put(key, convertValue(record.get(key)));
            }
            rows.add(row);
        }
        return rows;
    }

    private Object convertValue(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }

        String typeName = value.type().name();
        return switch (typeName) {
            case "LIST" -> value.asList(this::convertValue);
            case "MAP" -> value.asMap(this::convertValue);
            case "NODE" -> toNodeMap(value.asNode());
            case "RELATIONSHIP" -> toRelationshipMap(value.asRelationship());
            case "PATH" -> value.asPath().toString();
            default -> value.asObject();
        };
    }

    private Map<String, Object> toNodeMap(Node node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("elementId", node.elementId());
        map.put("labels", node.labels());
        map.put("properties", node.asMap());
        return map;
    }

    private Map<String, Object> toRelationshipMap(Relationship rel) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("elementId", rel.elementId());
        map.put("type", rel.type());
        map.put("startNodeElementId", rel.startNodeElementId());
        map.put("endNodeElementId", rel.endNodeElementId());
        map.put("properties", rel.asMap());
        return map;
    }

    private int toUpdatedCount(SummaryCounters counters) {
        return counters.nodesCreated()
            + counters.nodesDeleted()
            + counters.relationshipsCreated()
            + counters.relationshipsDeleted()
            + counters.propertiesSet()
            + counters.labelsAdded()
            + counters.labelsRemoved()
            + counters.indexesAdded()
            + counters.indexesRemoved()
            + counters.constraintsAdded()
            + counters.constraintsRemoved();
    }

    private String requireString(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing required field: " + key);
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("empty required field: " + key);
        }
        return text;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return converted;
        }
        return Map.of();
    }

    private List<Map<String, Object>> asStatementList(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("statements must be a list");
        }
        List<Map<String, Object>> statements = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("statement item must be a map");
            }
            Map<String, Object> stmt = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                stmt.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            statements.add(stmt);
        }
        return statements;
    }
}
