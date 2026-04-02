plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	id("org.springframework.boot") version "4.0.1"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.graalvm.buildtools.native") version "0.11.3"
	id("com.google.cloud.tools.jib") version "3.5.2"
	id("com.google.protobuf") version "0.9.4"
	id("build-cache-metrics")
	jacoco
}

// Configure build cache metrics reporting
buildCacheMetrics {
	enableLogging.set(false)          // Set to true to log individual cache hits/misses
	enableDetailedLogging.set(false)  // Set to true for debugging internal operations
	useInternalApi.set(true)          // Use internal API for local cache metrics
}

// OpenTelemetry Java Agent — auto-instruments WebFlux, WebClient, R2DBC, Netty
val otelAgent by configurations.creating

group = "com.keeplearning"
version = "0.0.1-SNAPSHOT"
description = "Authentication Backend"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

val grpcKotlinVersion = "1.5.0"

repositories {
	mavenCentral()
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.grpc:spring-grpc-dependencies:1.0.2")
	}
}

dependencies {
	otelAgent("io.opentelemetry.javaagent:opentelemetry-javaagent:2.12.0")
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
	implementation("com.unboundid.product.scim2:scim2-sdk-common:4.0.0")
	implementation(project(":scim-common"))
	// gRPC / Spring gRPC
	implementation("org.springframework.grpc:spring-grpc-spring-boot-starter")
	implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
	implementation("com.google.protobuf:protobuf-kotlin")
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
	finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required.set(true)
		html.required.set(true)
		csv.required.set(false)
	}
	// Exclude generated protobuf/gRPC classes from coverage reports
	classDirectories.setFrom(
		files(classDirectories.files.map {
			fileTree(it) {
				exclude(
					"**/grpc/provisioning/v1/**",
					"**/proto/**"
				)
			}
		})
	)
}

val copyOtelAgent by tasks.registering(Copy::class) {
	from(otelAgent)
	into(layout.buildDirectory.dir("otel"))
	rename { "opentelemetry-javaagent.jar" }
}

val jibTag: String? = findProperty("jibTag")?.toString()

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
		image = "ghcr.io/dsjkeeplearning/kos-auth-backend:latest"
		tags = if (!jibTag.isNullOrBlank()) setOf(jibTag) else emptySet()
	}
	container {
		ports = listOf("8080", "9090")
		mainClass = "com.keeplearning.auth.KosAuthApplicationKt"
		creationTime.set("USE_CURRENT_TIMESTAMP")
		jvmFlags = listOf("-javaagent:/app/otel/opentelemetry-javaagent.jar")
	}
	extraDirectories {
		paths {
			path {
				setFrom(layout.buildDirectory.dir("otel"))
				into = "/app/otel"
			}
		}
	}
}

// Ensure OTel agent is copied before JIB builds the container
tasks.matching { it.name.startsWith("jib") }.configureEach {
	dependsOn(copyOtelAgent)
}

// Disable AOT processing for development (causes issues with R2DBC Json type)
tasks.named("processAot") {
	enabled = false
}

tasks.named("processTestAot") {
	enabled = false
}

// ──────────────────── Protobuf / gRPC code generation ────────────────────
protobuf {
	protoc {
		artifact = "com.google.protobuf:protoc:${dependencyManagement.importedProperties["protobuf-java.version"]}"
	}
	plugins {
		create("grpc") {
			artifact = "io.grpc:protoc-gen-grpc-java:${dependencyManagement.importedProperties["grpc.version"]}"
		}
		create("grpckt") {
			artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar"
		}
	}
	generateProtoTasks {
		all().forEach { task ->
			task.plugins {
				create("grpc")
				create("grpckt")
			}
			task.builtins {
				create("kotlin")
			}
		}
	}
}
