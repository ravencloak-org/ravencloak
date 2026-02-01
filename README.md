# KOS Auth Backend

[![status-badge](https://drone.keeplearningos.com/api/badges/1/status.svg?events=push%2Ctag%2Crelease%2Cpull_request%2Cpull_request_closed%2Cpull_request_metadata%2Cdeployment)](https://drone.keeplearningos.com/repos/1)
![Auth](https://img.shields.io/badge/auth-v0.0.0-blue)
![Keycloak SPI](https://img.shields.io/badge/keycloak--spi-spi--v0.0.0-green)

A multi-tenant authentication backend with Spring Boot/Kotlin and a Keycloak User Storage SPI for federated user validation via REST API.

## Setup

### Prerequisites
- Java 21
- PostgreSQL database
- Gradle (included via wrapper)

### Environment Configuration

1. Copy the sample environment file:
   ```bash
   cp .env.sample .env
   ```

2. Update the `.env` file with your database configuration:
   ```env
   DB_HOST=localhost
   DB_PORT=5433
   DB_NAME=kos-auth
   DB_USERNAME=postgres
   DB_PASSWORD=your_password_here
   ```

### Database Setup

Make sure you have a PostgreSQL database running on the configured host and port. The application will use Flyway to automatically run database migrations.

### Running the Application

1. Build the project:
   ```bash
   ./gradlew build
   ```

2. Run the application:
   ```bash
   ./gradlew bootRun
   ```

The application will start on the default Spring Boot port (8080).

## Features

- R2DBC reactive database connectivity
- Flyway database migrations
- Spring Security integration
- OAuth2 client support
- WebFlux reactive web framework

## Modules

### Main Application (`auth`)

The core authentication backend service.

### Keycloak User Storage SPI (`keycloak-spi`)

A read-only Keycloak User Storage Provider that validates users by calling the auth backend's REST API.

#### Building the SPI

```bash
./gradlew :keycloak-spi:shadowJar
```

The fat JAR will be created at `keycloak-spi/build/libs/keycloak-user-storage-spi-0.0.1-SNAPSHOT.jar`.

#### Deploying to Keycloak

**Option 1: Local/Standalone Keycloak**

1. Copy the JAR to Keycloak's providers directory:
   ```bash
   cp keycloak-spi/build/libs/keycloak-user-storage-spi-0.0.1-SNAPSHOT.jar $KEYCLOAK_HOME/providers/
   ```

2. Rebuild Keycloak (or restart):
   ```bash
   $KEYCLOAK_HOME/bin/kc.sh build
   ```

3. In Keycloak Admin Console, go to **User Federation** and add `kos-auth-storage` provider.

**Option 2: Docker Compose with Woodpecker CI**

Woodpecker CI builds the SPI on release tags and places the JAR in a shared volume accessible to Keycloak.

1. Create the shared providers directory on the host:
   ```bash
   sudo mkdir -p /opt/keycloak-providers
   sudo chmod 755 /opt/keycloak-providers
   ```

2. Add volume mount to your Keycloak service in docker-compose:
   ```yaml
   volumes:
     - /opt/keycloak-providers:/opt/keycloak/providers:ro
   ```

3. Create a release tag to deploy (see CI/CD section below).

**Option 3: Docker Compose (Local Development)**

For local development without Woodpecker CI:

```bash
# Build the SPI first
./gradlew :keycloak-spi:shadowJar

# Copy to shared providers folder
cp keycloak-spi/build/libs/keycloak-user-storage-spi-*.jar /opt/keycloak-providers/keycloak-user-storage-spi.jar

# Start Keycloak
docker compose up -d
```

#### How it Works

- The SPI calls `http://auth-backend:8080/api/users/{email}` to validate if a user exists
- Returns user data (id, email, firstName, lastName) on HTTP 200
- Returns null (user not found) on HTTP 404
- Uses Java 11+ HttpClient for REST calls
- Uses Keycloak's JsonSerialization for JSON parsing

## CI/CD

This project uses [Woodpecker CI](https://woodpecker-ci.org/) for continuous integration and deployment.

**Woodpecker Dashboard:** https://drone.keeplearningos.com/dsjkeeplearning/kos-auth-backend

### Pipelines

| Pipeline | Trigger | Description |
|----------|---------|-------------|
| `auth.yml` | Push/PR to `src/**` | Compile, build bootJar |
| `keycloak-spi.yml` | Push/PR to `keycloak-spi/**` | Compile, test, build JAR |
| `release-helper.yml` | Push to `main` | Creates/updates release PR with changelog |
| `auth-release.yml` | Tag `v*` | Build auth backend, GitHub release |
| `keycloak-spi-release.yml` | Tag `spi-v*` or Manual | Build, test, deploy SPI, GitHub release |
| `release-all.yml` | Tag `release-v*` or Manual | Build both modules, GitHub release |

### Automatic Builds

- **Pull Requests**: Runs compile and tests for affected modules only
- **Push to main**: Runs build for affected modules, plus updates release PR
- **Path filtering**: Changes to `keycloak-spi/**` only trigger SPI pipeline, changes to `src/**` only trigger auth pipeline
- **Gradle caching**: Dependencies cached at `/opt/woodpecker-cache/gradle` for faster builds

### Release Workflow (Hybrid Approach)

This project uses a **hybrid release strategy** combining PR-based semantic versioning for the auth backend with manual/tag-based releases for the Keycloak SPI.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     AUTH BACKEND RELEASE FLOW                               │
│                     (Automated via ready-release-go)                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌──────────┐    ┌──────────┐    ┌──────────────┐    ┌──────────────┐     │
│   │ Create   │───>│ Merge to │───>│ Release PR   │───>│ Merge        │     │
│   │ PR +     │    │ main     │    │ auto-created │    │ Release PR   │     │
│   │ Labels   │    │          │    │ on release/  │    │              │     │
│   └──────────┘    └──────────┘    └──────────────┘    └──────┬───────┘     │
│                                                              │             │
│                                                              v             │
│                                    ┌──────────────┐    ┌──────────────┐    │
│                                    │ GitHub       │<───│ Tag created  │    │
│                                    │ Release      │    │ (v1.2.0)     │    │
│                                    └──────────────┘    └──────────────┘    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                     KEYCLOAK SPI RELEASE FLOW                               │
│                     (Manual trigger or tag push)                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Option A: Manual Trigger                                                  │
│   ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                 │
│   │ Woodpecker   │───>│ Auto-version │───>│ Build, Test  │                 │
│   │ UI: Run      │    │ spi-v1.0.X   │    │ Deploy, Tag  │                 │
│   └──────────────┘    └──────────────┘    └──────────────┘                 │
│                                                                             │
│   Option B: Tag Push                                                        │
│   ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                 │
│   │ git tag      │───>│ Pipeline     │───>│ Build, Test  │                 │
│   │ spi-v1.0.0   │    │ triggered    │    │ Deploy       │                 │
│   └──────────────┘    └──────────────┘    └──────────────┘                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Auth Backend (PR-Based with Semantic Versioning)

Uses [ready-release-go](https://woodpecker-ci.org/plugins/ready-release-go) for automated releases with semantic versioning based on PR labels.

**How it works:**

1. **Create PRs with labels** - Add labels to categorize changes:

   | Label | Version Bump | Changelog Section |
   |-------|--------------|-------------------|
   | `breaking`, `breaking-change` | Major (1.0.0 → 2.0.0) | 💥 Breaking Changes |
   | `feature`, `enhancement` | Minor (1.0.0 → 1.1.0) | ✨ Features |
   | `bug`, `bugfix`, `fix` | Patch (1.0.0 → 1.0.1) | 🐛 Bug Fixes |
   | `security` | Patch | 🔒 Security |
   | `documentation`, `docs` | Patch | 📚 Documentation |
   | `dependencies`, `deps` | Patch | 📦 Dependencies |
   | `chore`, `maintenance`, `ci` | Patch | 🔨 Maintenance |
   | `keycloak-spi`, `spi` | Patch | 🔧 Keycloak SPI |
   | `refactor`, `refactoring` | Patch | 🏗️ Refactoring |

2. **Merge PRs to main** - The `release-helper` pipeline automatically:
   - Creates/updates a release PR on branch `release/next`
   - Generates changelog from merged PRs grouped by category
   - Determines next version based on highest-priority label (breaking > feature > patch)

3. **Review the release PR** - The PR contains:
   - Updated `CHANGELOG.md` with all changes since last release
   - Version bump in relevant files
   - Summary of all included PRs

4. **Merge the release PR** - This triggers:
   - Automatic tag creation (e.g., `v1.2.0`)
   - `auth-release.yml` pipeline runs
   - JAR built and GitHub release created with changelog

**Example workflow:**

```bash
# 1. Create feature branch
git checkout -b feature/add-user-endpoint

# 2. Make changes and commit
git add .
git commit -m "Add user profile endpoint"

# 3. Push and create PR
git push origin feature/add-user-endpoint
# Create PR on GitHub with label "feature"

# 4. After PR review and merge to main:
#    - release-helper creates/updates release PR
#    - Review release PR on branch "release/next"
#    - Merge release PR → tag v1.1.0 created → release pipeline runs
```

#### Keycloak SPI (Manual or Tag-Based)

The SPI module uses a simpler release flow since it's a separate deployable artifact.

**Option 1: Manual trigger from Woodpecker UI (Recommended)**

1. Go to [Woodpecker Dashboard](https://drone.keeplearningos.com/dsjkeeplearning/kos-auth-backend)
2. Select `keycloak-spi-release.yml` pipeline
3. Click **Run Pipeline** (manual trigger)
4. Pipeline will:
   - Find latest `spi-v*` tag
   - Auto-increment patch version (e.g., `spi-v1.0.0` → `spi-v1.0.1`)
   - Create and push the new tag
   - Build, test, and deploy SPI JAR
   - Create GitHub release

**Option 2: Push a specific tag**

```bash
# For a specific version
git tag spi-v1.0.0
git push origin spi-v1.0.0

# For minor version bump
git tag spi-v1.1.0
git push origin spi-v1.1.0
```

#### Combined Release (Both Modules)

Use this when you want to release both modules together with the same version.

**Option 1: Manual trigger from Woodpecker UI**
- Run `release-all.yml` manually
- Auto-increments to next `release-v*` patch version

**Option 2: Push a tag**
```bash
git tag release-v1.0.0
git push origin release-v1.0.0
```

### Setting Up GitHub Labels

Create these labels in your GitHub repository for the release workflow:

```bash
# Using GitHub CLI (gh)
gh label create "breaking" --color "d73a4a" --description "Breaking change (major version bump)"
gh label create "breaking-change" --color "d73a4a" --description "Breaking change (major version bump)"
gh label create "feature" --color "0e8a16" --description "New feature (minor version bump)"
gh label create "enhancement" --color "0e8a16" --description "Enhancement (minor version bump)"
gh label create "bug" --color "d93f0b" --description "Bug fix (patch version bump)"
gh label create "bugfix" --color "d93f0b" --description "Bug fix (patch version bump)"
gh label create "fix" --color "d93f0b" --description "Bug fix (patch version bump)"
gh label create "security" --color "ee0701" --description "Security fix (patch version bump)"
gh label create "documentation" --color "0075ca" --description "Documentation (patch version bump)"
gh label create "docs" --color "0075ca" --description "Documentation (patch version bump)"
gh label create "dependencies" --color "0366d6" --description "Dependency update (patch version bump)"
gh label create "deps" --color "0366d6" --description "Dependency update (patch version bump)"
gh label create "chore" --color "fef2c0" --description "Maintenance (patch version bump)"
gh label create "maintenance" --color "fef2c0" --description "Maintenance (patch version bump)"
gh label create "ci" --color "fef2c0" --description "CI/CD changes (patch version bump)"
gh label create "refactor" --color "c5def5" --description "Refactoring (patch version bump)"
gh label create "keycloak-spi" --color "7057ff" --description "Keycloak SPI changes (patch version bump)"
gh label create "spi" --color "7057ff" --description "Keycloak SPI changes (patch version bump)"
gh label create "skip-changelog" --color "ededed" --description "Exclude from release notes"
gh label create "no-release" --color "ededed" --description "Exclude from release notes"
```

### Release Configuration

The release behavior is configured in `release-config.ts` at the project root:

```typescript
// release-config.ts
export default {
  tagPrefix: 'v',                           // Tag format: v1.0.0
  changeTypes: [
    { title: '💥 Breaking Changes', labels: ['breaking'], bump: 'major', weight: 100 },
    { title: '✨ Features', labels: ['feature'], bump: 'minor', weight: 80 },
    { title: '🐛 Bug Fixes', labels: ['bug'], bump: 'patch', weight: 70 },
    // ... more categories
  ],
  skipLabels: ['skip-changelog', 'no-release'],  // PRs to exclude
  skipCommitsWithoutPullRequest: true,            // Only include PR-based changes
  commentOnReleasedPullRequests: true,            // Comment on PRs when released
};
```

### PR Labels Quick Reference

| Label | Emoji | Version | Use When |
|-------|-------|---------|----------|
| `breaking` | 💥 | Major | API changes, removed features, incompatible changes |
| `feature` | ✨ | Minor | New endpoints, new functionality |
| `bug` | 🐛 | Patch | Bug fixes, error corrections |
| `security` | 🔒 | Patch | Security vulnerabilities, CVE fixes |
| `keycloak-spi` | 🔧 | Patch | Changes to the Keycloak SPI module |
| `docs` | 📚 | Patch | README, documentation updates |
| `refactor` | 🏗️ | Patch | Code refactoring, no behavior change |
| `dependencies` | 📦 | Patch | Dependency updates |
| `chore` | 🔨 | Patch | Build scripts, CI, tooling |
| `skip-changelog` | - | - | Exclude from release notes |

### Release Pipeline Actions

On release, the pipeline will:

| Step | Auth Backend | Keycloak SPI | Combined |
|------|--------------|--------------|----------|
| Build | `./gradlew bootJar` | `./gradlew :keycloak-spi:shadowJar` | Both |
| Test | Skipped (tested in PR) | `./gradlew :keycloak-spi:test` | SPI only |
| Deploy JAR | - | Copy to `/opt/keycloak-providers/` | SPI only |
| Changelog | Generate from commits | Generate from commits | Generate from commits |
| Update README | Update version badge | Update version badge | Both badges |
| GitHub Release | Create with JAR | Create with JAR + test report | Both JARs |

### After SPI Release

Restart Keycloak to load the new SPI:
```bash
docker compose restart keycloak
```

Verify the SPI is loaded:
```bash
# Check Keycloak logs
docker compose logs keycloak | grep "kos-auth-storage"

# Or check in Keycloak Admin Console
# Go to: Realm Settings → User Federation → Add provider
# You should see "kos-auth-storage" in the list
```

### Woodpecker Endpoints

| Endpoint | URL |
|----------|-----|
| Dashboard | https://drone.keeplearningos.com/dsjkeeplearning/kos-auth-backend |
| Build Status Badge | `https://drone.keeplearningos.com/api/badges/dsjkeeplearning/kos-auth-backend/status.svg` |

### Troubleshooting Releases

**Release PR not being created:**
- Ensure PRs have at least one recognized label
- Check `release-helper.yml` pipeline logs in Woodpecker
- Verify `github_token` secret is configured in Woodpecker

**Manual SPI release not incrementing version:**
- Check if there are existing `spi-v*` tags: `git tag -l 'spi-v*'`
- Ensure the pipeline has permission to push tags (check `github_token`)

**SPI not loading in Keycloak:**
- Verify JAR exists: `ls -la /opt/keycloak-providers/`
- Check Keycloak has read access to the volume
- Restart Keycloak: `docker compose restart keycloak`
- Check logs: `docker compose logs keycloak | grep -i error`

**Pipeline failing on README update:**
- This can happen if main branch is protected
- Either allow the CI bot to push, or remove the `update-readme` step

**Changelog showing "No changes":**
- Ensure commits follow the path patterns in changelog generation
- Check that the previous tag exists for comparison

## Configuration

The application uses environment variables for configuration. All database-related settings can be configured via the `.env` file:

- `DB_HOST`: Database host (default: localhost)
- `DB_PORT`: Database port (default: 5432, configured as 5433 in .env)
- `DB_NAME`: Database name (default: kos-auth)
- `DB_USERNAME`: Database username (default: postgres)
- `DB_PASSWORD`: Database password
