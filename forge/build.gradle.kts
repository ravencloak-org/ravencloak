plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.spring.dependency-management") version "1.1.7"
    `maven-publish`
}

group = "com.keeplearning"
version = findProperty("version")?.toString()?.takeIf { it != "unspecified" } ?: "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.1")
    }
}

dependencies {
    api(project(":scim-common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("tools.jackson.module:jackson-module-kotlin")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "com.keeplearning"
            artifactId = "forge"
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/dsjkeeplearning/kos-auth-backend")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: "dsjkeeplearning"
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}
