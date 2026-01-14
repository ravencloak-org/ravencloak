# KOS Auth Backend

Authentication backend service built with Spring Boot and Kotlin.

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

## Configuration

The application uses environment variables for configuration. All database-related settings can be configured via the `.env` file:

- `DB_HOST`: Database host (default: localhost)
- `DB_PORT`: Database port (default: 5432, configured as 5433 in .env)
- `DB_NAME`: Database name (default: kos-auth)
- `DB_USERNAME`: Database username (default: postgres)
- `DB_PASSWORD`: Database password
