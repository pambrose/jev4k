package com.pambrose.jev4k

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * An image read now from [path]. These are Kotlin extensions on the companion; from Java, which would have to pass the
 * companion explicitly, write `JevImage.of(Files.readAllBytes(path))`.
 */
@ExperimentalJevImages
fun JevImage.Companion.read(
    path: Path,
    reference: String? = null,
): JevImage = of(Files.readAllBytes(path), reference)

/** An image read now from [file]. */
@ExperimentalJevImages
fun JevImage.Companion.read(
    file: File,
    reference: String? = null,
): JevImage = of(file.readBytes(), reference)
