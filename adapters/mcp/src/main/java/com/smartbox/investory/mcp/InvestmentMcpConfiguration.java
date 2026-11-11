package com.smartbox.investory.mcp;

import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Configuration
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class InvestmentMcpConfiguration {
  @Bean
  @ConditionalOnMissingBean(InvestmentMcpClient.class)
  InvestmentMcpClient investmentMcpClient(InvestmentDashboardApi dashboard) {
    return new InProcessInvestmentMcpClient(dashboard);
  }

  @Bean
  List<McpServerFeatures.SyncToolSpecification> investmentMcpToolSpecifications(
      InvestmentMcpTools tools, ObjectMapper mapper) {
    return List.of(
        tool(
            "get_portfolio_dashboard",
            "Read the current investment dashboard, including positions and summaries.",
            tools::getPortfolioDashboard,
            mapper),
        tool(
            "get_portfolio_performance",
            "Read the portfolio's historical performance KPI and return context.",
            tools::getPortfolioPerformance,
            mapper),
        tool(
            "get_investment_ytd",
            "Read the current calendar year's investment result in base currency.",
            tools::getInvestmentYtd,
            mapper));
  }

  private static McpServerFeatures.SyncToolSpecification tool(
      String name, String description, LongFunction<Object> query, ObjectMapper mapper) {
    var specification =
        McpSchema.Tool.builder()
            .name(name)
            .description(description)
            .inputSchema(
                Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of(
                        "portfolioId",
                        Map.of(
                            "type", "integer",
                            "minimum", 1,
                            "description", "The portfolio ID to read.")),
                    "required",
                    List.of("portfolioId"),
                    "additionalProperties",
                    false))
            .build();
    return new McpServerFeatures.SyncToolSpecification(
        specification,
        (exchange, request) -> {
          Object argument = request.arguments().get("portfolioId");
          if (!(argument instanceof Number number)
              || number.longValue() <= 0
              || number.doubleValue() != number.longValue()) {
            return error("portfolioId must be a positive integer");
          }
          try {
            Object value = query.apply(number.longValue());
            String json = mapper.writeValueAsString(value);
            Map<String, Object> structured = mapper.readValue(json, new TypeReference<>() {});
            return McpSchema.CallToolResult.builder()
                .addTextContent(json)
                .structuredContent(structured)
                .isError(false)
                .build();
          } catch (AccessDeniedException exception) {
            return error("Portfolio access denied");
          } catch (RuntimeException exception) {
            return error("Portfolio data is unavailable");
          } catch (Exception exception) {
            return error("Portfolio data is unavailable");
          }
        });
  }

  private static McpSchema.CallToolResult error(String message) {
    return McpSchema.CallToolResult.builder().addTextContent(message).isError(true).build();
  }
}
