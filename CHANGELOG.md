# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.0.1] - 2026-05-27

### Added
- **Branching model**: `main` + `develop` two-branch strategy with auto-versioning
- **CI/CD workflows**:
  - `ci.yml` — build, test, checkstyle, SpotBugs, JaCoCo, OWASP on all PRs
  - `branch-gate.yml` — enforces `develop` → `main` only (no other branches)
  - `dev-release.yml` — auto-publishes dev builds to GitHub pre-releases (tagged `vX.Y.Z-dev.SHA`)
  - `release.yml` — auto-bumps version via conventional commits, GPG-signs artifacts, publishes to Maven Central
- **Auto-versioning** via `mathieudutour/github-tag-action@v6.2`:
  - `feat:` commits → minor version bump
  - `fix:` / `chore:` commits → patch version bump
  - `BREAKING CHANGE:` in footer → major version bump
- **Vulnerability gate** — OWASP Dependency Check blocks builds on CVSS ≥ 7
- **Code quality**:
  - JaCoCo coverage reporting (50% gate, OTel/gRPC init excluded)
  - Checkstyle with Google Java Style (2-space, 100 char limit)
  - SpotBugs with FindSecBugs plugin for SAST
  - SpotBugs REDOS exclusion for PiiRedactor (possessive quantifiers not recognized by heuristic)
- **Maven Central release profile** — GPG signing, Sonatype Central Publishing
- **SCM and developers metadata** in pom.xml (required for Maven Central)
- SLF4J API (consumers provide binding: Logback, Log4j2, slf4j-simple)
- Spring Boot 3.2+ optional autoconfiguration
- `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`
- `RELEASE.md` — comprehensive dev/prod branching, versioning, and release process
- `REGISTRY_SETUP.md` — Maven Central credential setup guide
- GitHub pre-release and Javadoc badges in README

### Fixed
- ReDoS vulnerability in credit card PII regex (replaced backtracking pattern)
- Added explicit `connectTimeout` to HttpClient in PromptClient
- Spring Boot upgraded to 3.2.10 (security patches)
- TLS insecure mode now emits SLF4J WARN log

### Changed
- Logging migrated from `java.util.logging` to SLF4J API
- **IMPORTANT**: Dev builds no longer attempt SNAPSHOT publish to Maven Central (new portal rejects SNAPSHOT). Dev builds now create GitHub pre-releases only, tagged `vX.Y.Z-dev.SHA`. Production releases to Maven Central via `main` branch trigger.

## [0.1.0] - 2025-05-01

### Added
- Initial release
- OpenTelemetry traces, metrics, and logs via OTLP/gRPC
- Spring Boot 3 auto-configuration
- LLM span helpers: conversation, RAG chunks, tool calls
- PII redaction utilities
- W3C Baggage context propagation
- Serverless flush wrapper
- Eval submission with retry logic
- Prompt vault client with in-memory cache

[Unreleased]: https://github.com/san-data-systems/niriksha-sdk-java/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/san-data-systems/niriksha-sdk-java/releases/tag/v0.1.0
