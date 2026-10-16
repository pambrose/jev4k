package com.pambrose.jev4k

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
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
                "is not valid base64: use the standard alphabet, with no whitespace and no data: prefix"
        }

        "a generated reference is distinctive and unique" {
            val a = JevImage.of(PNG_BYTES)
            val b = JevImage.of(PNG_BYTES)
            a.reference shouldMatch GENERATED_REFERENCE
            a.reference shouldNotBe b.reference
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

        "of copies the bytes it is given" {
            val bytes = PNG_BYTES.copyOf()
            val image = JevImage.of(bytes)
            bytes[0] = 0
            image.format shouldBe ImageFormat.PNG
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
