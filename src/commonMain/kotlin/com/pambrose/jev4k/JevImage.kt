package com.pambrose.jev4k

import kotlinx.io.Source
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
 * The factories never throw for bad content. Empty bytes, invalid base64, bytes that are neither a PNG nor a JPEG, or
 * more than the API's 16 MiB per image are reported, with every other problem, when the [QuestionSet] the image is
 * attached to is validated. Only [read] throws, for a file it can't read, and it reads no further than that limit. The
 * API's pixel limits are left to the API.
 *
 * @property reference The text that stands for the image. A generated one is `jev4k-image-` and a UUID whose first
 *   group is the same for every image this process generates, so jev4k can tell its own references from text that
 *   only looks like one. One you choose should be as unlikely to occur in your text, since the API matches it anywhere
 *   inside a string, and mustn't occur inside another image's reference.
 */
@ExperimentalJevImages
@Serializable(with = JevImageSerializer::class)
class JevImage private constructor(
    val reference: String,
    internal val bytes: ByteArray,
    internal val format: ImageFormat?,
    internal val problem: String?,
    /** The size a problem names: what [bytes] holds, or a file's size when it was too large to read. */
    internal val sizeLabel: String?,
) {
    override fun toString(): String = reference

    companion object {
        // Padding optional and whitespace dropped: the bytes are decoded here and sent as they are, so neither a
        // missing '=' nor a line break matters.
        private val LenientBase64 = Base64.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

        /** An image from its file's bytes, which are copied. A null [reference] generates one. */
        @JvmStatic
        @JvmOverloads
        fun of(
            bytes: ByteArray,
            reference: String? = null,
        ): JevImage = create(bytes.copyOf(), reference, null)

        /**
         * An image from base64 text (standard alphabet). It is decoded now and sent as bytes. Whitespace, such as the
         * line breaks the `base64` tool writes, is ignored.
         */
        @JvmStatic
        @JvmOverloads
        fun fromBase64(
            text: String,
            reference: String? = null,
        ): JevImage {
            val decoded =
                try {
                    LenientBase64.decode(text.filterNot { it in BASE64_WHITESPACE })
                } catch (ignored: IllegalArgumentException) {
                    null
                }
            return create(decoded ?: ByteArray(0), reference, if (decoded == null) BAD_BASE64 else null)
        }

        /**
         * An image read now from the file at [path], on any platform with a file system (Node.js included). A file over
         * the API's 16 MiB limit isn't read: the image carries that problem instead.
         */
        @JvmStatic
        @JvmOverloads
        fun read(
            path: Path,
            reference: String? = null,
        ): JevImage {
            val size = SystemFileSystem.metadataOrNull(path)?.takeIf { it.isRegularFile }?.size
            return if (size != null && size > MAX_IMAGE_BYTES) {
                tooLarge(size, reference)
            } else {
                SystemFileSystem.source(path).buffered().use { readCapped(it, reference) }
            }
        }

        /**
         * An image read from [source] to its end, unless it holds more than the API's limit, so a file whose size isn't
         * known in advance (a device or a pipe) can't fill memory.
         */
        internal fun readCapped(
            source: Source,
            reference: String?,
        ): JevImage =
            if (source.request(MAX_IMAGE_BYTES + 1L)) {
                tooLarge(null, reference)
            } else {
                ofOwned(source.readByteArray(), reference)
            }

        /** A file over the API's limit, read no further: no bytes, its [size] when known, and the problem. */
        internal fun tooLarge(
            size: Long?,
            reference: String?,
        ): JevImage = create(ByteArray(0), reference, TOO_LARGE, size?.let { "$it bytes" })

        /** An image from bytes nothing else holds, such as a file just read, so they needn't be copied. */
        internal fun ofOwned(
            bytes: ByteArray,
            reference: String?,
        ): JevImage = create(bytes, reference, null)

        private fun create(
            bytes: ByteArray,
            reference: String?,
            problem: String?,
            sizeLabel: String? = "${bytes.size} bytes",
        ): JevImage {
            val format = ImageFormat.of(bytes)
            val found =
                problem ?: when {
                    bytes.isEmpty() -> "is empty"
                    bytes.size > MAX_IMAGE_BYTES -> TOO_LARGE
                    format == null -> NOT_AN_IMAGE
                    else -> null
                }
            return JevImage(reference ?: generatedReference(), bytes, format, found, sizeLabel)
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

/**
 * The first group of every reference this process generates. It is random per process, so text that only looks like a
 * generated reference, such as a state stored by an earlier run, isn't taken for an image that wasn't attached.
 */
@OptIn(ExperimentalUuidApi::class)
private val PROCESS_TAG: String = Uuid.random().toString().substringBefore('-')

/** A reference this process generated, wherever it appears in text. */
internal val GENERATED_REFERENCE =
    Regex("$GENERATED_PREFIX$PROCESS_TAG-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

/** The API's limit on one image, in bytes after base64 decoding. */
internal const val MAX_IMAGE_BYTES = 16 * 1024 * 1024

/** The API's limit on one reference, in bytes of UTF-8. */
internal const val MAX_REFERENCE_BYTES = 1024

private const val REFERENCE_PREVIEW = 40

private const val NOT_AN_IMAGE = "is neither a PNG nor a JPEG; the API takes only those two"

private const val TOO_LARGE = "is over the API's limit of 16 MiB per image"

private const val BAD_BASE64 = "is not valid base64: use the standard alphabet, with no data: prefix"

/** What [JevImage.fromBase64] drops before decoding: ASCII whitespace. */
private const val BASE64_WHITESPACE = " \t\n\r\u000C"

@OptIn(ExperimentalUuidApi::class)
private fun generatedReference(): String =
    GENERATED_PREFIX + PROCESS_TAG + Uuid.random().toString().dropWhile { it != '-' }

/**
 * How a problem names this image: by its reference, plus its format and size when jev4k generated the reference, since
 * a random UUID alone doesn't tell the caller which of their images is meant.
 */
internal val JevImage.label: String
    get() {
        val details = listOfNotNull(format?.name, sizeLabel)
        return if (GENERATED_REFERENCE.matches(reference) && details.isNotEmpty()) {
            "image '$reference' (${details.joinToString(", ")})"
        } else {
            "image '$reference'"
        }
    }

/**
 * [images] with each image once: an instance attached twice, or a second instance with the same reference and the same
 * bytes, such as a picture read again by a helper, is kept only the first time.
 */
internal fun List<JevImage>.distinctImages(): List<JevImage> =
    fold(mutableListOf()) { kept, image ->
        if (kept.none { it === image || (it.reference == image.reference && it.bytes.contentEquals(image.bytes)) }) {
            kept += image
        }
        kept
    }

/**
 * What is wrong with [images] on their own, before any request: each one's carried problem, a blank or overlong
 * reference, two different images sharing a reference, and a reference inside another, which the API's plain-text
 * matching can't tell apart. [images] holds each image once ([distinctImages]).
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
        val references = images.map { it.reference }.filter { it.isNotBlank() }.distinct()
        for (inner in references) {
            references
                .filter { it != inner && inner in it }
                .forEach { add("image reference '$inner' occurs inside '$it', so the API can't tell them apart") }
        }
    }
