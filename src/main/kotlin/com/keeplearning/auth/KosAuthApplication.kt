package com.keeplearning.auth

import io.github.cdimascio.dotenv.dotenv
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KosAuthApplication

fun main(args: Array<String>) {
	// Load environment variables from .env file
	val dotenv = dotenv {
		ignoreIfMalformed = true
		ignoreIfMissing = true
	}

	// Set system properties from .env file
	dotenv.entries().forEach { entry ->
		System.setProperty(entry.key, entry.value)
	}

	runApplication<KosAuthApplication>(*args)
}
