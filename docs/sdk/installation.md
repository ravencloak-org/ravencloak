# Installation

Add the Forge SDK to your Spring Boot project to integrate with the KOS Auth user provisioning API.

## Add the Dependency

The SDK is published to **GitHub Packages**. First, add the repository, then the dependency.

=== "Kotlin DSL"

    ```kotlin
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/dsjkeeplearning/kos-auth-backend")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }

    dependencies {
        implementation("com.keeplearning:forge:{{ sdk_version }}")
    }
    ```

=== "Groovy DSL"

    ```groovy
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/dsjkeeplearning/kos-auth-backend")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }

    dependencies {
        implementation "com.keeplearning:forge:{{ sdk_version }}"
    }
    ```

## GitHub Packages Authentication

GitHub Packages requires authentication even for downloading public packages.

1. Create a **Personal Access Token (classic)** with the `read:packages` scope at [GitHub Settings > Tokens](https://github.com/settings/tokens)

2. Set the environment variables:

    ```bash
    export GITHUB_ACTOR=your-github-username
    export GITHUB_TOKEN=ghp_your_token_here
    ```

    Or add them to your `~/.gradle/gradle.properties`:

    ```properties
    gpr.user=your-github-username
    gpr.key=ghp_your_token_here
    ```

    Then reference them in your build file:

    ```kotlin
    credentials {
        username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
        password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
    }
    ```

## Enable Auto-Configuration

The SDK uses Spring Boot's auto-configuration mechanism. No annotation is required — just having the dependency on the classpath and setting `forge.base-url` is enough.

If you prefer explicit opt-in, use the `@EnableAuth` annotation:

```kotlin
@SpringBootApplication
@EnableAuth
class MyApplication
```

!!! tip
    `@EnableAuth` is optional. The SDK registers itself via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, so auto-configuration activates automatically when `forge.base-url` is set.

!!! note "Migration from @EnableForge"
    `@EnableAuth` was previously named `@EnableForge`. The old name is still available as a `@Deprecated` typealias.

## Verify Installation

After adding the dependency and setting the minimum required properties:

```yaml
forge:
  base-url: https://auth.example.com
  realm-name: my-realm
```

Start your application. You should see no errors related to Forge beans. The `ScimClient` bean is now available for injection.

## Next Steps

- [Configuration](configuration.md) — full property reference and OAuth2 setup
- [API Reference](api-reference.md) — all available methods and DTOs
- [Usage Guide](usage-guide.md) — recipes and common patterns
