# Startup Sync

The Forge SDK includes a startup sync mechanism that automatically reconciles your local user database with the KOS Auth backend when your application starts.

## How It Works

1. Your application provides an `AuthStartupSync` implementation that knows how to fetch local users
2. On startup, the SDK computes a SHA-256 checksum of your local users
3. It fetches the remote checksum from the auth backend (`GET /Users/checksum`)
4. If checksums match, no action is taken
5. If they differ, the SDK diffs by email and sends a bulk request to create missing users and update changed ones
6. Users that exist remotely but not locally are **ignored** (no deletes)

```
App starts → fetch local users → compute checksum
                                        ↓
                              fetch remote checksum
                                        ↓
                                match? ─── yes → done (no-op)
                                  │
                                  no
                                  ↓
                        fetch all remote users
                                  ↓
                            diff by email
                                  ↓
                    ┌─────────────┼─────────────┐
                    ↓             ↓              ↓
              local only    both (changed)   remote only
              → POST         → PUT           → ignored
                    ↓             ↓
                 bulk request → done
```

## Setup

### 1. Implement AuthStartupSync

Create a Spring bean that extends `AuthStartupSync<T>` where `T` is your local user type:

```kotlin
@Component
class MyUserSync(
    private val localUserRepository: LocalUserRepository
) : AuthStartupSync<LocalUser>() {

    override suspend fun fetchAllLocalUsers(): List<LocalUser> {
        return localUserRepository.findAll()
    }

    override fun mapToAuthUser(localUser: LocalUser): AuthUser {
        return object : AuthUser() {
            init {
                email = localUser.email
                firstName = localUser.firstName
                lastName = localUser.lastName
                displayName = "${localUser.firstName} ${localUser.lastName}"
                phone = localUser.phoneNumber
                jobTitle = localUser.title
                active = localUser.isEnabled
                externalId = localUser.keycloakId
            }
        }
    }
}
```

!!! tip
    The `mapToAuthUser` method controls which fields are compared. Only fields you set on the `AuthUser` will be included in the checksum and sync.

### 2. Configure (optional)

Startup sync is enabled by default when an `AuthStartupSync` bean is present. To disable:

```yaml
forge:
  startup-sync:
    enabled: false
```

### 3. That's it

The `StartupSyncRunner` bean is automatically registered by `AuthAutoConfiguration` when:

- `forge.base-url` is configured
- An `AuthStartupSync` bean exists in the application context
- `forge.startup-sync.enabled` is `true` (default)

## Behavior Details

### Checksum Algorithm

The checksum is a SHA-256 hash computed identically on both the client and server:

1. For each user, produce a canonical string: `email|externalId|firstName|lastName|displayName|phone|title|active`
2. Null fields become empty strings
3. Sort all strings by email (case-insensitive)
4. Join with newline (`\n`)
5. SHA-256 hash (hex-encoded)

This is implemented in `ScimChecksumUtil` (shared via `scim-common`), ensuring both sides compute the same hash.

### Diff Strategy

When checksums differ, the SDK fetches all remote users and diffs by email:

| Scenario | Action |
|----------|--------|
| User in local but not remote | Bulk POST (create) |
| User in both, canonical strings differ | Bulk PUT (update) |
| User in both, canonical strings match | No action |
| User in remote but not local | **Ignored** (no delete) |

### Error Handling

- Sync failures are **non-fatal** — the application starts normally even if sync fails
- Errors are logged at `ERROR` level with full stack traces
- Individual bulk operation failures don't block other operations
- Failed operations are logged at `WARN` level with the `bulkId` and status

### Performance

- The checksum comparison avoids fetching all remote users when nothing has changed
- When sync is needed, all changes are sent in a single bulk request
- Remote users are fetched with pagination (100 per page)

## Example: JPA Entity Sync

```kotlin
@Entity
@Table(name = "employees")
class Employee {
    @Id @GeneratedValue
    var id: Long? = null
    var email: String = ""
    var firstName: String? = null
    var lastName: String? = null
    var department: String? = null
    var active: Boolean = true
}

@Repository
interface EmployeeRepository : JpaRepository<Employee, Long>

@Component
class EmployeeSync(
    private val employeeRepo: EmployeeRepository
) : AuthStartupSync<Employee>() {

    override suspend fun fetchAllLocalUsers(): List<Employee> {
        return employeeRepo.findAll()
    }

    override fun mapToAuthUser(localUser: Employee): AuthUser {
        return object : AuthUser() {
            init {
                email = localUser.email
                firstName = localUser.firstName
                lastName = localUser.lastName
                displayName = listOfNotNull(localUser.firstName, localUser.lastName)
                    .joinToString(" ")
                    .ifBlank { null }
                active = localUser.active
            }
        }
    }
}
```

## Disabling Sync

There are two ways to prevent sync from running:

### 1. Property flag

```yaml
forge:
  startup-sync:
    enabled: false
```

### 2. Don't register the bean

Simply don't create an `AuthStartupSync` implementation. The `StartupSyncRunner` is `@ConditionalOnBean(AuthStartupSync::class)` — it won't be created without one.
