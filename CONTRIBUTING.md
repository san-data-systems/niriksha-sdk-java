# Contributing to niriksha-sdk-java

Thank you for helping improve the NirikshaAI Java SDK!  
Product: [niriksha.ai](https://niriksha.ai) · Company: [sandatasystem.ai](https://sandatasystem.ai)

## Development Setup

```bash
git clone https://github.com/san-data-systems/niriksha-sdk-java
cd niriksha-sdk-java
mvn verify   # compiles, tests, checkstyle, SpotBugs, JaCoCo
```

**Requirements:** Java 17+, Maven 3.9+

## Available Commands

| Command | Description |
|---------|-------------|
| `mvn compile` | Compile source |
| `mvn test` | Run unit tests |
| `mvn verify` | Full build + quality gates |
| `mvn checkstyle:check` | Run Checkstyle only |
| `mvn spotbugs:check` | Run SpotBugs only |
| `mvn spotbugs:gui` | Open SpotBugs GUI report |
| `mvn jacoco:report` | Generate coverage HTML report |
| `mvn site` | Full site with all reports |

## Code Style

This project follows [Google Java Style](https://google.github.io/styleguide/javaguide.html), enforced by Checkstyle. Run `mvn checkstyle:check` before submitting a PR.

Key rules:
- 2-space indentation
- 100 character line limit
- Javadoc required on all public methods
- No wildcard imports

## Logging

Use SLF4J API only (`org.slf4j.Logger`). Do **not** use `java.util.logging`, Log4j, or Logback directly — consumers choose their binding.

```java
private static final Logger log = LoggerFactory.getLogger(MyClass.class);
log.debug("processing request id={}", requestId);
```

## Branch Naming

- `feat/<description>` — new features
- `fix/<description>` — bug fixes
- `chore/<description>` — maintenance
- `docs/<description>` — documentation only

## Pull Request Process

1. Branch from `main`
2. Write tests first — target 80%+ coverage
3. Run `mvn verify` locally — all gates must pass
4. Update `CHANGELOG.md` under `[Unreleased]`
5. Open PR — all CI checks must pass

## Reporting Issues

Security vulnerabilities: see [SECURITY.md](SECURITY.md)
