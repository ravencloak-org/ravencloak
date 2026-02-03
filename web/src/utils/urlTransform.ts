/**
 * URL transformation utilities for Keycloak redirect URIs and web origins.
 */

/**
 * Checks if a URL string is localhost or 127.0.0.1
 */
function isLocalhost(url: string): boolean {
  return url.includes('localhost') || url.includes('127.0.0.1')
}

/**
 * Checks if the URL already has a scheme (http:// or https://)
 */
function hasScheme(url: string): boolean {
  return /^https?:\/\//i.test(url)
}

/**
 * Checks if the URL already has a wildcard path
 */
function hasWildcard(url: string): boolean {
  return url.includes('/*') || url.includes('*')
}

/**
 * Checks if the URL has a specific path (not just root)
 */
function hasSpecificPath(url: string): boolean {
  try {
    const urlObj = new URL(url)
    return urlObj.pathname.length > 1 && urlObj.pathname !== '/'
  } catch {
    // If we can't parse as URL, check for common path patterns
    const pathMatch = url.match(/(?:\/\/[^\/]+)?(\/.+)/)
    return pathMatch !== null && pathMatch[1] !== '/'
  }
}

/**
 * Transforms a user-entered URL to Keycloak redirect URI format.
 *
 * Rules:
 * - localhost:5173 → http://localhost:5173/*
 * - example.com → https://example.com/*
 * - http://example.com → http://example.com/*
 * - https://example.com/callback → keep as-is (already specific)
 */
export function transformToRedirectUri(input: string): string {
  const trimmed = input.trim()
  if (!trimmed) return trimmed

  // If it already has a wildcard, leave it alone
  if (hasWildcard(trimmed)) {
    // Just ensure it has a scheme
    if (!hasScheme(trimmed)) {
      const scheme = isLocalhost(trimmed) ? 'http://' : 'https://'
      return scheme + trimmed
    }
    return trimmed
  }

  // If it has a specific path (like /callback), keep it as-is but add scheme if needed
  if (hasScheme(trimmed) && hasSpecificPath(trimmed)) {
    return trimmed
  }

  // Add scheme if missing
  let url = trimmed
  if (!hasScheme(url)) {
    const scheme = isLocalhost(url) ? 'http://' : 'https://'
    url = scheme + url
  }

  // Remove trailing slash before adding wildcard
  url = url.replace(/\/+$/, '')

  // Add wildcard for redirect URIs
  return url + '/*'
}

/**
 * Transforms a user-entered URL to Keycloak web origin format.
 *
 * Rules:
 * - localhost:5173 → http://localhost:5173
 * - example.com → https://example.com
 * - Strip trailing paths/wildcards
 */
export function transformToWebOrigin(input: string): string {
  const trimmed = input.trim()
  if (!trimmed) return trimmed

  // Special case: + means "allow all redirect URI origins"
  if (trimmed === '+') return trimmed

  // Add scheme if missing
  let url = trimmed
  if (!hasScheme(url)) {
    const scheme = isLocalhost(url) ? 'http://' : 'https://'
    url = scheme + url
  }

  // Parse URL to get just the origin
  try {
    const urlObj = new URL(url)
    return urlObj.origin
  } catch {
    // If parsing fails, strip path manually
    const match = url.match(/^(https?:\/\/[^\/]+)/)
    return match && match[1] ? match[1] : url
  }
}

/**
 * Result from a URL transformation that tracks if the value was auto-transformed.
 */
export interface TransformResult {
  value: string
  wasTransformed: boolean
  originalValue: string
}

/**
 * Transforms a redirect URI with tracking of whether it was modified.
 */
export function transformRedirectUriWithTracking(input: string): TransformResult {
  const transformed = transformToRedirectUri(input)
  return {
    value: transformed,
    wasTransformed: transformed !== input,
    originalValue: input
  }
}

/**
 * Transforms a web origin with tracking of whether it was modified.
 */
export function transformWebOriginWithTracking(input: string): TransformResult {
  const transformed = transformToWebOrigin(input)
  return {
    value: transformed,
    wasTransformed: transformed !== input,
    originalValue: input
  }
}

/**
 * Transforms an array of redirect URIs.
 */
export function transformRedirectUris(uris: string[]): string[] {
  return uris.map(transformToRedirectUri).filter((uri): uri is string => Boolean(uri))
}

/**
 * Transforms an array of web origins.
 */
export function transformWebOrigins(origins: string[]): string[] {
  return origins.map(transformToWebOrigin).filter((origin): origin is string => Boolean(origin))
}
