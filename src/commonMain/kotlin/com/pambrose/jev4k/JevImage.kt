package com.pambrose.jev4k

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.io.encoding.Base64
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Marks jev4k's support for TypeSafe's images and Box questions. TypeSafe calls both "Dreamer-ready": the request
 * format, the answers and the limits can change between its releases, so this API can change in any jev4k release.
 * Opting in also stands for what TypeSafe's `TypeSafe-Danger-Zone-Images: true` header does: jev4k sends that header
 * with every request that carries an image or a Box question.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "TypeSafe's images and Box questions are experimental; this API can change in any jev4k release",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
annotation class ExperimentalJevImages

/**
 * A PNG or JPEG image to send with a request.
 *
 * Place it anywhere content goes, as its [reference][JevImage.reference]: in a string template
 * (`"Is $photo damaged?"`), through `jsonOf` or `entry`, as a `@Serializable` field, or with the builders' image
 * overloads (`"thumbs_up" means thumb`). The API replaces every occurrence of the reference with the image. Placing an
 * image doesn't send it: [attach][QueryBuilder.attach] it to the request, or attach it to a [JevQuery], so its bytes
 * travel with the questions.
 *
 * The factories never throw for bad content. Empty bytes, invalid base64, or bytes that are neither a PNG nor a JPEG
 * are reported, with every other problem, when the [QuestionSet] the image is attached to is validated. Only [read]
 * throws, for a file it can't read. The API's size and pixel limits are left to the API.
 *
 * @property reference The text that stands for the image. A generated one is `jev4k-image-` and a random UUID. One you
 *   choose should be as unlikely to occur in your text, since the API matches it anywhere inside a string.
 */
@ExperimentalJevImages
@Serializable(with = JevImageSerializer::class)
class JevImage private constructor(
    val reference: String,
    internal val bytes: ByteArray,
    internal val format: ImageFormat?,
    internal val problem: String?,
) {
    override fun toString(): String = reference

    companion object {
        // Padding optional: the bytes are decoded here and sent as they are, so a missing '=' is harmless.
        private val LenientBase64 = Base64.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

        /** An image from its file's bytes, which are copied. A null [reference] generates one. */
        @JvmStatic
        @JvmOverloads
        fun of(
            bytes: ByteArray,
            reference: String? = null,
        ): JevImage = create(bytes.copyOf(), reference, null)

        /** An image from base64 text (standard alphabet). It is decoded now and sent as bytes. */
        @JvmStatic
        @JvmOverloads
        fun fromBase64(
            text: String,
            reference: String? = null,
        ): JevImage {
            val decoded =
                try {
                    LenientBase64.decode(text)
                } catch (ignored: IllegalArgumentException) {
                    null
                }
            return create(decoded ?: ByteArray(0), reference, if (decoded == null) BAD_BASE64 else null)
        }

        /** An image read now from the file at [path], on any platform with a file system (Node.js included). */
        @JvmStatic
        @JvmOverloads
        fun read(
            path: Path,
            reference: String? = null,
        ): JevImage = ofOwned(SystemFileSystem.source(path).buffered().use { it.readByteArray() }, reference)

        /** An image from bytes nothing else holds, such as a file just read, so they needn't be copied. */
        internal fun ofOwned(
            bytes: ByteArray,
            reference: String?,
        ): JevImage = create(bytes, reference, null)

        private fun create(
            bytes: ByteArray,
            reference: String?,
            problem: String?,
        ): JevImage {
            val format = ImageFormat.of(bytes)
            val found =
                problem ?: when {
                    bytes.isEmpty() -> "is empty"
                    format == null -> NOT_AN_IMAGE
                    else -> null
                }
            return JevImage(reference ?: generatedReference(), bytes, format, found)
        }
    }
}

/** The two formats the API takes, recognized by their signatures. */
internal enum class ImageFormat(
    val contentType: String,
    val extension: String,
    private val signature: ByteArray,
) {
    PNG("image/png", "png", byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
    JPEG("image/jpeg", "jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())),
    ;

    private fun matches(bytes: ByteArray): Boolean =
        bytes.size >= signature.size && signature.indices.all { bytes[it] == signature[it] }

    companion object {
        fun of(bytes: ByteArray): ImageFormat? = entries.firstOrNull { it.matches(bytes) }
    }
}

/** Writes an image as its reference, so a `@Serializable` field of type [JevImage] places it. */
internal object JevImageSerializer : KSerializer<JevImage> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.pambrose.jev4k.JevImage", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: JevImage,
    ) {
        encoder.encodeString(value.reference)
    }

    override fun deserialize(decoder: Decoder): JevImage =
        throw SerializationException("A JevImage can't be decoded: its reference doesn't hold the image")
}

private const val GENERATED_PREFIX = "jev4k-image-"

/** A reference jev4k generated, wherever it appears in text. */
internal val GENERATED_REFERENCE =
    Regex("$GENERATED_PREFIX[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

/** The API's limit on one reference, in bytes of UTF-8. */
internal const val MAX_REFERENCE_BYTES = 1024

private const val REFERENCE_PREVIEW = 40

private const val NOT_AN_IMAGE = "is neither a PNG nor a JPEG; the API takes only those two"

private const val BAD_BASE64 =
    "is not valid base64: use the standard alphabet, with no whitespace and no data: prefix"

@OptIn(ExperimentalUuidApi::class)
private fun generatedReference(): String = GENERATED_PREFIX + Uuid.random()

/**
 * How a problem names this image: by its reference, plus its format and size when jev4k generated the reference, since
 * a random UUID alone doesn't tell the caller which of their images is meant.
 */
internal val JevImage.label: String
    get() =
        if (GENERATED_REFERENCE.matches(reference)) {
            "image '$reference' (${listOfNotNull(format?.name, "${bytes.size} bytes").joinToString(", ")})"
        } else {
            "image '$reference'"
        }

/**
 * What is wrong with [images] on their own, before any request: each one's carried problem, a blank or overlong
 * reference, and two different images sharing a reference. [images] holds each instance once.
 */
internal fun imageDefinitionProblems(images: List<JevImage>): List<String> =
    buildList {
        for (image in images) {
            val reference = image.reference
            when {
                reference.isBlank() -> {
                    add("image references must not be blank")
                }

                reference.encodeToByteArray().size > MAX_REFERENCE_BYTES -> {
                    val preview = reference.take(REFERENCE_PREVIEW)
                    add("image reference '$preview…' is longer than $MAX_REFERENCE_BYTES bytes of UTF-8")
                }
            }
            image.problem?.let { add("${image.label} $it") }
        }
        images
            .groupBy { it.reference }
            .filterValues { it.size > 1 }
            .keys
            .forEach { add("two different images share the reference '$it'") }
    }
