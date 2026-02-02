package com.keeplearning.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Property
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.internal.build.event.BuildEventListenerRegistryInternal
import javax.inject.Inject

/**
 * Plugin that registers build cache metrics listeners.
 *
 * Apply this plugin to your root build.gradle.kts:
 * ```
 * plugins {
 *     id("build-cache-metrics")
 * }
 *
 * buildCacheMetrics {
 *     enableLogging.set(true)           // Log cache hits/misses during build
 *     enableDetailedLogging.set(false)  // Log internal operation details (debug)
 *     useInternalApi.set(true)          // Use internal API for local/remote distinction
 * }
 * ```
 */
abstract class BuildCacheMetricsPlugin @Inject constructor(
    private val buildEventsListenerRegistry: BuildEventsListenerRegistry
) : Plugin<Project> {

    override fun apply(project: Project) {
        if (project != project.rootProject) {
            project.logger.warn("build-cache-metrics plugin should only be applied to the root project")
            return
        }

        val extension = project.extensions.create("buildCacheMetrics", BuildCacheMetricsExtension::class.java)

        // Set defaults
        extension.enableLogging.convention(false)
        extension.enableDetailedLogging.convention(false)
        extension.useInternalApi.convention(true)

        // Register the public API metrics service
        val publicMetricsService = project.gradle.sharedServices.registerIfAbsent(
            "buildCacheMetricsService",
            BuildCacheMetricsService::class.java
        ) {
            parameters.enableLogging.set(extension.enableLogging)
        }

        // Register with public API
        buildEventsListenerRegistry.onTaskCompletion(publicMetricsService)

        // Register the internal API metrics service if enabled
        project.afterEvaluate {
            if (extension.useInternalApi.get()) {
                registerInternalMetricsService(project, extension)
            }
        }
    }

    private fun registerInternalMetricsService(project: Project, extension: BuildCacheMetricsExtension) {
        try {
            val internalMetricsService = project.gradle.sharedServices.registerIfAbsent(
                "buildCacheInternalMetricsService",
                BuildCacheInternalMetricsService::class.java
            ) {
                parameters.enableLogging.set(extension.enableLogging)
                parameters.enableDetailedLogging.set(extension.enableDetailedLogging)
            }

            // Get internal registry using reflection
            val gradle = project.gradle
            val internalRegistry = getInternalRegistry(gradle)

            if (internalRegistry != null) {
                internalRegistry.onOperationCompletion(internalMetricsService)
                project.logger.info("Registered BuildCacheInternalMetricsService with internal API")
            } else {
                project.logger.warn("Could not access internal BuildEventListenerRegistry, detailed metrics unavailable")
            }
        } catch (e: Exception) {
            project.logger.warn("Failed to register internal metrics service: ${e.message}")
        }
    }

    private fun getInternalRegistry(gradle: Gradle): BuildEventListenerRegistryInternal? {
        return try {
            // Try to get from services
            val servicesMethod = gradle.javaClass.methods.find { it.name == "getServices" }
            val services = servicesMethod?.invoke(gradle)
            val getMethod = services?.javaClass?.methods?.find {
                it.name == "get" && it.parameterCount == 1
            }
            getMethod?.invoke(services, BuildEventListenerRegistryInternal::class.java) as? BuildEventListenerRegistryInternal
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Extension for configuring build cache metrics.
 */
interface BuildCacheMetricsExtension {
    /**
     * Enable logging of cache hits/misses during the build.
     */
    val enableLogging: Property<Boolean>

    /**
     * Enable detailed logging of internal build operations (for debugging).
     */
    val enableDetailedLogging: Property<Boolean>

    /**
     * Use Gradle's internal API to distinguish local vs remote cache operations.
     * When false, only uses the public API which doesn't distinguish cache source.
     */
    val useInternalApi: Property<Boolean>
}
