package com.pambrose.jev4k

import com.pambrose.jev4k.internal.ResponseInfo
import com.pambrose.jev4k.internal.mapSystemOne
import com.pambrose.jev4k.internal.parseObject
import com.pambrose.jev4k.internal.retryHint
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.JsonObject
import kotlin.jvm.JvmOverloads
import kotlin.time.Duration

// Builders for the values a JevApi hands back, so application code can be tested against a fake or a mock
// without going near HTTP. They take the same input the client does, a response body, and run it through the
// same mapping, so a result built here behaves exactly like one that arrived over the wire.

private const val TEST_ENDPOINT = "POST (test)"

/**
 * A [JevResult] built from a raw response [body], as [JevApi.evaluate] would have returned it for [questions].
 *
 * ```
 * val jev = mockk<JevApi>()
 * coEvery { jev.evaluate(any(), any(), any()) } returns
 *     jevResult("""{"answers":{"urgent":{"type":"noul","noul":0.92}}}""", Triage.questions)
 * ```
 *
 * The body is validated exactly as a real one is, so a malformed answer raises
 * [JevResponseValidationException] here too. Recording a real response and replaying it is a good way to keep a
 * fixture honest.
 *
 * A result answers only the handles of the set it was built for. Code that builds its questions on each call (inline
 * handles, or a `JevQuery` class) sends a new set every time, so build the result from the one the mock received:
 *
 * ```
 * coEvery { jev.evaluate(any(), any(), any()) } answers { jevResult(body, secondArg()) }
 * ```
 */
@JvmOverloads
fun jevResult(
    body: JsonObject,
    questions: QuestionSet,
    model: String = JevDefaults.MODEL,
    requestId: String? = null,
): JevResult = mapSystemOne(body, model, questions, testResponse(body.toString(), requestId))

/**
 * The same, from the response body as JSON text. Text that isn't JSON, or isn't a JSON object, raises
 * [JevResponseValidationException], as it does from the client, so a garbled response can be simulated too.
 */
@JvmOverloads
fun jevResult(
    body: String,
    questions: QuestionSet,
    model: String = JevDefaults.MODEL,
    requestId: String? = null,
): JevResult {
    val response = testResponse(body, requestId)
    return mapSystemOne(response.parseObject(), model, questions, response)
}

private fun testResponse(
    text: String,
    requestId: String?,
) = ResponseInfo(text, HttpStatusCode.OK.value, Headers.Empty, requestId, TEST_ENDPOINT)

/**
 * The [JevApiException] subclass the client raises for HTTP [status], for testing a caller's error handling:
 * 429 gives a [JevRateLimitException], 401 a [JevAuthenticationException], and so on.
 *
 * ```
 * coEvery { jev.evaluate(any(), any(), any()) } throws jevApiException(429, retryAfter = 2.seconds)
 * ```
 *
 * A rate-limit error's hint is [retryAfter] when given, and otherwise read from a `retry-after-ms` or `Retry-After`
 * entry in [headers], as the client reads it. Java, which can't pass a `Duration`, sets it through [headers].
 */
@JvmOverloads
fun jevApiException(
    status: Int,
    body: String? = null,
    requestId: String? = null,
    headers: Map<String, List<String>> = emptyMap(),
    retryAfter: Duration? = null,
): JevApiException {
    val hint = retryAfter ?: retryHint(headersOf(*headers.toList().toTypedArray()))
    return apiException(status, body, headers, requestId, TEST_ENDPOINT, hint)
}
