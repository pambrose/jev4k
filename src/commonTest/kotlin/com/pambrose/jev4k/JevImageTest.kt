package com.pambrose.jev4k

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.random.Random

@Serializable
private data class Claim(
    val text: String,
    val photo: JevImage,
)

class JevImageTest : StringSpec() {
    init {
        "a PNG and a JPEG are recognized by their signatures" {
            JevImage.of(PNG_BYTES).format shouldBe ImageFormat.PNG
            JevImage.of(JPEG_BYTES).format shouldBe ImageFormat.JPEG
            JevImage.of(PNG_BYTES).problem shouldBe null
        }

        "bytes that are neither carry a problem instead of throwing" {
            JevImage.of("GIF89a......".encodeToByteArray()).problem shouldBe
                "is neither a PNG nor a JPEG; the API takes only those two"
            JevImage.of(ByteArray(0)).problem shouldBe "is empty"
        }

        "base64 is decoded, with or without padding, and text that isn't base64 carries a problem" {
            JevImage.fromBase64(Base64.encode(PNG_BYTES)).bytes shouldBe PNG_BYTES
            JevImage.fromBase64(Base64.encode(JPEG_BYTES).trimEnd('=')).bytes shouldBe JPEG_BYTES
            JevImage.fromBase64("data:image/png;base64,iVBORw0KGgo=").problem shouldBe
                "is not valid base64: use the standard alphabet, with no data: prefix"
        }

        "base64 whitespace is ignored, as the base64 tool's line breaks need, but the URL-safe alphabet isn't" {
            val wrapped = Base64.encode(PNG_BYTES).chunked(8).joinToString("\r\n", postfix = "\n")
            JevImage.fromBase64(wrapped).bytes shouldBe PNG_BYTES
            JevImage.fromBase64(" \t${Base64.encode(JPEG_BYTES)} ").problem shouldBe null
            JevImage.fromBase64(Base64.UrlSafe.encode(byteArrayOf(-5, -1, -2))).problem shouldBe
                "is not valid base64: use the standard alphabet, with no data: prefix"
        }

        "bytes over the API's 16 MiB limit carry a problem" {
            val big = PNG_BYTES.copyOf(MAX_IMAGE_BYTES + 1)
            JevImage.of(big).problem shouldBe "is over the API's limit of 16 MiB per image"
            JevImage.of(big.copyOf(MAX_IMAGE_BYTES)).problem shouldBe null
        }

        "a source is read only up to the limit, so one with no end can't fill memory" {
            val huge = JevImage.readCapped(Buffer().apply { write(ByteArray(MAX_IMAGE_BYTES + 1)) }, "<huge>")
            huge.problem shouldBe "is over the API's limit of 16 MiB per image"
            huge.bytes.size shouldBe 0
            JevImage.readCapped(Buffer().apply { write(PNG_BYTES) }, null).bytes shouldBe PNG_BYTES
        }

        "a generated reference is distinctive and unique" {
            val a = JevImage.of(PNG_BYTES)
            val b = JevImage.of(PNG_BYTES)
            a.reference shouldMatch GENERATED_REFERENCE
            a.reference shouldNotBe b.reference
        }

        "a generated reference carries this process's tag, so one from another process isn't recognized" {
            val a = JevImage.of(PNG_BYTES).reference
            val b = JevImage.of(PNG_BYTES).reference
            a.take(20) shouldBe b.take(20)
            GENERATED_REFERENCE.matches(foreignReference(a)) shouldBe false
        }

        "a chosen reference is kept as given" {
            JevImage.of(PNG_BYTES, reference = "<catalog>").reference shouldBe "<catalog>"
            JevImage.fromBase64(Base64.encode(PNG_BYTES), "<b64>").reference shouldBe "<b64>"
        }

        "toString is the reference, so a string template places the image" {
            val photo = JevImage.of(PNG_BYTES, "<photo>")
            "Is $photo damaged?" shouldBe "Is <photo> damaged?"
        }

        "jsonOf and entry place an image as its reference" {
            val palm = JevImage.of(PNG_BYTES, "<palm>")
            jsonOf(palm) shouldBe JsonPrimitive("<palm>")
            entry("examples" to listOf(palm)) shouldBe json("""{"examples":["<palm>"]}""")
        }

        "a @Serializable state field of type JevImage is written as its reference" {
            jsonEntry(Claim("chipped rim", JevImage.of(PNG_BYTES, "<claim>"))) shouldBe
                json("""{"text":"chipped rim","photo":"<claim>"}""")
        }

        "a JevImage can't be decoded from its reference" {
            shouldThrow<SerializationException> { Json.decodeFromString<JevImage>("\"<photo>\"") }
        }

        "copyBytes hands out a copy of the bytes, for a cache key" {
            val image = JevImage.of(PNG_BYTES)
            val copy = image.copyBytes()
            copy shouldBe PNG_BYTES
            copy[0] = 0
            image.copyBytes() shouldBe PNG_BYTES
        }

        "of copies the bytes it is given" {
            val bytes = PNG_BYTES.copyOf()
            val image = JevImage.of(bytes)
            bytes[0] = 0
            image.bytes shouldBe PNG_BYTES
        }

        "read takes a file through kotlinx-io" {
            val path = Path(SystemTemporaryDirectory, "jev4k-${Random.nextLong()}.png")
            SystemFileSystem.sink(path).buffered().use { it.write(PNG_BYTES) }
            try {
                JevImage.read(path).bytes shouldBe PNG_BYTES
                JevImage.read(path, reference = "<file>").reference shouldBe "<file>"
            } finally {
                SystemFileSystem.delete(path)
            }
        }

        "read of a missing file throws at once" {
            val missing = Path(SystemTemporaryDirectory, "jev4k-missing-${Random.nextLong()}")
            shouldThrow<IOException> { JevImage.read(missing) }
        }
    }
}
