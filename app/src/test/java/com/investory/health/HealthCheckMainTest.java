package com.investory.health;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HealthCheckMainTest {

  @Test
  void mainPrintsHealthyMessage() throws Exception {
    PrintStream originalOut = System.out;
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    try (PrintStream capturedOut = new PrintStream(output, true, StandardCharsets.UTF_8)) {
      System.setOut(capturedOut);

      HealthCheckMain.main(new String[0]);
    } finally {
      System.setOut(originalOut);
    }

    assertEquals(
        "Investory is healthy" + System.lineSeparator(), output.toString(StandardCharsets.UTF_8));
  }
}
