package com.pambrose.jev4k

import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * An image read now from [path]. A file over the API's 16 MiB limit isn't read: the image carries that problem
 * instead. These are Kotlin extensions on the companion; from Java, which would have to pass the companion explicitly,
 * write `JevImage.of(Files.readAllBytes(path))`.
 */
@ExperimentalJevImages
fun JevImage.Companion.read(
    path: Path,
    reference: String? = null,
): JevImage {
    val size = if (Files.isRegularFile(path)) Files.size(path) else null
    return if (size != null && size > MAX_IMAGE_BYTES) {
        tooLarge(size, reference)
    } else {
        Files.newInputStream(path).asSource().buffered().use { readCapped(it, reference) }
    }
}

/** An image read now from [file], with the same limit. */
@ExperimentalJevImages
fun JevImage.Companion.read(
    file: File,
    reference: String? = null,
): JevImage {
    val size = if (file.isFile) file.length() else null
    return if (size != null && size > MAX_IMAGE_BYTES) {
        tooLarge(size, reference)
    } else {
        file.inputStream().asSource().buffered().use { readCapped(it, reference) }
    }
}
