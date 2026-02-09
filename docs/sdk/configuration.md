# Configuration

## Properties Reference

All properties are under the `forge` prefix:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `forge.base-url` | `String` | *(required)* | Base URL of the KOS Auth backend (e.g., `https://auth.example.com`) |
| `forge.realm-name` | `String` | *(required)* | Keycloak realm name — used to construct SCIM API paths |
| `forge.client-registration-id` | `String` | `forge` | Spring Security OAuth2 client registration ID for authentication |
| `forge.api-version` | `String` | `1.0` | API version sent in the `API-Version` header |
| `forge.startup-sync.enabled` | `Boolean` | `true` | Enable automatic user sync on application startup (see [Startup Sync](startup-sync.md)) |

!!! note
    Auto-configuration only activates when `forge.base-url` is set. Without it, no Forge beans are created.

## Full `application.yml` Example

```yaml
forge:
  base-url: https://auth.example.com
  realm-name: my-realm
  client-registration-id: forge
  api-version: "1.0"
  startup-sync:
    enabled: true        # default; set to false to disable

spring:
  security:
    oauth2:
      client:
        registration:
          forge:                            # matches forge.client-registration-id
            provider: keycloak
            client-id: my-service-client
            client-secret: ${MY_CLIENT_SECRET}
            authorization-grant-type: client_credentials
            scope: openid
        provider:
          keycloak:
            issuer-uri: https://keycloak.example.com/realms/my-realm
```

The `client_credentials` grant allows your service to authenticate with Keycloak without user interaction. The obtained token is automatically attached to every SCIM API request.

## How Auto-Configuration Works

`ForgeAutoConfiguration` creates two beans when `forge.base-url` is present:

### 1. `forgeWebClient` (WebClient)

A reactive `WebClient` configured with:

- **Base URL** from `forge.base-url`
- **OAuth2 filter** using `ServerOAuth2AuthorizedClientExchangeFilterFunction` — automatically acquires and refreshes tokens using the configured client registration

### 2. `scimClient` (ScimClient)

Wraps the `forgeWebClient` with SCIM-specific logic. This is the primary bean you'll inject into your services.

Both beans are `@ConditionalOnMissingBean` — you can override either by defining your own.

### 3. `startupSyncRunner` (StartupSyncRunner) — *conditional*

Registered only when:

- An `AuthStartupSync` bean is present (you provide the implementation)
- `forge.startup-sync.enabled` is `true` (default)

See [Startup Sync](startup-sync.md) for details.

## Customizing the WebClient

To add custom headers, logging, timeouts, or other configuration, define your own `forgeWebClient` bean:

```kotlin
@Configuration
class ForgeConfig {

    @Bean
    fun forgeWebClient(
        properties: ForgeProperties,
        authorizedClientManager: ReactiveOAuth2AuthorizedClientManager
    ): WebClient {
        val oauth2 = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
        oauth2.setDefaultClientRegistrationId(properties.clientRegistrationId)

        return WebClient.builder()
            .baseUrl(properties.baseUrl)
            .filter(oauth2)
            .defaultHeader("X-Custom-Header", "value")
            .codecs { it.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) }
            .build()
    }
}
```

The auto-configured `scimClient` bean will use your custom `WebClient` automatically.

## OAuth2 Client Setup

The SDK requires a **confidential client** in Keycloak configured for the `client_credentials` grant:

1. In Keycloak Admin Console, create a new client in your realm
2. Set **Client authentication** to `On` (confidential)
3. Enable **Service accounts roles**
4. Note the client ID and secret
5. Assign the necessary roles/scopes for SCIM API access

The Spring Security registration in `application.yml` connects this client to the Forge SDK.
