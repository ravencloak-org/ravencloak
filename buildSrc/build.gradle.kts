plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Access to Gradle's internal APIs for BuildOperationListener
    implementation(gradleApi())
}

gradlePlugin {
    plugins {
        create("buildCacheMetrics") {
            id = "build-cache-metrics"
            implementationClass = "com.keeplearning.gradle.BuildCacheMetricsPlugin"
        }
    }
}
