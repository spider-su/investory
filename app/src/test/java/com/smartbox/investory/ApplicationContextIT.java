package com.smartbox.investory;

import com.smartbox.investory.testsupport.FastDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test-fast")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ApplicationContextIT extends FastDatabaseTest {

  @Test
  void applicationContextLoads() {}
}
