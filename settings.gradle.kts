plugins {
    id("com.github.burrunan.s3-build-cache") version "1.9.5"
}

rootProject.name = "auth"

include("keycloak-spi")

// S3 Build Cache Configuration
// Set these environment variables in .env or shell:
//   S3_BUILD_CACHE_BUCKET - bucket name (required for remote cache)
//   S3_BUILD_CACHE_REGION - AWS region (default: us-east-1)
//   S3_BUILD_CACHE_ENDPOINT - for S3-compatible storage (MinIO, R2, etc.)
//   S3_BUILD_CACHE_ACCESS_KEY_ID - access key
//   S3_BUILD_CACHE_SECRET_KEY - secret key
val s3Bucket = System.getenv("S3_BUILD_CACHE_BUCKET")
val s3Region = System.getenv("S3_BUILD_CACHE_REGION") ?: "us-east-1"
val s3Endpoint = System.getenv("S3_BUILD_CACHE_ENDPOINT")

buildCache {
    local {
        isEnabled = true
    }
    if (!s3Bucket.isNullOrBlank()) {
        remote<com.github.burrunan.s3cache.AwsS3BuildCache> {
            region = s3Region
            bucket = s3Bucket
            prefix = "gradle-cache/"
            isPush = true  // Both local and CI can push/pull
            isEnabled = true
            isReducedRedundancy = false  // R2 doesn't support REDUCED_REDUNDANCY
            if (!s3Endpoint.isNullOrBlank()) {
                endpoint = s3Endpoint
                forcePathStyle = true  // Required for R2 and most S3-compatible storage
            }
        }
    }
}