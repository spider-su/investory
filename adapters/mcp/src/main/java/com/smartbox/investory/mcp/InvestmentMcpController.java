package com.smartbox.investory.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Small stateless MCP JSON-RPC draft; use the official SDK before public deployment. */
@RestController
@RequestMapping("/mcp")
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class InvestmentMcpController {
  private static final String PROTOCOL_VERSION = "2025-03-26";
  private final InvestmentMcpClient investment;

  public InvestmentMcpController(InvestmentMcpClient investment) {
    this.investment = investment;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<?> handle(@RequestBody Map<String, Object> request) {
    Object id = request.get("id");
    if ("notifications/initialized".equals(request.get("method")) && id == null) {
      return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
    if (!"2.0".equals(request.get("jsonrpc")) || id == null
        || !(request.get("method") instanceof String method)) {
      return ResponseEntity.ok(error(id, -32600, "Invalid Request"));
    }
    try {
      Object result =
          switch (method) {
            case "initialize" ->
                Map.of(
                    "protocolVersion", PROTOCOL_VERSION,
                    "capabilities", Map.of("tools", Map.of()),
                    "serverInfo", Map.of("name", "investory", "version", "0.1.0"));
            case "ping" -> Map.of();
            case "tools/list" -> Map.of("tools", tools());
            case "tools/call" -> call(request.get("params"));
            default -> null;
          };
      if (result == null) return ResponseEntity.ok(error(id, -32601, "Method not found"));
      return ResponseEntity.ok(Map.of("jsonrpc", "2.0", "id", id, "result", result));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.ok(error(id, -32602, ex.getMessage()));
    } catch (Exception ex) {
      // Do not leak database details, user data or internal exception messages to the client.
      return ResponseEntity.ok(error(id, -32603, "Internal error"));
    }
  }

  private Object call(Object rawParams) {
    if (!(rawParams instanceof Map<?, ?> params)) {
      throw new IllegalArgumentException("params must be an object");
    }
    if (!(params.get("name") instanceof String name)) {
      throw new IllegalArgumentException("name is required");
    }
    if (!(params.get("arguments") instanceof Map<?, ?> arguments)) {
      throw new IllegalArgumentException("arguments must be an object");
    }
    Object rawId = arguments.get("portfolioId");
    if (!(rawId instanceof Number number) || number.longValue() <= 0
        || number.doubleValue() != number.longValue()) {
      throw new IllegalArgumentException("portfolioId must be a positive integer");
    }
    long portfolioId = number.longValue();
    Object value =
        switch (name) {
          case "get_portfolio_dashboard" -> investment.dashboard(portfolioId);
          case "get_portfolio_performance" -> investment.performance(portfolioId);
          case "get_investment_ytd" -> investment.yearToDate(portfolioId);
          default -> throw new IllegalArgumentException("Unknown tool");
        };
    return Map.of("structuredContent", Map.of("data", value), "content",
        List.of(Map.of("type", "text", "text", "See structuredContent.data")));
  }

  private static List<Map<String, Object>> tools() {
    return List.of(
        tool("get_portfolio_dashboard", "Read canonical investment dashboard for a portfolio"),
        tool("get_portfolio_performance", "Read canonical portfolio performance KPI"),
        tool("get_investment_ytd", "Read current-year investment result in portfolio base currency"));
  }

  private static Map<String, Object> tool(String name, String description) {
    return Map.of(
        "name", name,
        "description", description,
        "inputSchema", Map.of(
            "type", "object",
            "properties", Map.of("portfolioId", Map.of("type", "integer", "minimum", 1)),
            "required", List.of("portfolioId"),
            "additionalProperties", false));
  }

  private static Map<String, Object> error(Object id, int code, String message) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("jsonrpc", "2.0");
    response.put("id", id);
    response.put("error", Map.of("code", code, "message", message));
    return response;
  }
}
