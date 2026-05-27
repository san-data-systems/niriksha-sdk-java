# Release Guide — niriksha-sdk-java

> Product: [niriksha.ai](https://niriksha.ai) · Company: [San Data Systems](https://sandatasystem.ai)  
> Maintainer: vbhadauriya@sandatasystem.com

---

## Overview

This SDK uses a **two-branch model** with **automatic semantic versioning** and **split release channels**:

- **`develop` branch** → Feature integration, auto dev builds
- **`main` branch** → Production releases, Maven Central publish

### Release Channels

| Channel | Branch | Trigger | Artifact | Maven Central |
|---------|--------|---------|----------|--------------|
| **Dev Build** | `develop` | Any merge | JAR + sources | No (GitHub pre-release) |
| **Production** | `main` | Only via `develop` PR | JAR + sources + javadoc | Yes (semver auto-bumped) |

---

## Versioning

This SDK follows [Semantic Versioning 2.0.0](https://semver.org):

```
MAJOR . MINOR . PATCH
│       │       └── Bug fixes, patches (backwards compatible)
│       └────────── New features (backwards compatible)
└────────────────── Breaking API changes
```

### Version Scheme

| Pattern | Example | Use case |
|---------|---------|----------|
| Stable release | `0.1.0` | Production, Maven Central |
| Pre-release | `0.1.0-alpha.1`, `0.1.0-rc.1` | Early testing, Maven Central |
| Dev build | `0.0.1-dev.a1b2c3d` | Development only, GitHub pre-release |

### Conventional Commits (Auto-versioning)

The `release.yml` workflow uses [mathieudutour/github-tag-action](https://github.com/mathieudutour/github-tag-action) to automatically bump versions based on commit messages:

| Commit type | Example | Version bump |
|------------|---------|--------------|
| `feat:` | `feat: add sampler configuration` | `0.1.0` → `0.2.0` (minor) |
| `fix:` | `fix: null pointer in logger` | `0.1.0` → `0.1.1` (patch) |
| `chore:` | `chore: update OTel to 1.39` | `0.1.0` → `0.1.1` (patch) |
| `BREAKING CHANGE:` in footer | `feat: change API\n\nBREAKING CHANGE: ...` | `0.1.0` → `1.0.0` (major) |

---

## Dev Builds (Automatic)

### How it works

Every merge to `develop` triggers `dev-release.yml`:

1. Extracts base version from `pom.xml` (e.g., `0.0.1`)
2. Computes dev version: `0.0.1-dev.{7-char SHA}` (e.g., `0.0.1-dev.a1b2c3d`)
3. Runs `mvn verify` with new dev version
4. Creates GitHub **pre-release** tagged `v0.0.1-dev.a1b2c3d`
5. Attaches JAR + sources to release

### Using dev builds

Dev builds are published to GitHub Releases (pre-releases) only. You can:

**Option 1: Use GitHub releases page**
- Go to [releases](https://github.com/san-data-systems/niriksha-sdk-java/releases)
- Download the JAR from the pre-release assets
- Add to your local repository or classpath

**Option 2: Build from source (alternative)**
- Clone and checkout the commit SHA
- Run `mvn install -DskipTests`

### Important

- Dev builds are **NOT published to Maven Central** (new portal rejects SNAPSHOT versions)
- Dev builds are **pre-releases** — marked as unstable
- Use only for **testing and development**, never for production
- Dev builds are **overwritten** — don't rely on them being archived

---

## Production Releases (Automatic)

### How it works

When you merge `develop` → `main`:

1. `ci.yml` branch gate enforces: **only `develop` can merge to `main`**
2. `release.yml` workflow triggers:
   - Fetches all commits since last tag
   - Analyzes commit messages (conventional commits)
   - Computes new semver (major/minor/patch)
   - Creates annotated Git tag (e.g., `v0.1.0`)
   - Updates `pom.xml` with new version
   - Commits version bump with `[skip ci]` (skips re-triggering CI)
   - Runs `mvn deploy -P release` with GPG signing
   - Publishes to Maven Central
   - Creates GitHub Release

### Example flow

```
develop (commit: "feat: add new OTLP exporter option")
│
├─── mvn verify passes
│
├─── PR created: develop → main
│
├─── Branch gate passes (develop is allowed)
│
├─── Merge to main
│
└─── release.yml triggers:
     1. Analyze commits → "feat:" → bump minor
     2. Tag: v0.1.1
     3. Update pom.xml version
     4. Commit & push
     5. Sign & deploy to Maven Central
     6. Create GitHub Release
```

### Maven Central

After deployment:
- Artifacts appear in [Maven Central](https://central.sonatype.com) within ~15 minutes
- Available in all build tools:
  ```xml
  <dependency>
    <groupId>io.github.san-data-systems</groupId>
    <artifactId>niriksha-sdk-java</artifactId>
    <version>0.1.1</version>
  </dependency>
  ```

---

## Pre-releases (Manual)

For alpha/beta/RC versions, create tags manually:

```bash
# Create a local tag
git tag -a v0.1.0-beta.1 -m "Beta 1"

# Push to GitHub
git push origin v0.1.0-beta.1

# release.yml detects the v* tag and auto-deploys
```

The tag must match `v*` pattern for `release.yml` to trigger.

---

## Setup Requirements (One-time)

### 1. Branch protection

Protect `main` branch:
1. Go to GitHub repo → Settings → Branches
2. Add rule for `main`:
   - Require PR review (optional)
   - Require status checks to pass: `build-and-test`, `owasp`, `branch-gate`
   - Dismiss stale reviews
   - No force push

### 2. GitHub Secrets

Add to [Settings → Secrets → Actions](https://github.com/san-data-systems/niriksha-sdk-java/settings/secrets/actions):

| Secret | Value | Source |
|--------|-------|--------|
| `OSSRH_USERNAME` | Sonatype token username | See [REGISTRY_SETUP.md](REGISTRY_SETUP.md#33-generate-a-deployment-token) |
| `OSSRH_PASSWORD` | Sonatype token password | See [REGISTRY_SETUP.md](REGISTRY_SETUP.md#33-generate-a-deployment-token) |
| `GPG_PRIVATE_KEY` | Armored GPG private key | See [REGISTRY_SETUP.md](REGISTRY_SETUP.md#34-generate-a-gpg-signing-key) |
| `GPG_PASSPHRASE` | GPG key passphrase | See [REGISTRY_SETUP.md](REGISTRY_SETUP.md#34-generate-a-gpg-signing-key) |

`GITHUB_TOKEN` is auto-provided by GitHub Actions.

### 3. Release environment (optional)

Create a GitHub environment for production releases:
1. Go to Settings → Environments
2. Create environment: `release`
3. Optionally add required reviewers (e.g., `@V-Bhadauriya`)
4. This gates `release.yml` deployment step

---

## Troubleshooting

### Dev build doesn't trigger

Check `dev-release.yml`:
- Verify branch is `develop`
- Verify commit was actually merged (not squashed into wrong branch)
- Check Actions tab → `Dev Release` workflow run

### Release doesn't trigger

Check `release.yml`:
- Verify PR was merged from `develop` to `main` (not force-pushed)
- Verify branch gate passed
- Check Actions tab → `Release` workflow run
- Look for Git tag in [releases](https://github.com/san-data-systems/niriksha-sdk-java/releases)

### Maven Central sync delay

Maven Central syncs every 5–15 minutes. Check:
1. Is the GitHub Release created? (indicates workflow ran)
2. Check Maven Central: [search `io.github.san-data-systems`](https://central.sonatype.com/search?q=io.github.san-data-systems)
3. If missing after 30 minutes, check the `release.yml` run logs for deploy errors

### Version bump unexpected

Review the commit history since last tag:

```bash
git log --oneline v0.1.0..HEAD
```

Each commit's message determines the bump:
- `feat:` → minor
- `fix:` / `chore:` → patch
- `BREAKING CHANGE:` in footer → major

If bump is wrong, the commit message format is likely non-standard.

### GPG signing fails

Check `release.yml` logs under the "Deploy to Maven Central" step:

```
error: gpg: signing failed
```

Solutions:
1. Verify `GPG_PRIVATE_KEY` secret is valid (must include header/footer lines)
2. Verify `GPG_PASSPHRASE` is correct
3. Regenerate keys and update secrets (see [REGISTRY_SETUP.md](REGISTRY_SETUP.md#34-generate-a-gpg-signing-key))

---

## References

- [Semantic Versioning](https://semver.org)
- [Conventional Commits](https://www.conventionalcommits.org)
- [mathieudutour/github-tag-action](https://github.com/mathieudutour/github-tag-action#outputs)
- [Maven Central Publishing](https://central.sonatype.com)
- [Contributing Guidelines](CONTRIBUTING.md)
- [Registry Setup (Credentials)](REGISTRY_SETUP.md)
