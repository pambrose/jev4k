package com.pambrose.jev4k

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

class JevImageJvmTest : StringSpec() {
    init {
        "read takes a java.nio.file.Path and a java.io.File" {
            val path = Files.createTempFile("jev4k-", ".jpg")
            try {
                Files.write(path, JPEG_BYTES)
                JevImage.read(path).format shouldBe ImageFormat.JPEG
                JevImage.read(path.toFile(), reference = "<file>").reference shouldBe "<file>"
            } finally {
                Files.delete(path)
            }
        }

        "a file over the API's 16 MiB limit isn't read, through any of the three overloads" {
            val path = Files.createTempFile("jev4k-", ".png")
            try {
                // A sparse file: its length says 16 MiB + 1 without writing that much.
                RandomAccessFile(path.toFile(), "rw").use { it.setLength(MAX_IMAGE_BYTES + 1L) }
                val images =
                    listOf(
                        JevImage.read(path),
                        JevImage.read(path.toFile()),
                        JevImage.read(kotlinx.io.files.Path(path.toString())),
                    )
                images.forEach { image ->
                    image.bytes.size shouldBe 0
                    shouldThrow<JevValidationException> { questions { attach(image) } }.problems shouldContain
                        "image '${image.reference}' (16777217 bytes) is over the API's limit of 16 MiB per image"
                }
            } finally {
                Files.delete(path)
            }
        }

        "a missing file throws at once" {
            shouldThrow<NoSuchFileException> { JevImage.read(Path.of("/no/such/jev4k/image.png")) }
        }
    }
}
