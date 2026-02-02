package com.keeplearning.gradle

import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSuccessResult
import java.util.concurrent.atomic.AtomicInteger

/**
 * Build service that tracks task-level cache metrics using Gradle's public API.
 *
 * This provides information about which tasks were loaded from cache vs executed.
 * Uses the stable public API (OperationCompletionListener).
 */
abstract class BuildCacheMetricsService : BuildService<BuildCacheMetricsService.Params>,
    OperationCompletionListener, AutoCloseable {

    interface Params : BuildServiceParameters {
        val enableLogging: Property<Boolean>
    }

    private val tasksFromCache = AtomicInteger(0)
    private val tasksExecuted = AtomicInteger(0)
    private val tasksUpToDate = AtomicInteger(0)

    override fun onFinish(event: FinishEvent) {
        if (event is TaskFinishEvent) {
            val result = event.result
            if (result is TaskSuccessResult) {
                val taskPath = event.descriptor.taskPath
                when {
                    result.isFromCache -> {
                        tasksFromCache.incrementAndGet()
                        if (parameters.enableLogging.getOrElse(false)) {
                            println("  [FROM-CACHE] $taskPath")
                        }
                    }
                    result.isUpToDate -> {
                        tasksUpToDate.incrementAndGet()
                    }
                    else -> {
                        tasksExecuted.incrementAndGet()
                        if (parameters.enableLogging.getOrElse(false)) {
                            println("  [EXECUTED] $taskPath")
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        val total = tasksFromCache.get() + tasksExecuted.get()
        if (total > 0) {
            val hitRate = tasksFromCache.get() * 100.0 / total
            println()
            println("╔═══════════════════════════════════════════════════════╗")
            println("║              TASK EXECUTION SUMMARY                   ║")
            println("╠═══════════════════════════════════════════════════════╣")
            println("║  From cache: ${tasksFromCache.get().toString().padStart(4)}   Executed: ${tasksExecuted.get().toString().padStart(4)}   Up-to-date: ${tasksUpToDate.get().toString().padStart(4)} ║")
            println("║  Cache hit rate: ${String.format("%5.1f%%", hitRate)}                               ║")
            println("╚═══════════════════════════════════════════════════════╝")
        }
    }
}
