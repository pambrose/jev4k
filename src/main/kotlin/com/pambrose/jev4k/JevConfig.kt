package com.pambrose.jev4k

import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Environment variable names, defaults, and header names shared with the official TypeSafe SDKs. */
object JevDefaults {
    const val API_KEY_ENV = "TYPESAFE_API_KEY"
    const val BASE_URL_ENV = "TYPESAFE_BASE_URL"
    const val DEFAULT_MODEL_ENV = "TYPESAFE_DEFAULT_MODEL"
    const val BASE_URL = "https://api.typesafe.ai"
    const val MODEL = "jev-latest"
    const val REQUEST_ID_HEADER = "x-typesafe-request-id"
    const val RETRY_AFTER_MS_HEADER = "retry-after-ms"
    val TIMEOUT: Duration = 10.seconds
}

/**
 * When and how failed requests are retried. The defaults match the official Python and JS SDKs:
 * two retries, 0.5 s backoff doubling to a 5 s cap with up to 25% subtracted as jitter, and
 * server `retry-after-ms` / `Retry-After` hints honored up to [maxRetryAfter][RetryPolicy.maxRetryAfter].
 */
data class RetryPolicy(
    val maxRetries: Int = 2,
    val initialBackoff: Duration = 500.milliseconds,
    val maxBackoff: Duration = 5.seconds,
    val jitter: Double = 0.25,
    val retryStatuses: Set<Int> = setOf(408, 429) + (500..599),
    val respectRetryAfter: Boolean = true,
    val maxRetryAfter: Duration = 60.seconds,
    val retryOnConnectionError: Boolean = true,
    val retryOnTimeout: Boolean = true,
) {
    init {
        val problems = buildList {
            if (maxRetries < 0) add("maxRetries must be >= 0 (was $maxRetries)")
            if (initialBackoff.isNegative()) add("initialBackoff must not be negative")
            if (maxBackoff < initialBackoff) add("maxBackoff ($maxBackoff) must be >= initialBackoff ($initialBackoff)")
            if (jitter !in 0.0..1.0) add("jitter must be within 0.0..1.0 (was $jitter)")
            if (maxRetryAfter.isNegative()) add("maxRetryAfter must not be negative")
        }
        if (problems.isNotEmpty()) throw JevConfigException("Invalid RetryPolicy: ${problems.joinToString("; ")}")
    }

    /**
     * The delay before retry number [retryNumber] (1-based): `min(initial * 2^(n-1), max)`, reduced by
     * `jitter * random` of itself. [random] is a value in `0.0..1.0`.
     */
    fun backoff(
        retryNumber: Int,
        random: Double,
    ): Duration {
        val exponent = (retryNumber - 1).coerceIn(0, 30)
        val base = (initialBackoff * (1L shl exponent).toDouble()).coerceAtMost(maxBackoff)
        return base * (1.0 - jitter * random)
    }

    companion object {
        /** Never retry. */
        val NONE = RetryPolicy(maxRetries = 0)
    }
}

/** Builder for [JevConfig]. Each setting resolves as: explicit value, then environment variable, then default. */
@JevDsl
class JevConfigBuilder {
    /** API key; falls back to `TYPESAFE_API_KEY`. Required. */
    var apiKey: String? = null

    /** API root; falls back to `TYPESAFE_BASE_URL`, then `https://api.typesafe.ai`. */
    var baseUrl: String? = null

    /** Model used when a call doesn't name one; falls back to `TYPESAFE_DEFAULT_MODEL`, then `jev-latest`. */
    var defaultModel: String? = null

    /** Timeout for each HTTP attempt. */
    var timeout: Duration = JevDefaults.TIMEOUT

    var retry: RetryPolicy = RetryPolicy()

    /** A Ktor engine to use instead of CIO, e.g. a `MockEngine` in tests. The client does not close it. */
    var engine: HttpClientEngine? = null

    /** Extra headers sent with every request. */
    val headers: MutableMap<String, String> = linkedMapOf()

    internal var env: (String) -> String? = System::getenv
    internal var retryDelay: suspend (Long) -> Unit = { delay(it.milliseconds) }
    internal var random: Random = Random.Default

    fun build(): JevConfig {
        fun fromEnv(name: String) = env(name)?.takeIf { it.isNotBlank() }

        val key = apiKey?.takeIf { it.isNotBlank() } ?: fromEnv(JevDefaults.API_KEY_ENV)
        ?: throw JevConfigException(
            "No TypeSafe API key: set apiKey or the ${JevDefaults.API_KEY_ENV} environment variable",
        )
        // A blank explicit value is ignored exactly like a blank environment value.
        val url = (baseUrl?.takeIf { it.isNotBlank() } ?: fromEnv(JevDefaults.BASE_URL_ENV) ?: JevDefaults.BASE_URL)
            .trimEnd('/')

        val problems = buildList {
            // The factory truncates with inWholeMilliseconds, so anything under a millisecond reaches Ktor as 0,
            // which means "no timeout" to CIO and is rejected outright by HttpTimeout.
            if (timeout.inWholeMilliseconds < 1) add("timeout must be at least 1 millisecond (was $timeout)")
            // Ktor reads a scheme-less value as a relative path, which turns into a puzzling connection error
            // much later instead of a configuration error here.
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                add("baseUrl must start with http:// or https:// (was '$url')")
            }
        }
        if (problems.isNotEmpty()) throw JevConfigException("Invalid configuration: ${problems.joinToString("; ")}")

        return JevConfig(
            apiKey = key,
            baseUrl = url,
            defaultModel = defaultModel?.takeIf { it.isNotBlank() }
                ?: fromEnv(JevDefaults.DEFAULT_MODEL_ENV) ?: JevDefaults.MODEL,
            timeout = timeout,
            retry = retry,
            engine = engine,
            headers = headers.toMap(),
            retryDelay = retryDelay,
            random = random,
        )
    }
}

/** Resolved client settings. Build one with [JevConfigBuilder] or the `JevClient { }` constructor. */
class JevConfig internal constructor(
    val apiKey: String,
    val baseUrl: String,
    val defaultModel: String,
    val timeout: Duration,
    val retry: RetryPolicy,
    val engine: HttpClientEngine?,
    val headers: Map<String, String>,
    internal val retryDelay: suspend (Long) -> Unit,
    internal val random: Random,
) {
    override fun toString(): String =
        "JevConfig(apiKey=***, baseUrl=$baseUrl, defaultModel=$defaultModel, timeout=$timeout, retry=$retry, " +
            "engine=${engine?.let { it::class.simpleName } ?: "CIO"}, headers=${headers.keys})"
}
