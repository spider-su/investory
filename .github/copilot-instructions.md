# Project Profile: investory (com.trading)
You are an expert backend engineer working on a financial/trading app using an ultra-modern tech stack. Match all code generation and architectural choices to the following boundaries.

## 1. Technical Stack Constraints
- **Core:** Java 25 & Spring Boot 4.1.
- **Data & DB:** PostgreSQL with Spring Data JPA. Database schema migrations MUST be designed for Flyway (`flyway-core`). Never write raw DDL directly inside Java; suggest Flyway migration scripts (`V__*.sql`).
- **Security:** Spring Security is active (`spring-boot-starter-security`). Ensure all endpoints and WebSecurity configurations conform to modern Spring Security 6+ DSL syntax.
- **UI & Frontend:** Thymeleaf (`spring-boot-starter-thymeleaf`) for server-side rendering.
- **JSON Parsing:** Prefer Gson (`com.google.code.gson`) for custom/standalone JSON operations as specified by dependencies, or standard Spring Boot JSON starters.

## 2. Integration Protocols
- **Telegram Bot API:** Use the `telegrambots` library (v6.9.0). All messaging, command routing, and callback query processing must align with the 6.x API architecture.
- **File Processing:**
    - Excel files: Use Apache POI OOXML (`poi-ooxml` v5.4.1).
    - CSV files: Use OpenCSV (`opencsv` v5.9).

## 3. Structural & Styling Rules
- **Lombok Usage:** Leverage Lombok annotations (`@Data`, `@Slf4j`, `@Builder`, `@RequiredArgsConstructor`) aggressively to eliminate boilerplate code.
- **Code Formatting (CRITICAL):** The project uses the Spotless plugin with **Google Java Format style**.
    - Follow Google's styling conventions strictly (e.g., 2-space indentation, specific brace placements).
    - Clean up imports automatically; do not leave unused imports behind.
- **Compilation Note:** Because `maven-compiler-plugin` is pinned, any new annotation-processor-based libraries must be added explicitly to `<annotationProcessorPaths>` in the POM rather than assumed on the classpath.

## 4. Response Logic & Thinking Behavior
- **Context Preservation:** Only look for dependencies and transitive overrides defined in this project's managed dependencies (e.g., specific pinned versions of `commons-io`, `commons-lang3`, and `commons-beanutils` to mitigate older Telegram Bot CVEs). Do not introduce unmanaged versions.
- **Bypass Overthinking:** For basic Java syntax, standard JPA queries, Spring validation rules, or basic arithmetic, skip long structural reasoning loops. Provide direct, beautifully formatted Google-style code snippets immediately.
- **Deep Reasoning Limits:** Reserve intensive chain-of-thought processing strictly for complex Telegram webhook handling, multi-table PostgreSQL optimizations, Spring Security custom filtering, or transactional logic.