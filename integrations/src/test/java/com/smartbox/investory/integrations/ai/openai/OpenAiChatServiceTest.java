package com.smartbox.investory.integrations.ai.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayName("Open Ai Chat Service")
class OpenAiChatServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @DisplayName("extract Output Text combines Text Parts")
  @Test
  void extractOutputText_combinesTextParts() throws Exception {
    JsonNode response =
        objectMapper.readTree(
            """
                {
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {"type": "output_text", "text": "First"},
                        {"type": "refusal", "refusal": "ignored"},
                        {"type": "output_text", "text": "Second"}
                      ]
                    }
                  ]
                }
                """);

    assertEquals("First\nSecond", OpenAiChatService.extractOutputText(response));
  }

  @DisplayName("build Input adds Portfolio Context When Available")
  @Test
  void buildInput_addsPortfolioContextWhenAvailable() {
    String input = OpenAiChatService.buildInput("What is my balance?", "Balance $100; Cash $10");

    org.junit.jupiter.api.Assertions.assertTrue(input.contains("What is my balance?"));
    org.junit.jupiter.api.Assertions.assertTrue(input.contains("Balance $100; Cash $10"));
    org.junit.jupiter.api.Assertions.assertTrue(input.contains("source of truth"));
  }

  @DisplayName("build Input returns Original Message Without Context")
  @Test
  void buildInput_returnsOriginalMessageWithoutContext() {
    assertEquals("Explain an ETF", OpenAiChatService.buildInput("Explain an ETF", ""));
  }
}
