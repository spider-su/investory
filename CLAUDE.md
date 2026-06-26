# Build & Test Imperatives
- Never run builds using wrappers; always call `mvn` directly.
- To check formatting, use `mvn spotless:check`. Do not bind it to a build phase.
- To fix formatting errors automatically, run `mvn spotless:apply`.
- Every controller test slice uses `@WebMvcTest`. You MUST explicitly import `@Import({SecurityConfig.class, MockMvcSecurityTestConfig.class})` to prevent 401 errors.
- Pinned security transitives: `commons-io` (2.21.0), `commons-lang3` (3.20.0). Do not downgrade.