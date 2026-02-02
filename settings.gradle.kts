plugins {
    // Using 1.8.2 instead of 1.9.5 for R2 compatibility
    // Newer versions use AWS SDK 2.40+ which adds checksum headers R2 doesn't support
    // See: https://community.cloudflare.com/t/aws-sdk-client-s3-v3-729-0-breaks-uploadpart-and-putobject-r2-s3-api-compatibility/758637
    id("com.github.burrunan.s3-build-cache") version "1.8.2"
}

rootProject.name = "auth"

include("keycloak-spi")

// S3 Build Cache Configuration
// Set these environment variables in .env or shell:
//   S3_BUILD_CACHE_BUCKET - bucket name (required for remote cache)
//   S3_BUILD_CACHE_REGION - AWS region (use "auto" for R2, or "us-east-1")
//   S3_BUILD_CACHE_ENDPOINT - for S3-compatible storage (MinIO, R2, etc.)
//   AWS_ACCESS_KEY_ID or S3_BUILD_CACHE_ACCESS_KEY_ID - access key
//   AWS_SECRET_ACCESS_KEY or S3_BUILD_CACHE_SECRET_KEY - secret key
val s3Bucket: String? = System.getenv("S3_BUILD_CACHE_BUCKET")
val s3Region: String = System.getenv("S3_BUILD_CACHE_REGION") ?: "auto"
val s3Endpoint: String? = System.getenv("S3_BUILD_CACHE_ENDPOINT")
// Support both AWS standard names and custom S3_BUILD_CACHE_* names
val s3AccessKey: String? = System.getenv("AWS_ACCESS_KEY_ID")
    ?: System.getenv("S3_BUILD_CACHE_ACCESS_KEY_ID")
val s3SecretKey: String? = System.getenv("AWS_SECRET_ACCESS_KEY")
    ?: System.getenv("S3_BUILD_CACHE_SECRET_KEY")

buildCache {
    local {
        isEnabled = true
    }
    if (!s3Bucket.isNullOrBlank() && !s3AccessKey.isNullOrBlank() && !s3SecretKey.isNullOrBlank()) {
        remote<com.github.burrunan.s3cache.AwsS3BuildCache> {
            region = s3Region
            bucket = s3Bucket
            prefix = "gradle-cache/"
            isPush = true
            isEnabled = true
            awsAccessKeyId = s3AccessKey
            awsSecretKey = s3SecretKey
            isReducedRedundancy = false  // R2 doesn't support REDUCED_REDUNDANCY
            if (!s3Endpoint.isNullOrBlank()) {
                endpoint = s3Endpoint
                forcePathStyle = true  // Required for R2 and most S3-compatible storage
            }
        }
    }
}