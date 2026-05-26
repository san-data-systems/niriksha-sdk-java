# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.0.1] - 2026-05-27

### Added
- `dev-release.yml` workflow — auto-deploys `0.x.y-SNAPSHOT` to Maven Central on every merge to `main`
- `RELEASE.md` — comprehensive versioning, branching, Maven Central setup, and release process guide
- SLF4J API replacing `java.util.logging` — consumers provide their preferred binding
- JaCoCo code coverage reporting (60% minimum gate)
- Checkstyle with Google Java Style configuration
- SpotBugs with FindSecBugs plugin for SAST
- OWASP Dependency-Check for CVE scanning
- Maven Central release profile (GPG signing, Sonatype Central Publishing)
- `PiiRedactorTest` unit tests
- GitHub Actions CI pipeline (build, test, checkstyle, SpotBugs, JaCoCo, OWASP)
- GitHub Actions release workflow (Maven Central publish on `v*` tags)
- CodeQL security analysis workflow
- CONTRIBUTING.md, SECURITY.md, CODE_OF_CONDUCT.md

### Fixed
- ReDoS vulnerability in credit card PII regex (replaced backtracking pattern)
- Added explicit `connectTimeout` to HttpClient in PromptClient
- Spring Boot upgraded to 3.2.10 (security patches)
- TLS insecure mode now emits SLF4J WARN log

### Changed
- Logging migrated from `java.util.logging` to SLF4J API

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
