package com.keeplearning.gradle

import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Build service that tracks build cache metrics using Gradle's internal API.
 *
 * NOTE: The S3/remote cache plugin (burrunan) operates outside of Gradle's
 * BuildOperation system and prints its own statistics. This listener captures
 * local cache operations that Gradle manages internally.
 *
 * Uses internal Gradle APIs which may change between versions.
 */
abstract class BuildCacheInternalMetricsService : BuildService<BuildCacheInternalMetricsService.Params>,
    BuildOperationListener, AutoCloseable {

    interface Params : BuildServiceParameters {
        val enableLogging: Property<Boolean>
        val enableDetailedLogging: Property<Boolean>
    }

    // Local cache metrics
    private val localCacheLoads = AtomicInteger(0)
    private val localCacheHits = AtomicInteger(0)
    private val localCacheStores = AtomicInteger(0)
    private val localBytesLoaded = AtomicLong(0)
    private val localBytesStored = AtomicLong(0)

    override fun started(buildOperation: BuildOperationDescriptor, startEvent: OperationStartEvent) {
        // Not needed for cache metrics
    }

    override fun progress(operationIdentifier: OperationIdentifier, progressEvent: OperationProgressEvent) {
        // Not needed for cache metrics
    }

    override fun finished(buildOperation: BuildOperationDescriptor, finishEvent: OperationFinishEvent) {
        val details = buildOperation.details ?: return
        val result = finishEvent.result
        val detailsClassName = details.javaClass.name
        val displayName = buildOperation.displayName ?: ""

        // Debug logging
        if (parameters.enableDetailedLogging.getOrElse(false)) {
            if (detailsClassName.contains("Cache", ignoreCase = true) ||
                detailsClassName.contains("BuildCacheService", ignoreCase = true)) {
                println("  [DEBUG] Operation: $displayName")
                println("          Details: $detailsClassName")
                println("          Result: ${result?.javaClass?.name}")
            }
        }

        when {
            // Local cache LOAD operations
            // Class: OpFiringLocalBuildCacheServiceHandle$LocalLoadDetails
            detailsClassName.contains("LocalLoadDetails") ||
            (detailsClassName.contains("LocalBuildCacheServiceHandle") && displayName.contains("Load")) -> {
                localCacheLoads.incrementAndGet()
                val archiveSize = extractArchiveSize(result)
                if (archiveSize != null && archiveSize > 0) {
                    localCacheHits.incrementAndGet()
                    localBytesLoaded.addAndGet(archiveSize)
                    logIfEnabled("  [LOCAL HIT] $displayName (${formatBytes(archiveSize)})")
                }
            }

            // Local cache STORE operations
            // Class: OpFiringLocalBuildCacheServiceHandle$LocalStoreDetails
            detailsClassName.contains("LocalStoreDetails") ||
            (detailsClassName.contains("LocalBuildCacheServiceHandle") && displayName.contains("Store")) -> {
                localCacheStores.incrementAndGet()
                val archiveSize = extractArchiveSize(result)
                if (archiveSize != null) {
                    localBytesStored.addAndGet(archiveSize)
                }
                logIfEnabled("  [LOCAL STORE] $displayName")
            }
        }
    }

    private fun extractArchiveSize(result: Any?): Long? {
        if (result == null) return null
        return try {
            // Try getArchiveSize method (common in cache results)
            val method = result.javaClass.methods.find { it.name == "getArchiveSize" }
            method?.invoke(result) as? Long
        } catch (e: Exception) {
            null
        }
    }

    private fun formatBytes(bytes: Long?): String {
        if (bytes == null || bytes == 0L) return "0 B"
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    private fun logIfEnabled(message: String) {
        if (parameters.enableLogging.getOrElse(false)) {
            println(message)
        }
    }

    override fun close() {
        // Only show if we captured any operations
        if (localCacheLoads.get() > 0 || localCacheStores.get() > 0) {
            val hitRate = if (localCacheLoads.get() > 0) {
                localCacheHits.get() * 100.0 / localCacheLoads.get()
            } else 0.0

            println()
            println("╔═══════════════════════════════════════════════════════╗")
            println("║              LOCAL BUILD CACHE                        ║")
            println("╠═══════════════════════════════════════════════════════╣")
            println("║  Lookups: ${localCacheLoads.get().toString().padStart(6)}   Hits: ${localCacheHits.get().toString().padStart(6)}   Rate: ${String.format("%5.1f%%", hitRate)}    ║")
            println("║  Stores:  ${localCacheStores.get().toString().padStart(6)}                                    ║")
            println("╚═══════════════════════════════════════════════════════╝")
        }
    }
}
