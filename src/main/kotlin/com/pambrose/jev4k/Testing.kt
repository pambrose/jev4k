package com.pambrose.jev4k

import com.pambrose.jev4k.internal.JevJson
import com.pambrose.jev4k.internal.mapSystemOne
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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
 */
fun jevResult(
    body: JsonObject,
    questions: QuestionSet,
    model: String = JevDefaults.MODEL,
    requestId: String? = null,
): JevResult = mapSystemOne(body, model, questions, requestId, TEST_ENDPOINT)

/** The same, from the response body as JSON text. */
fun jevResult(
    body: String,
    questions: QuestionSet,
    model: String = JevDefaults.MODEL,
    requestId: String? = null,
): JevResult = jevResult(JevJson.parseToJsonElement(body).jsonObject, questions, model, requestId)

/**
 * The [JevApiException] subclass the client raises for HTTP [status], for testing a caller's error handling:
 * 429 gives a [JevRateLimitException], 401 a [JevAuthenticationException], and so on.
 *
 * ```
 * coEvery { jev.evaluate(any(), any(), any()) } throws jevApiException(429)
 * ```
 */
@JvmOverloads
fun jevApiException(
    status: Int,
    body: String? = null,
    requestId: String? = null,
    retryAfter: Duration? = null,
    headers: Map<String, List<String>> = emptyMap(),
): JevApiException = apiException(status, body, headers, requestId, TEST_ENDPOINT, retryAfter)
