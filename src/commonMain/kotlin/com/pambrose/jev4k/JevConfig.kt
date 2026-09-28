package com.pambrose.jev4k

import com.pambrose.jev4k.internal.defaultEngine
import com.pambrose.jev4k.internal.platformGetenv
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.util.date.getTimeMillis
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

    /** [TIMEOUT] in milliseconds, for Java, which can't read a `Duration` constant. */
    const val TIMEOUT_MILLIS = 10_000L

    /** The default timeout for each HTTP attempt. */
    val TIMEOUT: Duration = TIMEOUT_MILLIS.milliseconds
}

/**
 * When and how failed requests are retried. The defaults match the official JS SDK: two retries, 0.5 s backoff
 * doubling to a 5 s cap with up to 25% subtracted as jitter, and server `retry-after-ms` / `Retry-After` hints
 * honored up to [maxRetryAfter][RetryPolicy.maxRetryAfter]. The Python SDK shares the retries, backoff and statuses
 * but doesn't cap hints, and adds a 30 s budget per call, which jev4k doesn't have: `timeout` applies per attempt.
 *
 * Java can't call the constructor or `copy`, whose `Duration` parameters Kotlin hides from it. It starts from
 * `new RetryPolicy()` and changes one setting at a time with the `with…` methods, which take milliseconds:
 * `new RetryPolicy().withMaxRetries(4).withInitialBackoffMillis(250)`.
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

    /** A copy with [maxRetries][RetryPolicy.maxRetries] changed. */
    fun withMaxRetries(maxRetries: Int): RetryPolicy = copy(maxRetries = maxRetries)

    /** A copy with [initialBackoff][RetryPolicy.initialBackoff] changed, in milliseconds. */
    fun withInitialBackoffMillis(millis: Long): RetryPolicy = copy(initialBackoff = millis.milliseconds)

    /** A copy with [maxBackoff][RetryPolicy.maxBackoff] changed, in milliseconds. */
    fun withMaxBackoffMillis(millis: Long): RetryPolicy = copy(maxBackoff = millis.milliseconds)

    /** A copy with [jitter][RetryPolicy.jitter] changed. */
    fun withJitter(jitter: Double): RetryPolicy = copy(jitter = jitter)

    /** A copy with [retryStatuses][RetryPolicy.retryStatuses] changed. */
    fun withRetryStatuses(retryStatuses: Set<Int>): RetryPolicy = copy(retryStatuses = retryStatuses)

    /** A copy with [respectRetryAfter][RetryPolicy.respectRetryAfter] changed. */
    fun withRespectRetryAfter(respectRetryAfter: Boolean): RetryPolicy = copy(respectRetryAfter = respectRetryAfter)

    /** A copy with [maxRetryAfter][RetryPolicy.maxRetryAfter] changed, in milliseconds. */
    fun withMaxRetryAfterMillis(millis: Long): RetryPolicy = copy(maxRetryAfter = millis.milliseconds)

    /** A copy with [retryOnConnectionError][RetryPolicy.retryOnConnectionError] changed. */
    fun withRetryOnConnectionError(retryOnConnectionError: Boolean): RetryPolicy =
        copy(retryOnConnectionError = retryOnConnectionError)

    /** A copy with [retryOnTimeout][RetryPolicy.retryOnTimeout] changed. */
    fun withRetryOnTimeout(retryOnTimeout: Boolean): RetryPolicy = copy(retryOnTimeout = retryOnTimeout)

    /**
     * A copy whose status set is a snapshot. The set may be the caller's mutable one, and the client re-reads it on
     * every response, so a later change to it would otherwise change a built client.
     */
    internal fun snapshot(): RetryPolicy = copy(retryStatuses = retryStatuses.toSet())

    companion object {
        /** Never retry. */
        val NONE = RetryPolicy(maxRetries = 0)
    }
}

/**
 * Builder for [JevConfig]. Each setting resolves as: explicit value, then environment variable, then default. String
 * settings are trimmed, so the trailing newline of a value read from a file is harmless, and a value that is blank
 * after trimming counts as unset. [build] reports every problem it finds at once, without echoing a secret.
 */
@JevDsl
class JevConfigBuilder {
    /** API key; falls back to `TYPESAFE_API_KEY`. Required. */
    var apiKey: String? = null

    /**
     * API root; falls back to `TYPESAFE_BASE_URL`, then `https://api.typesafe.ai`. It must be an absolute `https://`
     * URL (or `http://` to this machine) with a host and optionally a path, and no credentials, query or fragment.
     */
    var baseUrl: String? = null

    /** Model used when a call doesn't name one; falls back to `TYPESAFE_DEFAULT_MODEL`, then `jev-latest`. */
    var defaultModel: String? = null

    /** Timeout for each HTTP attempt. */
    var timeout: Duration = JevDefaults.TIMEOUT

    /** [timeout] in milliseconds, for Java, which can't call its `Duration` setter. */
    var timeoutMillis: Long
        get() = timeout.inWholeMilliseconds
        set(value) {
            timeout = value.milliseconds
        }

    var retry: RetryPolicy = RetryPolicy()

    /**
     * A Ktor engine to use instead of the platform default (CIO on the JVM, Darwin on Apple platforms, Curl on Linux,
     * WinHttp on Windows, Js on Node.js), e.g. a `MockEngine` in tests. The client does not close it.
     */
    var engine: HttpClientEngine? = null

    /** Extra headers sent with every request. */
    val headers: MutableMap<String, String> = linkedMapOf()

    /**
     * Allows a plain `http://` [baseUrl] on a host other than this machine, such as an Ollaya server elsewhere on the
     * network. Off by default: without TLS, the API key and every state cross the network in cleartext. A loopback
     * host (`localhost`, `127.x.x.x`, `::1`) never needs it.
     */
    var allowInsecureHttp: Boolean = false

