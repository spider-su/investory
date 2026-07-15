package com.example.demo.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AssetPriceHistoryGeneratorPythonTest {

  @Test
  void pythonGeneratorUnitTestsPass() throws IOException, InterruptedException {
    Process process =
        new ProcessBuilder(
                "python3", "-m", "unittest", "src/test/python/test_asset_price_history_generator.py")
            .redirectErrorStream(true)
            .start();

    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exitCode = process.waitFor();

    assertEquals(0, exitCode, output);
  }
}
