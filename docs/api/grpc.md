# gRPC User Provisioning API

The User Provisioning gRPC API provides a high-performance, strongly typed interface for third-party applications to manage users within their Keycloak realms. It replaces the former Forge REST SDK with auto-generated gRPC clients — no separate SDK to maintain.

## Connection

| Property | Default |
|----------|---------|
| Port | `9090` (configurable via `GRPC_PORT` env var) |
| Protocol | HTTP/2 (gRPC native) |
| Proto file | `src/main/proto/keeplearning/auth/provisioning/v1/user_provisioning.proto` |

## Service: `UserProvisioning`

**Package:** `keeplearning.auth.provisioning.v1`

### RPCs

| RPC | Request | Response | Description |
|-----|---------|----------|-------------|
| `CreateUser` | `CreateUserRequest` | `User` | Create a single user in a realm |
| `GetUser` | `GetUserRequest` | `User` | Get a user by ID |
| `ListUsers` | `ListUsersRequest` | `ListUsersResponse` | Paginated user listing with optional filter |
| `UpdateUser` | `UpdateUserRequest` | `User` | Full replacement of user fields |
| `DeleteUser` | `DeleteUserRequest` | `DeleteUserResponse` | Remove a user |
| `BulkCreateUsers` | `BulkCreateUsersRequest` | `BulkUsersResponse` | Batch create users |
| `BulkUpdateUsers` | `BulkUpdateUsersRequest` | `BulkUsersResponse` | Batch update users |
| `GetChecksum` | `GetChecksumRequest` | `ChecksumResponse` | SHA-256 checksum for sync verification |

### Error Codes

The service uses standard gRPC status codes:

| gRPC Code | When |
|-----------|------|
| `NOT_FOUND` | Realm or user does not exist |
| `ALREADY_EXISTS` | User with same email already exists in realm |
| `INVALID_ARGUMENT` | Malformed UUID, bad filter syntax, missing fields |
| `INTERNAL` | Unexpected server error |

### User Message

```protobuf
message User {
  string id = 1;             // Server-assigned UUID
  string email = 2;          // Unique within realm
  string external_id = 3;    // Keycloak user ID
  optional string first_name = 4;
  optional string last_name = 5;
  optional string display_name = 6;
  optional string phone = 7;
  optional string job_title = 8;
  bool active = 9;
  string created_at = 10;    // ISO 8601
  string updated_at = 11;    // ISO 8601
}
```

## Client Setup

### Generating Client Code

Copy the proto file from `src/main/proto/keeplearning/auth/provisioning/v1/user_provisioning.proto` into your project and use your language's protobuf toolchain to generate client stubs.

#### Kotlin / Java (Gradle)

```kotlin
plugins {
    id("com.google.protobuf") version "0.9.4"
}

dependencies {
    implementation("io.grpc:grpc-netty-shaded:1.70.0")
    implementation("io.grpc:grpc-protobuf:1.70.0")
    implementation("io.grpc:grpc-kotlin-stub:1.5.0") // For Kotlin coroutines
}
```

#### Go

```bash
protoc --go_out=. --go-grpc_out=. user_provisioning.proto
```

#### Python

```bash
python -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. user_provisioning.proto
```

### Example: Kotlin Client

```kotlin
val channel = ManagedChannelBuilder
    .forAddress("auth-backend", 9090)
    .usePlaintext()
    .build()

val stub = UserProvisioningGrpcKt.UserProvisioningCoroutineStub(channel)

// Create a user
val user = stub.createUser(createUserRequest {
    realmName = "my-realm"
    email = "john@example.com"
    externalId = "kc-user-id-123"
    firstName = "John"
    lastName = "Doe"
    active = true
})

// List users with filter
val response = stub.listUsers(listUsersRequest {
    realmName = "my-realm"
    filter = "displayName sw \"J\""
    startIndex = 1
    count = 50
})

// Sync verification
val checksum = stub.getChecksum(getChecksumRequest {
    realmName = "my-realm"
})
```

## Bulk Operations

Bulk RPCs process each operation independently — a single failure does not abort the batch. Each `BulkOperationResult` includes a `status` field (0 = OK) and an optional `error_message`.

```kotlin
val response = stub.bulkCreateUsers(bulkCreateUsersRequest {
    realmName = "my-realm"
    users += createUserData { email = "a@example.com"; active = true }
    users += createUserData { email = "b@example.com"; active = true }
})

for (result in response.resultsList) {
    if (result.status == 0) {
        println("Created: ${result.user.email}")
    } else {
        println("Failed: ${result.errorMessage}")
    }
}
```

## Checksum-Based Sync

Use `GetChecksum` to detect drift between local and remote user stores without fetching the full user list:

1. Compute a local checksum of your users (same SHA-256 algorithm)
2. Call `GetChecksum` for the realm
3. If checksums differ, fetch users and reconcile

The checksum is computed by canonicalizing each user (`email|externalId|firstName|lastName|displayName|phone|jobTitle|active`), sorting by email, and computing SHA-256 of the joined result.

## Migration from Forge SDK

| Forge SDK | gRPC Equivalent |
|-----------|-----------------|
| `AuthRepository.create(user)` | `stub.createUser(request)` |
| `AuthRepository.findById(id)` | `stub.getUser(request)` |
| `AuthRepository.findAll()` | `stub.listUsers(request)` |
| `AuthRepository.update(user)` | `stub.updateUser(request)` |
| `AuthRepository.delete(id)` | `stub.deleteUser(request)` |
| `AuthRepository.createAll(users)` | `stub.bulkCreateUsers(request)` |
| `AuthRepository.updateAll(users)` | `stub.bulkUpdateUsers(request)` |
| `ScimClient.getChecksum()` | `stub.getChecksum(request)` |
| `StartupSyncRunner` | Implement sync logic using `getChecksum` + `listUsers` + bulk RPCs |
