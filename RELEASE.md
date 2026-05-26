# Release Guide — niriksha-sdk-java

> Product: [niriksha.ai](https://niriksha.ai) · Company: [sandatasystem.ai](https://sandatasystem.ai)  
> Maintainer: vbhadauriya@redcloudcomputing.com

---

## Versioning Scheme

This SDK follows [Semantic Versioning 2.0.0](https://semver.org):

```
MAJOR . MINOR . PATCH
│       │       └── Bug fixes, security patches (backwards compatible)
│       └────────── New features (backwards compatible)
└────────────────── Breaking API changes
```

### Version Lifecycle

| Version Pattern | Meaning | Repository |
|----------------|---------|-----------|
| `0.1.0-SNAPSHOT` | Auto dev build (every merge to `main`) | Maven Central Snapshots |
| `0.1.1-alpha.1` | Alpha — early feature preview | Maven Central (pre-release) |
| `0.1.1-beta.1` | Beta — feature complete, needs testing | Maven Central (pre-release) |
| `0.1.1-rc.1` | Release candidate — final testing | Maven Central (pre-release) |
| `0.1.1` | Stable release | Maven Central (release) |
| `1.0.0` | First stable API contract | Maven Central (release) |

> **Why not v0.0.0?** We start at `0.1.0`. `0.0.0` is a placeholder meaning "not yet versioned". `0.x.y` means the public API may still evolve; `1.0.0` signals a stable, committed public API.

### Add to your project

**Stable release:**
```xml
<dependency>
  <groupId>io.github.san-data-systems</groupId>
  <artifactId>niriksha-sdk-java</artifactId>
  <version>0.1.0</version>
</dependency>
```

**SNAPSHOT (dev build):**
```xml
<repositories>
  <repository>
    <id>central-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <snapshots><enabled>true</enabled></snapshots>
  </repository>
</repositories>

<dependency>
  <groupId>io.github.san-data-systems</groupId>
  <artifactId>niriksha-sdk-java</artifactId>
  <version>0.1.1-SNAPSHOT</version>
</dependency>
```

**Gradle (stable):**
```kotlin
implementation("io.github.san-data-systems:niriksha-sdk-java:0.1.0")
```

**Gradle (SNAPSHOT):**
```kotlin
repositories {
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        mavenContent { snapshotsOnly() }
    }
}
implementation("io.github.san-data-systems:niriksha-sdk-java:0.1.1-SNAPSHOT")
```

---

## Branching Strategy

```
main                  ← Protected. Every merge auto-deploys a SNAPSHOT.
│
├── feature/xxx       ← New features. PR → main.
├── fix/xxx           ← Bug fixes. PR → main.
├── hotfix/xxx        ← Urgent production patches. PR → main.
├── enhance/xxx       ← Improvements (docs, CI, deps). PR → main.
└── release/x.y.z     ← Release preparation. PR → main, then tag.
```

### Branch rules (GitHub → Settings → Branches)

| Branch | Protection |
|--------|-----------|
| `main` | Require PR, require CI to pass, no force-push |

---

## Release Types

### 1. Patch Release (0.1.0 → 0.1.1)
**When:** Bug fix, security patch. No new public API.

```bash
# 1. Branch from main
git checkout main && git pull
git checkout -b release/0.1.1

# 2. Bump version in pom.xml
mvn versions:set -DnewVersion=0.1.1 --batch-mode
mvn versions:commit --batch-mode

# 3. Update CHANGELOG.md
#    Move [Unreleased] entries to [0.1.1] with today's date

# 4. Verify build passes
mvn verify --batch-mode --no-transfer-progress

# 5. Commit and PR
git add pom.xml CHANGELOG.md
git commit -m "chore: release 0.1.1"
git push -u origin release/0.1.1
gh pr create --base main --title "chore: release 0.1.1"

# 6. After PR merged, tag
git checkout main && git pull
git tag -a v0.1.1 -m "Release v0.1.1"
git push origin v0.1.1
# → release.yml deploys to Maven Central automatically
```

### 2. Minor Release (0.1.0 → 0.2.0)
**When:** New backwards-compatible features.

```bash
mvn versions:set -DnewVersion=0.2.0 --batch-mode
mvn versions:commit --batch-mode
# Then PR + tag v0.2.0
```

### 3. Major Release (0.x.y → 1.0.0)
**When:** Breaking API changes.

```bash
mvn versions:set -DnewVersion=1.0.0 --batch-mode
mvn versions:commit --batch-mode
# Then PR + tag v1.0.0
# Update package path if needed: ai.niriksha.sdk → ai.niriksha.sdk.v2
```

### 4. Pre-release (alpha / beta / RC)

```bash
# Alpha
mvn versions:set -DnewVersion=0.2.0-alpha.1 --batch-mode && mvn versions:commit --batch-mode
git tag -a v0.2.0-alpha.1 -m "Alpha 1 for 0.2.0"
git push origin v0.2.0-alpha.1

# Beta
mvn versions:set -DnewVersion=0.2.0-beta.1 --batch-mode && mvn versions:commit --batch-mode
git tag -a v0.2.0-beta.1 -m "Beta 1 for 0.2.0"
git push origin v0.2.0-beta.1

# Release Candidate
mvn versions:set -DnewVersion=0.2.0-rc.1 --batch-mode && mvn versions:commit --batch-mode
git tag -a v0.2.0-rc.1 -m "RC 1 for 0.2.0"
git push origin v0.2.0-rc.1
```

All pre-release tags trigger `release.yml`, which deploys to Maven Central.

### 5. Dev Build / SNAPSHOT (automatic)
**When:** Every merge to `main` — no manual action required.

The `dev-release.yml` workflow automatically:
1. Sets version to `{current}-SNAPSHOT` (e.g. `0.1.0-SNAPSHOT`)
2. Deploys to Maven Central Snapshots repository
3. Creates a GitHub pre-release

---

## Required Secrets & Setup (One-time)

> **Important:** All credentials below must be registered and generated under the **niriksha.ai product account** (`io.github.san-data-systems` namespace on Sonatype). Do not use a personal developer account. This keeps niriksha publish credentials separate from other San Data Systems products.

### Sonatype Central — Maven Central Publishing

| Step | Action | URL |
|------|--------|-----|
| 1 | Sign in with GitHub (use `san-data-systems` org account) | [central.sonatype.com/sign-up](https://central.sonatype.com/sign-up) |
| 2 | Verify `io.github.san-data-systems` namespace via GitHub org — no DNS needed | [central.sonatype.com/publishing/namespaces](https://central.sonatype.com/publishing/namespaces) |
| 3 | Generate a deployment token | [central.sonatype.com/account](https://central.sonatype.com/account) → Profile → Generate User Token |
| 4 | Add `OSSRH_USERNAME` + `OSSRH_PASSWORD` secrets | [github.com/san-data-systems/niriksha-sdk-java/settings/secrets/actions](https://github.com/san-data-systems/niriksha-sdk-java/settings/secrets/actions) |

### GPG Signing Key — niriksha.ai product key

| Step | Action | Command |
|------|--------|---------|
| 1 | Generate a GPG key for niriksha.ai (use `releases@niriksha.ai` as the email) | `gpg --gen-key` |
| 2 | List keys to get your `KEY_ID` | `gpg --list-secret-keys --keyid-format LONG` |
| 3 | Upload public key to keyserver | `gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID` |
| 4 | Export private key (armored) | `gpg --armor --export-secret-keys YOUR_KEY_ID` |
| 5 | Add `GPG_PRIVATE_KEY` secret (paste full `--BEGIN PGP...` output) | [github.com/san-data-systems/niriksha-sdk-java/settings/secrets/actions](https://github.com/san-data-systems/niriksha-sdk-java/settings/secrets/actions) |
| 6 | Add `GPG_PASSPHRASE` secret | Same page |

> Use `releases@niriksha.ai` (not a personal email) as the GPG key identity. This ties the signing key to the product, not an individual.

| Step | Action | URL |
|------|--------|-----|

### Secrets Summary

| Secret | Value source |
|--------|-------------|
| `OSSRH_USERNAME` | Sonatype Central token username (niriksha.ai account) |
| `OSSRH_PASSWORD` | Sonatype Central token password (niriksha.ai account) |
| `GPG_PRIVATE_KEY` | Armored private key for `releases@niriksha.ai` GPG key |
| `GPG_PASSPHRASE` | Passphrase for the GPG key above |
| `GITHUB_TOKEN` | Auto-provided by GitHub Actions |

### SLF4J Binding Note
This SDK declares `slf4j-api` as a compile dependency. Consumers must add their preferred binding:

```xml
<!-- Logback (recommended) -->
<dependency>
  <groupId>ch.qos.logback</groupId>
  <artifactId>logback-classic</artifactId>
  <version>1.5.6</version>
</dependency>

<!-- or Log4j2 -->
<dependency>
  <groupId>org.apache.logging.log4j</groupId>
  <artifactId>log4j-slf4j2-impl</artifactId>
  <version>2.23.1</version>
</dependency>
```

---

## Release Checklist

- [ ] All CI checks green on `main`
- [ ] `mvn verify` passes (tests + Checkstyle + SpotBugs + JaCoCo ≥60%)
- [ ] CHANGELOG.md updated — `[Unreleased]` moved to `[x.y.z]` with date
- [ ] Version bumped in `pom.xml` via `mvn versions:set`
- [ ] PR merged to `main`
- [ ] Tag pushed: `git tag -a vX.Y.Z -m "Release vX.Y.Z" && git push origin vX.Y.Z`
- [ ] Maven Central deployment confirmed (check [central.sonatype.com](https://central.sonatype.com))
- [ ] GitHub Release created (auto by `release.yml`)

---

## Hotfix Process

```bash
git checkout main && git pull
git checkout -b hotfix/fix-description

# Fix + test
mvn test

# Bump patch version
mvn versions:set -DnewVersion=0.1.1 --batch-mode
mvn versions:commit --batch-mode

git add pom.xml
git commit -m "fix: critical bug description"
git push -u origin hotfix/fix-description
gh pr create --base main --title "hotfix: critical bug"

# After merge
git checkout main && git pull
git tag -a v0.1.1 -m "Hotfix: critical bug"
git push origin v0.1.1
```

---

## CHANGELOG Management

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)

```markdown
## [Unreleased]          ← new changes go here first
## [0.1.1] - 2025-06-01 ← moved here when releasing
## [0.1.0] - 2025-05-01
```

Every PR must include a CHANGELOG entry under `[Unreleased]`.
