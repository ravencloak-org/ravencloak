plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "com.keeplearning"
version = findProperty("version")?.toString()?.takeIf { it != "unspecified" } ?: "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("tools.jackson:jackson-bom:3.0.3"))
    implementation("com.fasterxml.jackson.core:jackson-annotations")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "com.keeplearning"
            artifactId = "scim-common"
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
