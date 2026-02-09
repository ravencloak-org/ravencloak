package com.keeplearning.forge.annotation

import com.keeplearning.forge.config.ForgeAutoConfiguration
import org.springframework.context.annotation.Import

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(ForgeAutoConfiguration::class)
annotation class EnableForge
