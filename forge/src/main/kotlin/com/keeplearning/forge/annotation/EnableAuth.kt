package com.keeplearning.forge.annotation

import com.keeplearning.forge.config.AuthAutoConfiguration
import org.springframework.context.annotation.Import

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(AuthAutoConfiguration::class)
annotation class EnableAuth

@Deprecated("Renamed to EnableAuth", ReplaceWith("EnableAuth"))
typealias EnableForge = EnableAuth
