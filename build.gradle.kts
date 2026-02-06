plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	id("org.springframework.boot") version "4.0.1"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.graalvm.buildtools.native") version "0.11.3"
	id("com.google.cloud.tools.jib") version "3.5.2"
	id("build-cache-metrics")
}

// Configure build cache metrics reporting
buildCacheMetrics {
	enableLogging.set(false)          // Set to true to log individual cache hits/misses
	enableDetailedLogging.set(false)  // Set to true for debugging internal operations
	useInternalApi.set(true)          // Use internal API for local cache metrics
}

group = "com.keeplearning"
version = findProperty("appVersion")?.toString() ?: "0.0.1-SNAPSHOT"
description = "Authentication Backend"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("io.netty:netty-resolver-dns-native-macos:4.1.100.Final:osx-aarch_64")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-webclient")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
	implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:3.0.1")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("org.postgresql:postgresql")
	implementation("org.postgresql:r2dbc-postgresql")

	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-r2dbc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-client-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webclient-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
	testImplementation("io.mockk:mockk:1.13.13")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testRuntimeOnly("io.r2dbc:r2dbc-h2")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
	testLogging {
		events("passed", "skipped", "failed")
		showExceptions = true
		showCauses = true
		showStackTraces = true
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
	}
}

jib {
	from {
		image = "eclipse-temurin:21-jre-noble"
		platforms {
			platform {
				architecture = "arm64"
				os = "linux"
			}
		}
	}
	to {
		image = "ghcr.io/dsjkeeplearning/kos-auth-backend"
		tags = setOf(project.version.toString(), "latest")
	}
	container {
		ports = listOf("8080")
		mainClass = "com.keeplearning.auth.KosAuthApplicationKt"
		creationTime.set("USE_CURRENT_TIMESTAMP")
	}
}

// Disable AOT processing for development (causes issues with R2DBC Json type)
tasks.named("processAot") {
	enabled = false
}

tasks.named("processTestAot") {
	enabled = false
}
