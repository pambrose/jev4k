package com.pambrose.jev4k.internal

import com.pambrose.jev4k.BoxQuestion
import com.pambrose.jev4k.ChoiceQuestion
import com.pambrose.jev4k.GENERATED_REFERENCE
import com.pambrose.jev4k.JevImage
import com.pambrose.jev4k.QuestionSet
import com.pambrose.jev4k.sentEntries
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** The opt-in TypeSafe requires on any request with an image or a Box question. */
internal const val IMAGES_HEADER = "TypeSafe-Danger-Zone-Images"

/** The API's limit on all of a request's references together, in bytes of UTF-8. */
internal const val MAX_REFERENCE_BYTES_TOTAL = 65_536

private const val REQUEST_PART = "request"

private const val KEY_ONLY_HINT =
    "; it appears only as an object key, which the API never replaces: put it in a value, such as the option's " +
        "description"

private fun partName(index: Int): String = "image-$index"

/** True when the request needs [IMAGES_HEADER]: it carries an image or asks a Box question. */
internal fun needsImagesHeader(questions: QuestionSet): Boolean =
    questions.images.isNotEmpty() || questions.any { it.question is BoxQuestion }

/**
 * What stops this request being sent, as far as images go: an attached image no string references, a generated
 * reference placed but not attached, references over [MAX_REFERENCE_BYTES_TOTAL], a Box with no image, and an
 * `assets` field in [extraBody] on a request that attaches images. References are matched as the API matches them:
 * as plain text inside string values, never in object keys.
 */
internal fun imageRequestProblems(
    state: JsonElement,
    questions: QuestionSet,
    extraBody: Map<String, JsonElement>,
): List<String> {
    val images = questions.images
    val content = Content(listOf(state) + questions.flatMap { ref -> ref.question.sentEntries().map { it.second } })
    questions.forEach { ref -> (ref.question as? ChoiceQuestion)?.let { content.keys += it.options.keys } }
    return buildList {
        for (image in images) {
            if (content.strings.none { image.reference in it }) {
                val hint = if (image.reference in content.keys) KEY_ONLY_HINT else ""
                add(
                    "image '${image.reference}' is attached but appears in no string in the state, instructions " +
                        "or criteria$hint",
                )
            }
        }
        val attached = images.mapTo(mutableSetOf()) { it.reference }
        content.strings
            .flatMap { text -> GENERATED_REFERENCE.findAll(text).map { it.value } }
            .distinct()
            .filterNot { it in attached }
            .forEach { add("image '$it' is placed in the request but not attached; attach() it") }
        val total = images.sumOf { it.reference.encodeToByteArray().size }
        if (total > MAX_REFERENCE_BYTES_TOTAL) {
            add("image references total $total bytes of UTF-8, over the API's limit of $MAX_REFERENCE_BYTES_TOTAL")
        }
        if (images.isEmpty() && questions.any { it.question is BoxQuestion }) {
            add("a Box question needs an image attached to the request")
        }
        if (images.isNotEmpty() && "assets" in extraBody) {
            add("extraBody can't set 'assets' when the request attaches images")
        }
    }
}

/** The request's `assets` field: each image's reference, naming the part that carries it. */
internal fun assetsJson(images: List<JevImage>): JsonObject =
    buildJsonObject {
        images.forEachIndexed { index, image ->
            putJsonObject(image.reference) {
                put("type", "image")
                put("part", partName(index))
            }
        }
    }

/**
 * The multipart body for a request with images: [json] as the `request` part, then each image as its own part, as
 * both SDKs send it. Every part is a byte array, so a retry can write the body again.
 */
internal fun multipartBody(
    json: String,
    images: List<JevImage>,
): MultiPartFormDataContent =
    MultiPartFormDataContent(
        formData {
            append(
                REQUEST_PART,
                json,
                Headers.build { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) },
            )
            images.forEachIndexed { index, image ->
                // Validation rejected any image without a recognized format; requireNotNull, not checkNotNull, since a
                // bare IllegalStateException would count as a connection failure on Linux and Windows.
                val format = requireNotNull(image.format) { "image '${image.reference}' has no format" }
                append(
                    partName(index),
                    image.bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, format.contentType)
                        // curl's -F name=@file always sends a filename, so jev4k does too.
                        append(HttpHeaders.ContentDisposition, "filename=\"${partName(index)}.${format.extension}\"")
                    },
                )
            }
        },
    )

/** Every string value in [elements], and every object key, gathered iteratively so a deep tree can't overflow. */
private class Content(
    elements: List<JsonElement>,
) {
    val strings = mutableListOf<String>()
    val keys = mutableSetOf<String>()

    init {
        val pending = ArrayDeque(elements)
        while (pending.isNotEmpty()) {
            when (val element = pending.removeLast()) {
                is JsonObject -> {
                    keys += element.keys
                    pending.addAll(element.values)
                }

                is JsonArray -> {
                    pending.addAll(element)
                }

                is JsonPrimitive -> {
                    if (element.isString) strings += element.content
                }
            }
        }
    }
}
