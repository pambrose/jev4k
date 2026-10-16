package com.pambrose.jev4k

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
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

        "a missing file throws at once" {
            shouldThrow<NoSuchFileException> { JevImage.read(Path.of("/no/such/jev4k/image.png")) }
        }
    }
}
