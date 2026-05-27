# Maven Central Setup Guide — niriksha-sdk-java

> This guide covers one-time setup for publishing the Java SDK to Maven Central.  
> Product: [niriksha.ai](https://niriksha.ai) · Company: [San Data Systems](https://sandatasystem.ai)  
> Maintainer: vbhadauriya@sandatasystem.com

---

## Overview

The NirikshaAI Java SDK is published to [Maven Central](https://central.sonatype.com) via:

- **Sonatype Central Account** — account + namespace verification
- **GPG Signing Key** — `releases@niriksha.ai` identity for artifact signing
- **GitHub Secrets** — credentials for automated CI/CD deployment

Setup takes ~20 minutes. Do this once per organization.

---

## 1. Create Sonatype Central Account

### 1.1 Sign up

1. Go to [central.sonatype.com/sign-up](https://central.sonatype.com/sign-up)
2. Fill in:
   - **Email:** niriksha.ai product email (e.g., releases@niriksha.ai)
   - **Password:** strong password, store in password manager
3. Verify your email address
4. Enable **Two-Factor Authentication (2FA)**:
   - Go to account settings
   - Use authenticator app (Google Authenticator, 1Password, etc.)
   - Save recovery codes securely

### 1.2 Create the account

You now have a Sonatype Central account. Next: verify the namespace.

---

## 2. Verify the `io.github.san-data-systems` Namespace

Maven Central uses **GroupId** to identify packages. For GitHub orgs, the standard is `io.github.{org-name}`.

### 2.1 Verify via GitHub org

1. Log in to [central.sonatype.com](https://central.sonatype.com)
2. Go to **Publishing → Namespaces** → [central.sonatype.com/publishing/namespaces](https://central.sonatype.com/publishing/namespaces)
3. Click **Add Namespace**
4. Enter: `io.github.san-data-systems`
5. Choose verification method: **GitHub (recommended)**
6. Follow the prompts:
   - Sonatype will verify that you control the `san-data-systems` GitHub org
   - Takes 5–10 minutes
7. Status changes to **Verified** ✅

Once verified, you can publish any artifact with GroupId `io.github.san-data-systems`.

---

## 3. Generate Deployment Credentials

### 3.1 Create a deployment token

1. Log in to [central.sonatype.com](https://central.sonatype.com)
2. Click your avatar (top-right) → **View Account**
3. Scroll to **User Token** section
4. Click **Generate User Token**
5. Two values appear:
   - **Token username** — copy this → `OSSRH_USERNAME` secret
   - **Token password** — copy this → `OSSRH_PASSWORD` secret
6. Store both securely — the password is shown only once

> These tokens are specific to the Sonatype account and are used by GitHub Actions to deploy.

---

## 4. Generate GPG Signing Key

Maven Central requires all artifacts to be signed with GPG. Use the niriksha.ai product key, not a personal key.

### 4.1 Generate locally

```bash
# Start interactive key generation
gpg --gen-key

# Answer prompts:
# Real name:    NirikshaAI Releases
# Email:        releases@niriksha.ai
# Passphrase:   <choose a strong passphrase — save it>
# (optional) Expiration: 0 (no expiration)
```

### 4.2 List keys to find KEY_ID

```bash
gpg --list-secret-keys --keyid-format LONG

# Output looks like:
# sec   rsa4096/AABBCCDD11223344 2025-01-01 [SC]
#       AAAAAAAAAAAAAAAAAABBBBBBBBBBBBBBCCCCCCCC
# uid   [ultimate] NirikshaAI Releases <releases@niriksha.ai>
# ssb   rsa4096/1111222233334444 2025-01-01 [E]

# Your KEY_ID is: AABBCCDD11223344 (the 16-char hex after the slash)
```

### 4.3 Upload public key to keyservers

Maven Central checks multiple keyservers when verifying signatures. Upload to at least two:

```bash
# Ubuntu keyserver (most common)
gpg --keyserver keyserver.ubuntu.com --send-keys AABBCCDD11223344

# Also upload to OpenPGP keyserver
gpg --keyserver keys.openpgp.org --send-keys AABBCCDD11223344

# Verify (takes ~5 minutes)
gpg --keyserver keyserver.ubuntu.com --recv-keys AABBCCDD11223344
```

### 4.4 Export private key (for GitHub secret)

```bash
# Export armored private key
gpg --armor --export-secret-keys AABBCCDD11223344 > niriksha-releases.gpg.asc

# Display the full content (copy everything including header/footer)
cat niriksha-releases.gpg.asc

# Output starts with:
# -----BEGIN PGP PRIVATE KEY BLOCK-----
# ...
# -----END PGP PRIVATE KEY BLOCK-----
```

Save the entire output (including the header and footer lines) — this is your `GPG_PRIVATE_KEY` secret.

---

## 5. Add Secrets to GitHub

Add the credentials to your repository's GitHub Actions secrets.

### 5.1 Go to secrets page

1. Navigate to [github.com/san-data-systems/niriksha-sdk-java/settings/secrets/actions](https://github.com/san-data-systems/niriksha-sdk-java/settings/secrets/actions)
2. Click **New repository secret** for each:

### 5.2 Add each secret

| Secret name | Value | Source |
|-------------|-------|--------|
| `OSSRH_USERNAME` | Token username (step 3.1) | Sonatype token |
| `OSSRH_PASSWORD` | Token password (step 3.1) | Sonatype token |
| `GPG_PRIVATE_KEY` | Full armored key from step 4.4 | Local GPG export |
| `GPG_PASSPHRASE` | GPG key passphrase (step 4.1) | Your chosen passphrase |

**Important**: When pasting `GPG_PRIVATE_KEY`, include the full `-----BEGIN-----` and `-----END-----` lines.

`GITHUB_TOKEN` is auto-provided by GitHub Actions — do not add it.

---

## 6. Verify Setup

### 6.1 Create a test release

1. Create a test tag:
   ```bash
   git checkout main && git pull
   git tag -a v0.0.2-test -m "Test release"
   git push origin v0.0.2-test
   ```

2. Watch the `release.yml` workflow:
   - Go to [Actions](https://github.com/san-data-systems/niriksha-sdk-java/actions)
   - Click **Release** workflow → latest run
   - Verify all steps pass:
     - "Bump version and create tag"
     - "Deploy to Maven Central" ← should NOT have errors
     - "Create GitHub Release"

3. If deploy succeeds, check Maven Central:
   - Go to [central.sonatype.com/search?q=io.github.san-data-systems](https://central.sonatype.com/search?q=io.github.san-data-systems)
   - You should see `niriksha-sdk-java:0.0.2-test` appear within ~15 minutes

### 6.2 Use in a project

After artifacts appear in Maven Central:

```xml
<dependency>
  <groupId>io.github.san-data-systems</groupId>
  <artifactId>niriksha-sdk-java</artifactId>
  <version>0.0.2-test</version>
</dependency>
```

Or with Gradle:

```groovy
implementation 'io.github.san-data-systems:niriksha-sdk-java:0.0.2-test'
```

---

## Troubleshooting

### Deploy fails with "403 Forbidden"

- **Cause:** `OSSRH_USERNAME` or `OSSRH_PASSWORD` is incorrect
- **Fix:** Regenerate token in Sonatype account (step 3.1) and update both secrets

### Deploy fails with "gpg: signing failed"

- **Cause:** `GPG_PRIVATE_KEY` or `GPG_PASSPHRASE` is invalid
- **Fix:**
  1. Verify the secret contains the full key (with header/footer)
  2. Regenerate if needed: `gpg --armor --export-secret-keys {KEY_ID} > key.asc`
  3. Update both `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE` secrets
  4. Delete and re-create the key if problems persist

### Artifact doesn't appear in Maven Central

- **Cause:** Deploy succeeded but sync is slow or artifact failed validation
- **Fix:**
  1. Check GitHub Release was created (indicates workflow completed)
  2. Check Maven Central: [central.sonatype.com/search](https://central.sonatype.com/search)
  3. Wait 30+ minutes — sync is async
  4. Check the `release.yml` workflow logs for validation errors (e.g., missing javadoc)

### "Namespace not verified" error

- **Cause:** `io.github.san-data-systems` namespace verification didn't complete
- **Fix:**
  1. Go to [central.sonatype.com/publishing/namespaces](https://central.sonatype.com/publishing/namespaces)
  2. Check status — should be **Verified** (green checkmark)
  3. If pending, verify GitHub org control and wait 5–10 minutes

---

## What Gets Published

When `release.yml` runs:

```
mvn deploy -P release
```

This publishes:
- **JAR** — compiled SDK
- **Sources JAR** — source code (required by Maven Central)
- **Javadoc JAR** — API documentation (required by Maven Central)
- **POM** — project metadata
- **Signatures** — `.asc` files (GPG-signed by the key from step 4)
- **Checksums** — `.md5` and `.sha1` files

All are signed and verified by Maven Central before release.

---

## References

- [Maven Central Publishing Portal](https://central.sonatype.com)
- [Sonatype Central Documentation](https://central.sonatype.com/help/publish)
- [GPG Key Generation](https://gnupg.org/gph/de/manual/r899.html)
- [Maven Deploy Plugin](https://maven.apache.org/plugins/maven-deploy-plugin/)
- [GitHub Actions Secrets](https://docs.github.com/en/actions/security-guides/encrypted-secrets)

---

## Checklist

- [ ] Sonatype Central account created
- [ ] `io.github.san-data-systems` namespace verified
- [ ] Deployment token generated
- [ ] GPG key generated and uploaded to keyservers
- [ ] `OSSRH_USERNAME` secret added to GitHub
- [ ] `OSSRH_PASSWORD` secret added to GitHub
- [ ] `GPG_PRIVATE_KEY` secret added to GitHub (full key with header/footer)
- [ ] `GPG_PASSPHRASE` secret added to GitHub
- [ ] Test release triggered and published to Maven Central
- [ ] Artifact visible in [Maven Central search](https://central.sonatype.com/search)
