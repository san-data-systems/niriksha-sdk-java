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

## Branching Model

This project uses a **`main` + `develop` two-branch model** with automatic versioning:

```
main (protected)           ← Production releases only
  ↑
  │ (develop → main PR)
  │
develop                    ← Feature integration
  ↑
  ├── feature/xxx          ← New features
  ├── fix/xxx              ← Bug fixes
  └── enhance/xxx          ← Improvements (docs, CI, deps)
```

### Workflow

1. **Create a feature branch from `develop`** (not `main`):
   ```bash
   git checkout develop && git pull
   git checkout -b feature/my-feature
   ```

2. **Commit using conventional commits**:
   - `feat: add new OTLP exporter option` → minor bump
   - `fix: handle null pointer in PiiRedactor` → patch bump
   - `chore: update dependencies` → patch bump
   - `feat!: change API signature` or `BREAKING CHANGE: ...` → major bump

3. **Push and PR to `develop`** (not `main`):
   ```bash
   git push -u origin feature/my-feature
   gh pr create --base develop --title "feat: my feature"
   ```

4. **CI must pass** — all checks on the `develop` PR:
   - Build, test, checkstyle, SpotBugs, JaCoCo, OWASP

5. **After merge to `develop`**:
   - `dev-release.yml` auto-triggers
   - Creates GitHub pre-release tagged `vX.Y.Z-dev.SHA` (NOT published to Maven Central)
   - JAR available in release assets for testing

6. **Release to production**:
   - When ready, create a PR from `develop` → `main`
   - Branch gate enforces: only `develop` can merge to `main`
   - CI passes
   - Merge the PR
   - `release.yml` auto-triggers:
     - Bumps version based on commit messages (mathieudutour/github-tag-action)
     - Commits version bump with `[skip ci]`
     - Deploys to Maven Central with GPG signature
     - Creates GitHub release

### Rules

- **Always branch from `develop`**, never from `main`
- **PRs to `develop`** for all feature work
- **Only `develop` → `main`** — branch gate enforces this
- Keep branches short-lived (< 1 week ideal)
- Delete branch after merge

## Pull Request Process

1. Branch from `develop` (not `main`)
2. Write tests first — target 80%+ coverage
3. Run `mvn verify` locally — all gates must pass:
   - `mvn compile` compiles without errors
   - `mvn test` passes all unit tests
   - `mvn checkstyle:check` passes code style
   - `mvn spotbugs:check` passes security scan
   - JaCoCo coverage ≥ 50% (measured by `mvn verify`)
4. Update `CHANGELOG.md` under `[Unreleased]` with your changes
5. Commit with conventional commit message (`feat:`, `fix:`, `chore:`)
6. Push to your branch and open PR **against `develop`** (not `main`)
7. All CI checks must pass:
   - GitHub Actions `ci.yml` workflow
   - OWASP Dependency Check (CVSS ≥ 7 blocks merge)
8. Request review and merge

**Important:** Do not create PRs to `main` — the branch gate will reject them. All feature work merges to `develop` first.

## Reporting Issues

Security vulnerabilities: see [SECURITY.md](SECURITY.md)
