plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "9.0.0-beta4"
    `maven-publish`
}

group = "com.keeplearning"
version = findProperty("version")?.toString()?.takeIf { it != "unspecified" } ?: "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

val keycloakVersion = "26.5.2"

dependencies {
    // Keycloak SPI dependencies - compileOnly since Keycloak provides these at runtime
    compileOnly("org.keycloak:keycloak-server-spi:$keycloakVersion")
    compileOnly("org.keycloak:keycloak-core:$keycloakVersion")
    compileOnly("org.keycloak:keycloak-server-spi-private:$keycloakVersion")
    compileOnly("org.keycloak:keycloak-services:$keycloakVersion")

    // Kotlin stdlib - will be bundled in the fat JAR
    implementation(kotlin("stdlib"))

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("keycloak-user-storage-spi")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())

    // Relocate Kotlin stdlib to avoid conflicts with other plugins
    relocate("kotlin", "com.keeplearning.shadow.kotlin")
    relocate("org.jetbrains", "com.keeplearning.shadow.org.jetbrains")
    relocate("org.intellij", "com.keeplearning.shadow.org.intellij")

    // Exclude Keycloak and other server-provided dependencies
    dependencies {
        exclude(dependency("org.keycloak:.*"))
        exclude(dependency("org.jboss.*:.*"))
        exclude(dependency("jakarta.*:.*"))
    }

    // Merge service files properly
    mergeServiceFiles()
}

// Make the regular jar task depend on shadowJar for convenience
tasks.named("jar") {
    dependsOn(tasks.shadowJar)
}

publishing {
    publications {
        create<MavenPublication>("shadow") {
            artifact(tasks.shadowJar)
            groupId = "com.keeplearning"
            artifactId = "keycloak-user-storage-spi"
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