    internal var env: (String) -> String? = ::platformGetenv
    internal var retryDelay: suspend (Long) -> Unit = { delay(it.milliseconds) }
    internal var random: Random = Random.Default

    /** The current time in epoch milliseconds, for reading a `Retry-After` date. */
    internal var now: () -> Long = ::getTimeMillis

    fun build(): JevConfig {
        fun fromEnv(name: String) = env(name).setting()

        val key = apiKey.setting() ?: fromEnv(JevDefaults.API_KEY_ENV)
            ?: throw JevConfigException(
                "No TypeSafe API key: set apiKey or the ${JevDefaults.API_KEY_ENV} environment variable",
            )
        val url = (baseUrl.setting() ?: fromEnv(JevDefaults.BASE_URL_ENV) ?: JevDefaults.BASE_URL).trimEnd('/')

        // Checked here, not left to Ktor: Ktor validates them on every request, so a bad value would build a client
        // that fails every call with an exception that isn't a JevException, and its message echoes the value.
        val problems = buildList {
            timeoutProblem(timeout)?.let(::add)
            addAll(baseUrlProblems(url, allowInsecureHttp))
            val badChar = key.indexOfFirst { it.isControlCharacter() }
            if (badChar >= 0) add("apiKey contains a control character at index $badChar")
            headers.forEach { (name, value) -> headerProblem(name, value)?.let(::add) }
        }
        if (problems.isNotEmpty()) throw JevConfigException("Invalid configuration: ${problems.joinToString("; ")}")

        return JevConfig(
            apiKey = key,
            baseUrl = url,
            defaultModel = defaultModel.setting() ?: fromEnv(JevDefaults.DEFAULT_MODEL_ENV) ?: JevDefaults.MODEL,
            timeout = timeout,
            retry = retry.snapshot(),
            engine = engine,
            headers = headers.toMap(),
            retryDelay = retryDelay,
            random = random,
            now = now,
        )
    }
}

/**
 * What is wrong with a timeout. The client truncates it with inWholeMilliseconds, so anything under a millisecond
 * would reach Ktor as 0, which HttpTimeout rejects outright.
 */
internal fun timeoutProblem(timeout: Duration): String? =
    "timeout must be at least 1 millisecond (was $timeout)".takeIf { timeout.inWholeMilliseconds < 1 }

/** A string setting as given, trimmed, or null when it is unset or blank. A per-call model follows the same rule. */
internal fun String?.setting(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun Char.isControlCharacter(): Boolean = this < ' ' || this == '\u007F'

/**
 * What is wrong with [url] as a base URL. It is parsed here once, so a value Ktor would reject on every request is a
 * configuration error instead. Messages quote the URL only when it can't hold a secret.
 */
private fun baseUrlProblems(
    url: String,
    allowInsecureHttp: Boolean,
): List<String> {
    val quoted = url.takeUnless { '@' in it || '?' in it || '#' in it }?.let { " (was '$it')" }.orEmpty()
    // Ktor reads a scheme-less value as a relative path, which would turn into a puzzling connection error much
    // later instead of a configuration error here.
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        return listOf("baseUrl must start with http:// or https://$quoted")
    }
    // Ktor's own message (for a bad port, say) would quote the whole URL, so only the fact is reported.
    val parsed = runCatching { Url("$url/") }.getOrNull() ?: return listOf("baseUrl is not a valid URL$quoted")
    return buildList {
        if (parsed.host.isEmpty()) add("baseUrl has no host$quoted")
        if (parsed.user != null || parsed.password != null) {
            add("baseUrl must not carry credentials; send what a gateway needs in headers instead")
        }
        if (!parsed.parameters.isEmpty() || parsed.trailingQuery || parsed.fragment.isNotEmpty()) {
            add("baseUrl must not have a query or a fragment")
        }
        if (parsed.protocol.name == "http" && !allowInsecureHttp && !parsed.host.isLoopback()) {
            add(
                "baseUrl uses plain http:// for '${parsed.host}', so the API key would be sent unencrypted; use " +
                    "https://, or set allowInsecureHttp for a trusted network (localhost never needs it)",
            )
        }
    }
}

private val LOOPBACK_IPV4 = Regex("""127\.\d{1,3}\.\d{1,3}\.\d{1,3}""")

private fun String.isLoopback(): Boolean {
    val host = lowercase().removePrefix("[").removeSuffix("]")
    return host == "localhost" ||
        host.endsWith(".localhost") ||
        host == "::1" ||
        host == "0:0:0:0:0:0:0:1" ||
        LOOPBACK_IPV4.matches(host)
}

/** What is wrong with a configured header, by Ktor's rules, without echoing its value, which may be a secret. */
internal fun headerProblem(
    name: String,
    value: String,
): String? {
    val badName = runCatching { HttpHeaders.checkHeaderName(name) }.isFailure
    val badValue = runCatching { HttpHeaders.checkHeaderValue(value) }.isFailure
    return when {
        badName -> "header name '${name.escapeControls()}' contains a character HTTP doesn't allow in a name"
        badValue -> "header '$name' has a control character in its value"
        else -> null
    }
}

/** A header name made printable for a message: each control character becomes a `\uXXXX` escape. */
private fun String.escapeControls(): String =
    buildString {
        for (c in this@escapeControls) {
            if (c.isControlCharacter()) append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
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
    internal val now: () -> Long,
) {
    override fun toString(): String =
        "JevConfig(apiKey=***, baseUrl=$baseUrl, defaultModel=$defaultModel, timeout=$timeout, retry=$retry, " +
            "engine=${(engine ?: defaultEngine)::class.simpleName}, headers=${headers.keys})"
}
