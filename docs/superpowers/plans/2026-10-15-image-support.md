# Image and Box Support Implementation Plan

Status: carried out in full and released in 0.3.0 (2026-10-16). The step boxes below were not ticked as the
work went; the commits on the 0.3.0 release PR record it.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let jev4k send PNG and JPEG images in the state, instructions and criteria, and ask TypeSafe's Box
questions, matching the Python 0.7.5 and JS 0.7.0 SDKs.

**Architecture:** A `JevImage` stands in content as its reference string, and is *attached* to a `QuestionSet`
(through `QueryBuilder.attach`, `JevQuery.attach`, or the new `ask(query, state, vararg images)`). `JevClient.evaluate`
checks the request's structure, adds the `TypeSafe-Danger-Zone-Images: true` header, and sends a request that has images
as `multipart/form-data`: a `request` part holding the JSON plus a generated `assets` map, then one part per image.
Box is a fourth `Question` and `Answer` subtype. Everything sits behind the `@ExperimentalJevImages` opt-in.

**Tech Stack:** Kotlin 2.4.21 Multiplatform, Ktor 3.6.0 (`MultiPartFormDataContent`, `MockEngine`),
kotlinx.serialization 1.11.0, kotlinx-io 0.9.1, Kotest 6.2.6, MockK 1.14.11.

**Spec:** `docs/superpowers/specs/2026-10-15-image-support-design.md`

## Global Constraints

- **Commits:** the user's CLAUDE.md says not to commit or push until told to. Each task ends with a "ready to commit"
  checkpoint: show the user the diff summary and commit only when they say so.
- **Where code lives:** everything is in `src/commonMain`, except the JVM file readers (`src/jvmMain`) and the
  `BlockingJev` overloads (`src/jvmMain/.../BlockingJev.jvm.kt`). All of it works on every target in `build.gradle.kts`.
- **Opt-in:** every public declaration that names `JevImage`, `BoxQuestion`, `BoundingBox` or `BoxAnswer`, and every new
  public function, carries `@ExperimentalJevImages`. The build opts the whole module in
  (`optIn.add("com.pambrose.jev4k.ExperimentalJevImages")`), so the compiler won't flag a declaration that is missing
  the annotation. Check each one by eye.
- **No API breaks:** no existing public signature changes. `api/` diffs are additions only. Run `make abi-update` on the
  Mac after each task that changes public API, and read the diff.
- **Dependencies:** no new artifacts. `kotlinx-io-core` is declared at `0.9.1`, the version `ktor-io` 3.6.0 already
  brings.
- **Unchanged requests:** a request with no images and no Box question is sent exactly as today. It is a
  `TextContent` with the same JSON and no images header.
- **Client-side checks:** structural only. Size and pixel limits are left to the server.
- **Tests:**
    - Each test class is a Kotest `StringSpec()` with an `init {}` block.
    - Common specs go in `src/commonTest/kotlin/com/pambrose/jev4k/`. MockK is used only in `src/jvmTest`.
    - Build exceptions with common APIs.
    - Never throw a bare `IllegalStateException` on the request path: Curl and WinHttp failures arrive as one, so
      jev4k treats it as a connection error. Use `requireNotNull` / `IllegalArgumentException` instead.
- **Style:**
    - ktlint (`ktlint_official`, 4 spaces, 120 columns). A function or class signature with two or more parameters has
      one parameter per line.
    - Detekt limits: `ThrowsCount` 2, `ReturnCount` 3, `LongMethod` 140, `CyclomaticComplexMethod` 25.
    - `make format` can wrap lines without re-indenting them, so check its output by eye.
- **Website examples:**
    - Examples live in `src/jvmTest/kotlin/website` (`package website`). They contain no Kotest or MockK, and top-level
      names must be unique across that package.
    - Trailing comments are aligned in a column within each run of consecutive lines, and stay within 120 characters.
    - Pages pull examples in with `--8<-- "File.kt:section"`. Never write marker text in page prose.
    - An admonition body is indented four spaces.
- **Live calls:** only with `JEV4K_LIVE=1`.

## Review Focus

These five cases are implied by the spec but not covered by any obvious test. Each one now has a test in the task that
owns the code:

1. **One image placed several times** (in the state and in two questions): it is attached once, sent as one part, and
   gets one `assets` entry (Task 4).
2. **The same image attached twice over:** an image a `JevQuery` attaches and that is also passed to
   `ask(query, state, image)` is sent once (Task 4).
3. **Images in a nested `@Serializable` state:** a frame history (`List<Frame>`, each frame holding a `JevImage`) writes
   each image as its reference and sends the parts in attach order (Task 4).
4. **A short chosen reference inside a longer word:** `"cat"` placed only inside `"category"` counts as referenced, as
   it does under the API's plain-text rule (Task 4).
5. **A Box answer nobody asked for:** an extra answer with `"type": "box"` still maps to a `BoxAnswer` in `answers` and
   `boxes` (Task 3).

---

### Task 1: `JevImage`, the opt-in, and image sources

**Files:**
- Modify: `gradle/libs.versions.toml` (versions + libraries)
- Modify: `build.gradle.kts:171-191` (`compilerOptions.optIn`, `commonMain` dependencies)
- Create: `src/commonMain/kotlin/com/pambrose/jev4k/JevImage.kt`
- Create: `src/jvmMain/kotlin/com/pambrose/jev4k/JevImage.jvm.kt`
- Modify: `src/commonMain/kotlin/com/pambrose/jev4k/Entries.kt:40-88` (`jsonOf`)
- Modify: `src/commonTest/kotlin/com/pambrose/jev4k/TestSupport.kt` (fixtures)
- Test: `src/commonTest/kotlin/com/pambrose/jev4k/JevImageTest.kt`
- Test: `src/jvmTest/kotlin/com/pambrose/jev4k/JevImageJvmTest.kt`

**Interfaces:**
- Produces:
    - `annotation class ExperimentalJevImages`
    - `class JevImage` with `val reference: String`, `internal val bytes: ByteArray`, `internal val format: ImageFormat?`,
      `internal val problem: String?`
    - Factories: `JevImage.of(bytes: ByteArray, reference: String? = null)`, `JevImage.fromBase64(text: String,
      reference: String? = null)`, `JevImage.read(path: kotlinx.io.files.Path, reference: String? = null)`
    - JVM extensions: `JevImage.Companion.read(path: java.nio.file.Path, reference: String? = null)` and
      `read(file: java.io.File, reference: String? = null)`
    - `internal enum class ImageFormat(contentType: String, extension: String)` with `PNG` and `JPEG`
    - `internal val GENERATED_REFERENCE: Regex`
    - `internal const val MAX_REFERENCE_BYTES = 1024`
    - `internal fun imageDefinitionProblems(images: List<JevImage>): List<String>`
    - Test fixtures `PNG_BYTES`, `JPEG_BYTES`
- Consumes: nothing new.

- [ ] **Step 1: Add the dependency and the module-wide opt-in**

In `gradle/libs.versions.toml`, under `[versions]` after `kover = "0.9.11"`:

```toml
kotlinx-io = "0.9.1"  # the version ktor-io 3.6.0 brings; kotlinx.io.files.Path is in jev4k's public API
```

Under `[libraries]` after `kotlinx-coroutines-core`:

```toml
kotlinx-io-core = { module = "org.jetbrains.kotlinx:kotlinx-io-core", version.ref = "kotlinx-io" }
```

In `build.gradle.kts`, inside `compilerOptions { ... }`, after the `optIn.add("kotlinx.serialization...")` line:

```kotlin
        // jev4k's own image and Box API is marked @ExperimentalJevImages for its consumers; the library and its tests
        // use it everywhere, so the module opts in once here instead of in every file.
        optIn.add("com.pambrose.jev4k.ExperimentalJevImages")
```

In `commonMain`'s `dependencies`, after `api(libs.kotlinx.coroutines.core)`, and update the comment above the three
`api` lines from "each of these three" to "each of these four", adding "and JevImage.read takes a kotlinx-io Path":

```kotlin
                api(libs.kotlinx.io.core)
```

- [ ] **Step 2: Add the test fixtures**

Append to `src/commonTest/kotlin/com/pambrose/jev4k/TestSupport.kt`:

```kotlin
/** A PNG signature and a few bytes: jev4k checks only the signature, and no test sends these to the real API. */
internal val PNG_BYTES =
    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52)

/** A JPEG signature and a few bytes, as [PNG_BYTES] is for PNG. */
internal val JPEG_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 0x4A, 0x46)
```

- [ ] **Step 3: Write the failing common test**

Create `src/commonTest/kotlin/com/pambrose/jev4k/JevImageTest.kt`:

```kotlin
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
            shouldThrow<IOException> { JevImage.read(Path(SystemTemporaryDirectory, "jev4k-missing-${Random.nextLong()}")) }
        }
    }
}
```

- [ ] **Step 4: Run it to make sure it fails**

Run: `./gradlew jvmTest --tests "com.pambrose.jev4k.JevImageTest"`
Expected: compilation fails, with `Unresolved reference 'JevImage'`.

- [ ] **Step 5: Write `JevImage.kt`**

Create `src/commonMain/kotlin/com/pambrose/jev4k/JevImage.kt`:

```kotlin
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
        ): JevImage = create(SystemFileSystem.source(path).buffered().use { it.readByteArray() }, reference, null)

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
            image.problem?.let { add("image '$reference' $it") }
        }
        images
            .groupBy { it.reference }
            .filterValues { it.size > 1 }
            .keys
            .forEach { add("two different images share the reference '$it'") }
    }
```

If the compiler rejects `@Serializable(with = JevImageSerializer::class)` because the serializer is `internal`, make
`JevImageSerializer` public and annotate it `@ExperimentalJevImages`. If `Uuid.random()` is reported as deprecated,
use `Uuid.generateV4()`.

- [ ] **Step 6: Let `jsonOf` place images**

In `src/commonMain/kotlin/com/pambrose/jev4k/Entries.kt`, change the `jsonOf` KDoc's first sentence to:

```kotlin
/**
 * Converts plain Kotlin values (null, strings, numbers, booleans, enums, maps with string keys,
 * iterables, arrays, primitive arrays such as `IntArray`, JSON elements, and [JevImage]s, as their reference) to JSON.
 * Use [jsonEntry] for `@Serializable` values.
 */
```

and add this branch right after `is String -> JsonPrimitive(value)`:

```kotlin
        // An image is placed as its reference; its bytes go with the request once it is attached.
        is JevImage -> JsonPrimitive(value.reference)
```

- [ ] **Step 7: Run the common test**

Run: `./gradlew jvmTest --tests "com.pambrose.jev4k.JevImageTest"`
Expected: PASS, 12 tests.

- [ ] **Step 8: Write the failing JVM test**

Create `src/jvmTest/kotlin/com/pambrose/jev4k/JevImageJvmTest.kt`:

```kotlin
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
```

Run: `./gradlew jvmTest --tests "com.pambrose.jev4k.JevImageJvmTest"`
Expected: compilation fails, with no `read` overload taking `java.nio.file.Path`.

- [ ] **Step 9: Write the JVM readers**

Create `src/jvmMain/kotlin/com/pambrose/jev4k/JevImage.jvm.kt`:

```kotlin
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
```

Run: `./gradlew jvmTest --tests "com.pambrose.jev4k.JevImageJvmTest"`
Expected: PASS, 2 tests.

- [ ] **Step 10: Update the ABI dumps and run everything**

Run: `make abi-update`, then `git diff --stat api/ && git diff api/ | grep '^-' | grep -v '^---'`
Expected: `api/jev4k.api` and `api/jev4k.klib.api` gain `ExperimentalJevImages`, `JevImage` (with `Companion.of`,
`fromBase64`, `read` and `serializer`) and `JevImage_jvmKt.read`. The second command prints nothing: there are no
removed lines.

Run: `make tests`
Expected: BUILD SUCCESSFUL. The new specs pass on the JVM, js, wasmJs and the macOS and simulator targets.
`JevImageTest`'s `read` tests exercise kotlinx-io's file system on Node.js and native.

- [ ] **Step 11: Ready to commit**

Show `git status --short` and `git diff --stat` to the user. Commit only when they say so, with the message
"Add JevImage and the ExperimentalJevImages opt-in".

---

### Task 2: Attaching images to questions

**Files:**
- Modify: `src/commonMain/kotlin/com/pambrose/jev4k/Questions.kt:89-155` (`QuestionSet`, `validate`)
- Modify: `src/commonMain/kotlin/com/pambrose/jev4k/Builders.kt` (image overloads, `attach`, `include`, `build`)
- Modify: `src/commonMain/kotlin/com/pambrose/jev4k/JevQuery.kt:52-64` (`attach`, rebuild condition)
- Modify: `src/commonTest/kotlin/com/pambrose/jev4k/TestSupport.kt` (`Gestures`)
- Test: `src/commonTest/kotlin/com/pambrose/jev4k/ImageAttachTest.kt`

**Interfaces:**
- Consumes (Task 1): `JevImage`, `imageDefinitionProblems(images)`, `PNG_BYTES`, `JPEG_BYTES`
- Produces:
    - `QuestionSet internal constructor(questions: List<QuestionRef<*>>, images: List<JevImage> = emptyList())` and
      `@ExperimentalJevImages val QuestionSet.images: List<JevImage>`, holding each instance once, in attach order
    - `QueryBuilder.attach(vararg images: JevImage)`
    - `JevQuery.attach(image: JevImage): JevImage` (protected)
    - Builder overloads: `ChoiceBuilder: String.means(image)`, `ChoiceBuilder.option(key, image)`,
      `ScoreBuilder.level(image)`, `NoulBuilder.whenTrue(image)` and `whenFalse(image)`
    - Test object `Gestures` with `thumb` (`"<thumb>"`, PNG), `palm` (`"<palm>"`, JPEG) and `gesture`

- [ ] **Step 1: Add the shared `Gestures` query to the test support**

Append to `src/commonTest/kotlin/com/pambrose/jev4k/TestSupport.kt`:

```kotlin
/** A Choice defined by example pictures, TypeSafe's "define options by example" pattern. */
object Gestures : JevQuery() {
    val thumb = attach(JevImage.of(PNG_BYTES, "<thumb>"))
    val palm = attach(JevImage.of(JPEG_BYTES, "<palm>"))
    val gesture by choice("Which gesture is the hand in `camera` making?") {
        "thumbs_up" means thumb
        "open_palm" means entry("description" to "A hand held up, palm to the camera.", "examples" to listOf(palm))
        "none" means "No hand, or a hand that matches none of the examples."
    }
}
```

- [ ] **Step 2: Write the failing test**

Create `src/commonTest/kotlin/com/pambrose/jev4k/ImageAttachTest.kt`:

```kotlin
package com.pambrose.jev4k

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.jsonObject

private object BadImageQuery : JevQuery() {
    val pic = attach(JevImage.of(ByteArray(0), "<nothing>"))
    val seen by noul("Is $pic a cat?")
}

class ImageAttachTest : StringSpec() {
    init {
        "a JevQuery carries the images it attaches, in order" {
            Gestures.questions.images.map { it.reference } shouldBe listOf("<thumb>", "<palm>")
        }

        "a set built without images has none" {
            Triage.questions.images.shouldBeEmpty()
        }

        "the builders place an image as its reference" {
            Gestures.questions.toJson()["gesture"]!!.jsonObject["criteria"] shouldBe
                json(
                    """
                    {
                      "thumbs_up": "<thumb>",
                      "open_palm": {"description": "A hand held up, palm to the camera.", "examples": ["<palm>"]},
                      "none": "No hand, or a hand that matches none of the examples."
                    }
                    """,
                )

            val ok = JevImage.of(PNG_BYTES, "<ok>")
            val set =
                questions {
                    attach(ok)
                    noul("assembled", "Is the unit in `photo` assembled correctly?") {
                        whenTrue(ok)
                        whenFalse(ok)
                    }
                    score("open", "How open is the hand in `photo`?") {
                        level(ok)
                        level("Fully open")
                    }
                    choice("pick", "Which unit is in `photo`?") {
                        option("a", ok)
                        option("b")
                    }
                }
            set.toJson() shouldBe
                json(
                    """
                    {
                      "assembled": {"type": "noul", "instructions": "Is the unit in `photo` assembled correctly?",
                                    "criteria": {"true": "<ok>", "false": "<ok>"}},
                      "open": {"type": "score", "instructions": "How open is the hand in `photo`?",
                               "criteria": ["<ok>", "Fully open"]},
                      "pick": {"type": "choice", "instructions": "Which unit is in `photo`?",
                               "criteria": {"a": "<ok>", "b": null}}
                    }
                    """,
                )
        }

        "include and attach merge images, keeping each instance once" {
            val frame = JevImage.of(JPEG_BYTES, "<frame>")
            val set =
                questions {
                    include(Gestures)
                    attach(frame, frame)
                    attach(Gestures.thumb)
                }
            set.images.map { it.reference } shouldBe listOf("<thumb>", "<palm>", "<frame>")
        }

        "set validation reports every image problem along with the rest" {
            val problems =
                shouldThrow<JevValidationException> {
                    questions {
                        attach(
                            JevImage.of("GIF89a......".encodeToByteArray(), "<gif>"),
                            JevImage.of(ByteArray(0), "<empty>"),
                            JevImage.fromBase64("not base64!", "<b64>"),
                            JevImage.of(PNG_BYTES, " "),
                            JevImage.of(PNG_BYTES, "é".repeat(513)),
                            JevImage.of(PNG_BYTES, "<twin>"),
                            JevImage.of(JPEG_BYTES, "<twin>"),
                        )
                        noul(" ", "A question with a blank id")
                    }
                }.problems
            problems shouldContainAll
                listOf(
                    "Question ids must not be blank",
                    "image '<gif>' is neither a PNG nor a JPEG; the API takes only those two",
                    "image '<empty>' is empty",
                    "image '<b64>' is not valid base64: use the standard alphabet, with no whitespace and no " +
                        "data: prefix",
                    "image references must not be blank",
                    "image reference '${"é".repeat(40)}…' is longer than 1024 bytes of UTF-8",
                    "two different images share the reference '<twin>'",
                )
        }

        "a JevQuery with a bad image fails on first use, not in its initializer" {
            shouldThrow<JevValidationException> { BadImageQuery.questions }.problems shouldContain
                "image '<nothing>' is empty"
        }
    }
}
```

- [ ] **Step 3: Run it to make sure it fails**

Run: `./gradlew jvmTest --tests "com.pambrose.jev4k.ImageAttachTest"`
Expected: compilation fails, with `Unresolved reference 'attach'` (in `TestSupport.kt` and the test).

- [ ] **Step 4: Give `QuestionSet` its images**

In `src/commonMain/kotlin/com/pambrose/jev4k/Questions.kt`, replace the start of `QuestionSet` (the class header up to
and including its `init` block) with:

```kotlin
/** An ordered, validated set of questions: everything one request asks about a single state. */
class QuestionSet internal constructor(
    questions: List<QuestionRef<*>>,
    images: List<JevImage> = emptyList(),
) : Iterable<QuestionRef<*>> {
    private val questions: List<QuestionRef<*>> = questions.toList()

    /**
     * The images attached to these questions, each instance once, in the order they were attached. They are sent with
     * every request that asks this set.
     */
    @ExperimentalJevImages
    val images: List<JevImage> = images.distinct()

    init {
        val problems = validate(this.questions, this.images)
        if (problems.isNotEmpty()) throw JevValidationException(problems)
    }
```

`JevImage` doesn't override `equals`, so `distinct()` deduplicates by identity. Then change `internal fun validate` to:

```kotlin
internal fun validate(
    questions: List<QuestionRef<*>>,
    images: List<JevImage> = emptyList(),
): List<String> =
    buildList {
        if (questions.isEmpty()) add("A request needs at least one question")
        val seen = mutableSetOf<String>()
        for (ref in questions) {
            when {
                ref.id.isBlank() -> add("Question ids must not be blank")
                !seen.add(ref.id) -> add("Duplicate question id '${ref.id}'")
            }
            ref.problems.forEach { add("question '${ref.id}': $it") }
            if (ref.complete) addAll(validate(ref.id, ref.question))
        }
        addAll(imageDefinitionProblems(images))
    }
```

- [ ] **Step 5: Add `attach` and the image overloads to the builders**

In `src/commonMain/kotlin/com/pambrose/jev4k/Builders.kt`:

- In `NoulBuilder`, after `fun whenFalse(entry: JsonElement)`, add:

```kotlin
    /** Defines yes with a picture: the [image]'s reference. */
    @ExperimentalJevImages
    fun whenTrue(image: JevImage) {
        whenTrue(JsonPrimitive(image.reference))
    }

    /** Defines no with a picture: the [image]'s reference. */
    @ExperimentalJevImages
    fun whenFalse(image: JevImage) {
        whenFalse(JsonPrimitive(image.reference))
    }
```

- In `ChoiceBuilder`, after `infix fun String.means(entry: JsonElement)`, add:

```kotlin
    /** An option defined by a picture: the [image]'s reference. Option keys are never replaced, so it goes here. */
    @ExperimentalJevImages
    infix fun String.means(image: JevImage) {
        option(this, JsonPrimitive(image.reference))
    }
```

and after `fun option(key: String, entry: JsonElement)`, add:

```kotlin
    @ExperimentalJevImages
    fun option(
        key: String,
        image: JevImage,
    ) {
        option(key, JsonPrimitive(image.reference))
    }
```

- In `ScoreBuilder`, after `fun level(entry: JsonElement)`, add:

```kotlin
    /** A level shown by an example picture: the [image]'s reference. */
    @ExperimentalJevImages
    fun level(image: JevImage) {
        level(JsonPrimitive(image.reference))
    }
```

- In `QueryBuilder`, after `private val questions = mutableListOf<QuestionRef<*>>()`, add
  `private val images = mutableListOf<JevImage>()`. Then replace `include` and `build`, and add `attach` before
  `include`:

```kotlin
    /**
     * Attaches [images] to this request, so their bytes are sent. Placing an image (in a string, an entry, a builder
     * overload or the state) never attaches it.
     */
    @ExperimentalJevImages
    fun attach(vararg images: JevImage) {
        this.images += images
    }

    /**
     * Adds every question of [query], keeping its handles, so typed and ad-hoc questions share one request. The images
     * the query attaches come too.
     */
    fun include(query: JevQuery) {
        val set = query.questions
        questions += set
        images += set.images
    }
```

```kotlin
    internal fun build(): QuestionSet = QuestionSet(questions, images)
```

- [ ] **Step 6: Add `attach` to `JevQuery`**

In `src/commonMain/kotlin/com/pambrose/jev4k/JevQuery.kt`, after `private var built: QuestionSet? = null`, add
`private val registeredImages = mutableListOf<JevImage>()`. Replace the `questions` getter with:

```kotlin
    /** The declared questions, in declaration order (base-class questions first), with the images [attach]ed. */
    val questions: QuestionSet
        // Rebuilt when questions or images have registered since the last read, which only happens during
        // initialization. Once that is over the set never changes, and a race between two first reads just builds it
        // twice.
        get() =
            built?.takeIf { it.size == registered.size && it.images.size == registeredImages.size }
                ?: QuestionSet(registered, registeredImages).also { built = it }
```

After `internal fun register(...)`, add:

```kotlin
    /**
     * Attaches [image] to every request that asks these questions, and returns it, so a picture can be declared and
     * attached at once: `private val thumb = attach(JevImage.of(thumbBytes))`. Reading a file here, with
     * [JevImage.read], does I/O in the object's initializer; a missing file then fails the object's first use.
     */
    @ExperimentalJevImages
    protected fun attach(image: JevImage): JevImage {
        if (registeredImages.none { it === image }) registeredImages += image
        return image
    }
```

- [ ] **Step 7: Run the test**

Run: `./gradlew jvmTest --tests "com.pambrose.jev4k.ImageAttachTest" --tests "com.pambrose.jev4k.DslTest" --tests "com.pambrose.jev4k.ValidationTest"`
Expected: PASS. The existing DSL and validation specs are unchanged.

- [ ] **Step 8: Update the ABI dumps and run everything**

Run: `make abi-update`, then `git diff api/ | grep '^-' | grep -v '^---'`
Expected: the second command prints nothing. The additions are `QuestionSet.getImages`, `QueryBuilder.attach`,
`JevQuery.attach`, and the five builder overloads.

Run: `make tests`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Ready to commit**

Show the diff summary. Commit only when the user says so, with the message "Attach images to questions".

---

### Task 3: Box questions and answers

**Files:**
- Modify: `src/commonMain/kotlin/com/pambrose/jev4k/Questions.kt` (`BoxQuestion`, `typeName`, `validate`, `sentEntries`,
  `boxRef`)
- Modify: `src/commonMain/kotlin/com/pambrose/jev4k/Answers.kt` (`BoundingBox`, `BoxAnswer`, `typeName`, `decodeBox`)
- Modify: `src/commonMain/kotlin/com/pambrose/jev4k/Builders.kt` (`QueryBuilder.box`, `question`)
- Modify: `src/commonMain/kotlin/com/pambrose/jev4k/JevQuery.kt` (`box`)
- Modify: `src/commonMain/kotlin/com/pambrose/jev4k/JevResult.kt` (`box`, `boxes`)
- Modify: `src/commonMain/kotlin/com/pambrose/jev4k/internal/Wire.kt` (`WireQuestion.Box`)
- Modify: `src/commonMain/kotlin/com/pambrose/jev4k/internal/ResponseMapper.kt` (`"box"` branch)
- Test: `src/commonTest/kotlin/com/pambrose/jev4k/BoxTest.kt`

**Interfaces:**
- Consumes: `QuestionRef`, `QuestionProvider`, `ResponseInfo.fail`, `jevResult(String, QuestionSet)`
- Produces:
    - `@ExperimentalJevImages data class BoxQuestion(instructions: JsonElement) : Question`
    - `data class BoundingBox(top: Double, bottom: Double, left: Double, right: Double)`
    - `data class BoxAnswer(box: BoundingBox, confidence: Double?) : Answer`
    - `QueryBuilder.box(id: String, instructions: String | JsonElement): QuestionRef<BoxAnswer>`
    - `JevQuery.box(instructions: String | JsonElement, id: String? = null): QuestionProvider<BoxAnswer>`
    - `JevResult.box(id: String): BoxAnswer` and `JevResult.boxes: Map<String, BoxAnswer>`
    - `internal fun Question.sentEntries(): List<Pair<String, JsonElement>>` (renamed from the private `entries()`)

- [ ] **Step 1: Write the failing test**

Create `src/commonTest/kotlin/com/pambrose/jev4k/BoxTest.kt`:

```kotlin
package com.pambrose.jev4k

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonPrimitive

private object Finder : JevQuery() {
    val mug by box("the coffee mug in `photo`")
}

private const val MUG_ANSWER =
    """{"type":"box","box":{"top":0.25,"bottom":0.82,"left":0.44,"right":0.84},"confidence":0.92}"""

class BoxTest : StringSpec() {
    private val finder = questions { box("mug", "the coffee mug in `photo`") }

    init {
        "box builds a Box question in both layers, sent with no criteria" {
            finder.toJson() shouldBe json("""{"mug":{"type":"box","instructions":"the coffee mug in `photo`"}}""")
            Finder.questions.toJson() shouldBe finder.toJson()
        }

        "a blank Box instruction fails validation like any other" {
            shouldThrow<JevValidationException> { questions { box("mug", " ") } }
                .problems.single() shouldContain "question 'mug': instructions must be non-blank"
        }

        "a Box answer maps to a BoxAnswer, read by handle, by id and in boxes" {
            val r = jevResult("""{"model":"jev-1.14.0","answers":{"mug":$MUG_ANSWER}}""", Finder.questions)
            val expected = BoxAnswer(BoundingBox(top = 0.25, bottom = 0.82, left = 0.44, right = 0.84), 0.92)
            r[Finder.mug] shouldBe expected
            r.box("mug") shouldBe expected
            r.boxes shouldBe mapOf("mug" to expected)
        }

        "an answer with no type is read as the Box that was asked, and confidence may be null or absent" {
            val box = """"box":{"top":0.1,"bottom":0.9,"left":0.2,"right":0.8}"""
            jevResult("""{"answers":{"mug":{$box}}}""", finder).box("mug").confidence shouldBe null
            jevResult("""{"answers":{"mug":{$box,"confidence":null}}}""", finder).box("mug").confidence shouldBe null
        }

        "a Box answer nobody asked for still maps to a BoxAnswer" {
            val r = jevResult("""{"answers":{"mug":$MUG_ANSWER,"extra":$MUG_ANSWER}}""", finder)
            r.answers["extra"].shouldBeInstanceOf<BoxAnswer>()
            r.boxes.keys shouldBe setOf("mug", "extra")
        }

        "a malformed Box answer fails with the field's path" {
            fun box(edges: String) = """{"type":"box","box":{$edges}}"""
            val cases =
                mapOf(
                    """{"type":"box"}""" to "answers.mug.box",
                    box(""""top":0.1,"bottom":0.9,"left":0.2""") to "answers.mug.box.right",
                    box(""""top":0.1,"bottom":0.9,"left":0.2,"right":1.5""") to "answers.mug.box.right",
                    box(""""top":0.1,"bottom":0.9,"left":0.2,"right":"0.8"""") to "answers.mug.box.right",
                    box(""""top":0.1,"bottom":0.9,"left":0.8,"right":0.2""") to "answers.mug.box.left",
                    box(""""top":0.9,"bottom":0.1,"left":0.2,"right":0.8""") to "answers.mug.box.top",
                    """{"type":"box","box":{"top":0.1,"bottom":0.9,"left":0.2,"right":0.8},"confidence":2}""" to
                        "answers.mug.confidence",
                )
            for ((answer, path) in cases) {
                withClue(answer) {
                    shouldThrow<JevResponseValidationException> {
                        jevResult("""{"answers":{"mug":$answer}}""", finder)
                    }.fieldPath shouldBe path
                }
            }
        }

        "question() takes a BoxQuestion, and its handle checks the answer's type" {
            lateinit var ref: QuestionRef<Answer>
            val set = questions { ref = question("mug", BoxQuestion(JsonPrimitive("the mug in `photo`"))) }
            val r = jevResult("""{"answers":{"mug":{"type":"noul","noul":0.5}}}""", set)
            shouldThrow<JevResponseValidationException> { r[ref] }.message shouldContain
                "expected a box answer but got noul"
        }

        "box() by id checks the question's type" {
            val r = jevResult("""{"answers":{}}""", Triage.questions)
            shouldThrow<IllegalArgumentException> { r.box("urgent") }.message shouldBe
                "Question 'urgent' is a noul, not a box"
        }
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew jvmTest --tests "com.pambrose.jev4k.BoxTest"`
Expected: compilation fails, with `Unresolved reference 'box'`.

- [ ] **Step 3: Add `BoxQuestion`**

In `Questions.kt`, after `ScoreQuestion`, add:

```kotlin
/**
 * Find one thing in an image: the answer is a [BoxAnswer], the box around it. Box takes no criteria, and needs an
 * image attached to the request. It always returns a box, even for a target that isn't there, so ask a Noul too.
 */
@ExperimentalJevImages
data class BoxQuestion(
    override val instructions: JsonElement,
) : Question
```

In the `Question.typeName` getter add `is BoxQuestion -> "box"`. In `private fun validate(id, question)`, add a branch
to the `when`:

```kotlin
            is BoxQuestion -> {
                // Box takes no criteria; its instructions were checked above.
            }
```

Rename `private fun Question.entries()` to `internal fun Question.sentEntries()`, update its one caller in `validate`
(`question.sentEntries()`), and add the branch `is BoxQuestion -> emptyList()` to it. After `scoreRef`, add:

```kotlin
internal fun boxRef(
    id: String,
    instructions: JsonElement,
): QuestionRef<BoxAnswer> = QuestionRef(id, BoxQuestion(instructions), ::decodeBox)
```

- [ ] **Step 4: Add `BoundingBox` and `BoxAnswer`**

In `Answers.kt`, after `ScoreAnswer`, add:

```kotlin
/**
 * Where a Box found its target. Each edge is a fraction, from 0 to 1, of the image's height
 * ([top][BoundingBox.top], [bottom][BoundingBox.bottom]) or width ([left][BoundingBox.left],
 * [right][BoundingBox.right]), measured from the top-left corner, so the same box fits the image at any size.
 */
@ExperimentalJevImages
data class BoundingBox(
    val top: Double,
    val bottom: Double,
    val left: Double,
    val right: Double,
)

/**
 * The answer to a Box: the [box][BoxAnswer.box] around the target, and a [confidence][BoxAnswer.confidence] from 0
 * to 1, or null when the API reports none. A Box always answers with a box, even when the target isn't in the image.
 */
@ExperimentalJevImages
data class BoxAnswer(
    val box: BoundingBox,
    val confidence: Double?,
) : Answer
```

In `Answer.typeName` add `is BoxAnswer -> "box"`, and after `decodeScore` add:

```kotlin
internal fun decodeBox(answer: Answer): BoxAnswer = answer as? BoxAnswer ?: mismatch("box", answer)
```

- [ ] **Step 5: Add the builders**

In `Builders.kt`'s `QueryBuilder`, after the second `score(...)`, add:

```kotlin
    /** A Box: where, in an image attached to the request, the thing [instructions] describes is. */
    @ExperimentalJevImages
    fun box(
        id: String,
        instructions: String,
    ): QuestionRef<BoxAnswer> = box(id, JsonPrimitive(instructions))

    @ExperimentalJevImages
    fun box(
        id: String,
        instructions: JsonElement,
    ): QuestionRef<BoxAnswer> = add(boxRef(id, instructions))
```

In `QueryBuilder.question`, add `is BoxQuestion -> question to ::decodeBox` to the `when`.

In `JevQuery.kt`, after the second `score(...)`, add:

```kotlin
    /** A Box: where, in an image attached to the request, the thing [instructions] describes is. */
    @ExperimentalJevImages
    protected fun box(
        instructions: String,
        id: String? = null,
    ): QuestionProvider<BoxAnswer> = box(JsonPrimitive(instructions), id)

    @ExperimentalJevImages
    protected fun box(
        instructions: JsonElement,
        id: String? = null,
    ): QuestionProvider<BoxAnswer> = QuestionProvider(id) { boxRef(it, instructions) }
```

- [ ] **Step 6: Send it and read it back**

In `internal/Wire.kt`, add to `WireQuestion`:

```kotlin
    @Serializable
    @SerialName("box")
    data class Box(
        val instructions: JsonElement,
    ) : WireQuestion()
```

Import `com.pambrose.jev4k.BoxQuestion`, and add `is BoxQuestion -> WireQuestion.Box(instructions)` to
`Question.toWire()`.

In `internal/ResponseMapper.kt`, import `com.pambrose.jev4k.BoundingBox` and `com.pambrose.jev4k.BoxAnswer`. Add a
branch before `else ->` in `BodyReader.answer`:

```kotlin
            "box" -> {
                boxAnswer(obj, path)
            }
```

Add these to `BodyReader`, after `requiredDouble`:

```kotlin
    private fun boxAnswer(
        obj: JsonObject,
        path: String,
    ): BoxAnswer {
        val boxPath = "$path.box"
        val edges = optionalObject(obj, "box", boxPath) ?: fail(boxPath, "missing")
        val box =
            BoundingBox(
                top = fraction(edges["top"], "$boxPath.top"),
                bottom = fraction(edges["bottom"], "$boxPath.bottom"),
                left = fraction(edges["left"], "$boxPath.left"),
                right = fraction(edges["right"], "$boxPath.right"),
            )
        if (box.left >= box.right) fail("$boxPath.left", "left (${box.left}) must be less than right (${box.right})")
        if (box.top >= box.bottom) fail("$boxPath.top", "top (${box.top}) must be less than bottom (${box.bottom})")
        val confidence = obj["confidence"]?.takeUnless { it == JsonNull }?.let { fraction(it, "$path.confidence") }
        return BoxAnswer(box, confidence)
    }

    /** A number from 0 to 1, as a Box's edges and confidence are. */
    private fun fraction(
        value: JsonElement?,
        path: String,
    ): Double {
        val number = number(value ?: fail(path, "missing"), path)
        return number.takeIf { it in 0.0..1.0 } ?: fail(path, "expected a number from 0 to 1, got $number")
    }
```

In `JevResult.kt`, after `fun score(id: String)`, add:

```kotlin
    @ExperimentalJevImages
    fun box(id: String): BoxAnswer {
        requireQuestion<BoxQuestion>(id, "box")
        return decodeAnswer(id, ::decodeBox)
    }
```

and after `val scores`:

```kotlin
    @ExperimentalJevImages
    val boxes: Map<String, BoxAnswer> get() = answersOf<BoxAnswer>()
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew jvmTest --tests "com.pambrose.jev4k.BoxTest" --tests "com.pambrose.jev4k.ResponseMappingTest" --tests "com.pambrose.jev4k.WireSerializationTest"`
Expected: PASS.

- [ ] **Step 8: Update the ABI dumps and run everything**

Run: `make abi-update`, then `git diff api/ | grep '^-' | grep -v '^---'`
Expected: the second command prints nothing. The additions are `BoxQuestion`, `BoundingBox`, `BoxAnswer`,
`QueryBuilder.box`, `JevQuery.box`, `JevResult.box` and `getBoxes`.

Run: `make tests`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Ready to commit**

Show the diff summary. Commit only when the user says so, with the message "Add Box questions and answers".

---

### Task 4: Sending images: checks, the header, multipart, and `ask(..., images)`

**Files:**
- Create: `src/commonMain/kotlin/com/pambrose/jev4k/internal/ImageRequest.kt`
- Modify: `src/commonMain/kotlin/com/pambrose/jev4k/JevClient.kt:83-153` (`evaluate`, `send`, `setHeaders`, `models`)
- Modify: `src/commonMain/kotlin/com/pambrose/jev4k/JevApi.kt` (`ask` overloads with images)
- Modify: `src/jvmMain/kotlin/com/pambrose/jev4k/BlockingJev.jvm.kt` (the same overloads)
- Modify: `src/commonTest/kotlin/com/pambrose/jev4k/TestSupport.kt` (multipart parser, `Frame`, `History`)
- Test: `src/commonTest/kotlin/com/pambrose/jev4k/ImageRequestTest.kt`
- Test: `src/jvmTest/kotlin/com/pambrose/jev4k/BlockingJevTest.kt` (one test)

**Interfaces:**
- Consumes: `QuestionSet.images`, `JevImage.bytes`/`format`/`reference`, `ImageFormat.contentType`/`extension`,
  `GENERATED_REFERENCE`, `Question.sentEntries()`, `BoxQuestion`, `QueryBuilder.attach`, `Gestures`
- Produces:
    - `internal const val IMAGES_HEADER = "TypeSafe-Danger-Zone-Images"` and
      `internal const val MAX_REFERENCE_BYTES_TOTAL = 65_536`
    - `internal fun imageRequestProblems(state: JsonElement, questions: QuestionSet, extraBody: Map<String,
      JsonElement>): List<String>`
    - `internal fun needsImagesHeader(questions: QuestionSet): Boolean`
    - `internal fun assetsJson(images: List<JevImage>): JsonObject`
    - `internal fun multipartBody(json: String, images: List<JevImage>): MultiPartFormDataContent`
    - `JevApi.ask(query, state: String | JsonElement | T, vararg images: JevImage, model: String? = null)`, and the
      same three on `BlockingJev`
    - Test helpers `Part`, `HttpRequestData.parts()`, `multipartParts(bytes, boundary)`, `Frame`, `History`

- [ ] **Step 1: Add the multipart parser and fixtures to the test support**

Append to `src/commonTest/kotlin/com/pambrose/jev4k/TestSupport.kt`. Its existing imports (`HttpHeaders`,
`HttpRequestData`, `toByteArray`, `Serializable`) cover this code.

```kotlin
/** One part of a multipart/form-data body: its headers and its bytes. */
internal class Part(
    val headers: Map<String, String>,
    val body: ByteArray,
) {
    val name: String
        get() = headers.getValue(HttpHeaders.ContentDisposition).substringAfter("name=").substringBefore(";")

    val text: String get() = body.decodeToString()
}

/** The parts of this request's multipart body. */
internal suspend fun HttpRequestData.parts(): List<Part> {
    val boundary = requireNotNull(body.contentType?.parameter("boundary")) { "not a multipart body: ${body.contentType}" }
    return multipartParts(body.toByteArray(), boundary)
}

/** Splits a multipart [bytes] body on [boundary]: enough for the bodies jev4k writes, whose parts these tests choose. */
internal fun multipartParts(
    bytes: ByteArray,
    boundary: String,
): List<Part> {
    val delimiter = "--$boundary".encodeToByteArray()
    val starts =
        generateSequence(bytes.indexOf(delimiter, 0).takeIf { it >= 0 }) { previous ->
            bytes.indexOf(delimiter, previous + delimiter.size).takeIf { it >= 0 }
        }.toList()
    return starts.zipWithNext { from, to ->
        // "--boundary\r\n", the headers, a blank line, the body, and "\r\n" before the next delimiter.
        val part = bytes.copyOfRange(from + delimiter.size + 2, to - 2)
        val split = part.indexOf("\r\n\r\n".encodeToByteArray(), 0)
        Part(
            headers = part.copyOfRange(0, split).decodeToString().lines().associate {
                it.substringBefore(": ") to it.substringAfter(": ")
            },
            body = part.copyOfRange(split + 4, part.size),
        )
    }
}

private fun ByteArray.indexOf(
    needle: ByteArray,
    from: Int,
): Int = (from..size - needle.size).firstOrNull { start -> needle.indices.all { this[start + it] == needle[it] } } ?: -1

/** One camera frame of a [History], the docs' "what changed" pattern. */
@Serializable
data class Frame(
    val secondsAgo: Int,
    val frame: JevImage,
)

@Serializable
data class History(
    val frames: List<Frame>,
)
```

- [ ] **Step 2: Write the failing test**

Create `src/commonTest/kotlin/com/pambrose/jev4k/ImageRequestTest.kt`:

```kotlin
package com.pambrose.jev4k

import com.pambrose.jev4k.internal.IMAGES_HEADER
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private const val NO_ANSWERS = """{"answers":{}}"""

class ImageRequestTest : StringSpec() {
    init {
        "a request with images is multipart: the JSON first, then each image, with the images header" {
            val jev = testJev { respondJson(NO_ANSWERS) }
            val photo = JevImage.of(PNG_BYTES, "<photo>")
            val badge = JevImage.of(JPEG_BYTES, "<badge>")
            jev.client.query(buildJsonObject { put("photo", photo.reference) }) {
                attach(photo, badge)
                noul("same", "Is the person in `photo` the one in this badge photo: $badge?")
            }

            val request = jev.requests.single()
            request.headers[IMAGES_HEADER] shouldBe "true"
            request.body.contentType?.withoutParameters() shouldBe ContentType.MultiPart.FormData
            val parts = request.parts()
            parts.map { it.name } shouldBe listOf("request", "image-0", "image-1")
            parts[0].headers["Content-Type"] shouldBe "application/json"
            parts[1].headers["Content-Type"] shouldBe "image/png"
            parts[1].headers["Content-Disposition"] shouldBe "form-data; name=image-0; filename=\"image-0.png\""
            parts[1].body shouldBe PNG_BYTES
            parts[2].headers["Content-Type"] shouldBe "image/jpeg"
            parts[2].headers["Content-Disposition"] shouldBe "form-data; name=image-1; filename=\"image-1.jpg\""
            parts[2].body shouldBe JPEG_BYTES

            val body = json(parts[0].text).jsonObject
            body["state"] shouldBe json("""{"photo":"<photo>"}""")
            body["model"] shouldBe json("\"jev-latest\"")
            body["assets"] shouldBe
                json("""{"<photo>":{"type":"image","part":"image-0"},"<badge>":{"type":"image","part":"image-1"}}""")
        }

        "a request without images is the same JSON as before, with no images header" {
            val jev = triageJev()
            jev.client.ask(Triage, state = PAYOUT_TICKET)
            val request = jev.requests.single()
            request.body.shouldBeInstanceOf<TextContent>()
            request.headers[IMAGES_HEADER] shouldBe null
        }

        "the client's or the call's headers can replace the images header" {
            val photo = JevImage.of(PNG_BYTES, "<photo>")
            val jev = testJev(configure = { headers["typesafe-danger-zone-images"] = "false" }) {
                respondJson(NO_ANSWERS)
            }
            jev.client.query("A photo: $photo") {
                attach(photo)
                noul("cat", "Is there a cat in the photo?")
            }
            jev.requests.single().headers.getAll(IMAGES_HEADER) shouldBe listOf("false")
        }

        "a Box question is sent with its image and its answer comes back typed" {
            val photo = JevImage.of(PNG_BYTES)
            val jev = testJev {
                respondJson(
                    """{"answers":{"mug":{"type":"box","box":{"top":0.25,"bottom":0.82,"left":0.44,"right":0.84}}}}""",
                )
            }
            val r = jev.client.query(jsonOf(mapOf("photo" to photo))) {
                attach(photo)
                box("mug", "the coffee mug in `photo`")
            }
            r.box("mug").box shouldBe BoundingBox(top = 0.25, bottom = 0.82, left = 0.44, right = 0.84)
            jev.requests.single().headers[IMAGES_HEADER] shouldBe "true"
        }

        "extraBody fields go into the request part" {
            val photo = JevImage.of(PNG_BYTES, "<photo>")
            val jev = testJev { respondJson(NO_ANSWERS) }
            val options = JevCallOptions { extraBody["note"] = JsonPrimitive("hello") }
            jev.client.withOptions(options).query("A photo: $photo") {
                attach(photo)
                noul("cat", "Is there a cat in the photo?")
            }
            json(jev.requests.single().parts()[0].text).jsonObject["note"] shouldBe JsonPrimitive("hello")
        }

        "a retry sends the same multipart bytes" {
            val photo = JevImage.of(PNG_BYTES, "<photo>")
            val bodies = mutableListOf<ByteArray>()
            var calls = 0
            val jev = testJev(retry = RetryPolicy()) { request ->
                bodies += request.body.toByteArray()
                if (++calls == 1) respondJson("{}", HttpStatusCode.ServiceUnavailable) else respondJson(NO_ANSWERS)
            }
            jev.client.query("A photo: $photo") {
                attach(photo)
                noul("cat", "Is there a cat in the photo?")
            }
            bodies shouldHaveSize 2
            bodies[0] shouldBe bodies[1]
            bodies[0].size shouldBeGreaterThan PNG_BYTES.size
        }

        "ask attaches per-call images after the query's own, sending each instance once" {
            val jev = testJev { respondJson(NO_ANSWERS) }
            val frame = JevImage.of(JPEG_BYTES, "<frame>")
            jev.client.ask(Gestures, buildJsonObject { put("camera", frame.reference) }, frame, Gestures.thumb)
            val parts = jev.requests.single().parts()
            parts.map { it.name } shouldBe listOf("request", "image-0", "image-1", "image-2")
            json(parts[0].text).jsonObject["assets"]!!.jsonObject.keys shouldBe setOf("<thumb>", "<palm>", "<frame>")
        }

        "one image placed in the state and in two questions is sent once" {
            val jev = testJev { respondJson(NO_ANSWERS) }
            val photo = JevImage.of(PNG_BYTES, "<photo>")
            jev.client.query(buildJsonObject { put("photo", photo.reference) }) {
                attach(photo)
                noul("cat", "Is $photo a cat?")
                noul("dog", "Is $photo a dog?")
            }
            val parts = jev.requests.single().parts()
            parts.map { it.name } shouldBe listOf("request", "image-0")
            json(parts[0].text).jsonObject["assets"]!!.jsonObject.keys shouldBe setOf("<photo>")
        }

        "images in a nested @Serializable state are written as references and sent in attach order" {
            val jev = testJev { respondJson(NO_ANSWERS) }
            val frames = listOf(2, 1, 0).map { Frame(it, JevImage.of(PNG_BYTES, "<f$it>")) }
            jev.client.query(History(frames)) {
                attach(*frames.map { it.frame }.toTypedArray())
                noul("opened", "Did the door in `frames` open during these frames?")
            }
            val parts = jev.requests.single().parts()
            json(parts[0].text).jsonObject["state"] shouldBe
                json("""{"frames":[{"secondsAgo":2,"frame":"<f2>"},{"secondsAgo":1,"frame":"<f1>"},
                    {"secondsAgo":0,"frame":"<f0>"}]}""")
            json(parts[0].text).jsonObject["assets"]!!.jsonObject.keys.toList() shouldBe listOf("<f2>", "<f1>", "<f0>")
        }

        "a short chosen reference inside a longer word counts as referenced, as the API's plain-text rule has it" {
            val jev = testJev { respondJson(NO_ANSWERS) }
            jev.client.query("Pick a category.") {
                attach(JevImage.of(PNG_BYTES, "cat"))
                noul("ok", "Is this fine?")
            }
            jev.requests shouldHaveSize 1
        }

        "every pre-send problem is reported at once, and nothing is sent" {
            val jev = testJev { respondJson(NO_ANSWERS) }
            val stray = JevImage.of(PNG_BYTES, "<stray>")
            val keyOnly = JevImage.of(PNG_BYTES, "<key>")
            val unattached = JevImage.of(JPEG_BYTES)
            val e = shouldThrow<JevValidationException> {
                jev.client.query("Is $unattached a cat?") {
                    attach(stray, keyOnly)
                    choice("pick", "Which?") { options("<key>", "other") }
                }
            }
            e.problems shouldBe
                listOf(
                    "image '<stray>' is attached but appears in no string in the state, instructions or criteria",
                    "image '<key>' is attached but appears in no string in the state, instructions or criteria; " +
                        "it appears only as an object key, which the API never replaces: put it in a value, such as " +
                        "the option's description",
                    "image '${unattached.reference}' is placed in the request but not attached; attach() it",
                )
            jev.requests.shouldBeEmpty()
        }

        "a Box question with no image attached is refused" {
            val jev = testJev { respondJson(NO_ANSWERS) }
            shouldThrow<JevValidationException> {
                jev.client.query("No picture here.") { box("mug", "the coffee mug") }
            }.problems shouldBe listOf("a Box question needs an image attached to the request")
        }

        "extraBody can't set assets on a request with images, but still can on one without" {
            val jev = testJev { respondJson(NO_ANSWERS) }
            val raw = jev.client.withOptions(JevCallOptions { extraBody["assets"] = JsonObject(emptyMap()) })
            val photo = JevImage.of(PNG_BYTES, "<photo>")
            shouldThrow<JevValidationException> {
                raw.query("A photo: $photo") {
                    attach(photo)
                    noul("cat", "Is there a cat in the photo?")
                }
            }.problems shouldBe listOf("extraBody can't set 'assets' when the request attaches images")

            raw.query(PAYOUT_TICKET) { noul("urgent", "Is this urgent?") }
            jev.requests.single().bodyJson().jsonObject["assets"] shouldBe JsonObject(emptyMap())
        }

        "references over 64 KiB in total are refused" {
            val jev = testJev { respondJson(NO_ANSWERS) }
            val images = (0 until 65).map { JevImage.of(PNG_BYTES, it.toString().padStart(1024, 'r')) }
            shouldThrow<JevValidationException> {
                jev.client.query(JsonArray(images.map { JsonPrimitive(it.reference) })) {
                    attach(*images.toTypedArray())
                    noul("any", "Is any of these a cat?")
                }
            }.problems shouldBe listOf("image references total 66560 bytes of UTF-8, over the API's limit of 65536")
        }
    }
}
```

- [ ] **Step 3: Run it to make sure it fails**

Run: `./gradlew jvmTest --tests "com.pambrose.jev4k.ImageRequestTest"`
Expected: compilation fails, with `Unresolved reference 'IMAGES_HEADER'`.

- [ ] **Step 4: Write `ImageRequest.kt`**

Create `src/commonMain/kotlin/com/pambrose/jev4k/internal/ImageRequest.kt`:

```kotlin
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
                add("image '${image.reference}' is attached but appears in no string in the state, instructions or " +
                    "criteria$hint")
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
```

- [ ] **Step 5: Send it from `JevClient`**

In `src/commonMain/kotlin/com/pambrose/jev4k/JevClient.kt`, import `com.pambrose.jev4k.internal.IMAGES_HEADER`,
`assetsJson`, `imageRequestProblems`, `multipartBody` and `needsImagesHeader`. Replace the whole four-argument
`evaluate` with:

```kotlin
    override suspend fun evaluate(
        state: JsonElement,
        questions: QuestionSet,
        model: String?,
        options: JevCallOptions,
    ): JevResult {
        val problems = listOfNotNull(stateProblem(state)) + imageRequestProblems(state, questions, options.extraBody)
        if (problems.isNotEmpty()) throw JevValidationException(problems)
        val resolvedModel = model.setting() ?: config.defaultModel
        val request = SystemOneRequest(state, resolvedModel, questions.toWire())
        val images = questions.images
        val builtIn = if (needsImagesHeader(questions)) mapOf(IMAGES_HEADER to "true") else emptyMap()
        return send(HttpMethod.Post, SYSTEM_ONE_PATH, options, builtIn, {
            // Encoded here, inside the call, so a state that can't be encoded still becomes a JevValidationException.
            // jev4k encodes the body itself, extra fields and all, rather than installing Ktor's ContentNegotiation
            // plugin for this one object; responses are read as raw text anyway. With images, the same JSON is the
            // first part of a multipart body, with an assets field naming the parts that follow.
            val fields = JevJson.encodeToJsonElement(SystemOneRequest.serializer(), request).jsonObject
            val assets = if (images.isEmpty()) emptyMap() else mapOf("assets" to assetsJson(images))
            val text = JevJson.encodeToString(JsonObject.serializer(), JsonObject(fields + options.extraBody + assets))
            setBody(if (images.isEmpty()) TextContent(text, ContentType.Application.Json) else multipartBody(text, images))
        }) { body, response -> mapSystemOne(body, resolvedModel, questions, response) }
    }
```

Change `models(options)` to pass no built-in headers: `send(HttpMethod.Get, MODELS_PATH, options, emptyMap(), {},
::mapModels)`. Add `builtInHeaders: Map<String, String>,` to `send`'s parameters after `options`, and call
`setHeaders(options, builtInHeaders)`. Replace `setHeaders` with:

```kotlin
    /**
     * Every header, set rather than appended, in order of precedence: the built-in ones (with [builtIn], such as the
     * images opt-in), then the client's, then the call's, so each replaces an earlier one of the same name instead of
     * sending two values.
     */
    private fun HttpRequestBuilder.setHeaders(
        options: JevCallOptions,
        builtIn: Map<String, String>,
    ) {
        headers[HttpHeaders.Authorization] = "Bearer ${config.apiKey}"
        accept(ContentType.Application.Json)
        headers[HttpHeaders.UserAgent] = USER_AGENT
        builtIn.forEach { (name, value) -> headers[name] = value }
        config.headers.forEach { (name, value) -> headers[name] = value }
        options.headers.forEach { (name, value) -> headers[name] = value }
    }
```

- [ ] **Step 6: Add the `ask` overloads**

Append to `src/commonMain/kotlin/com/pambrose/jev4k/JevApi.kt`. The bodies call `evaluate` and `questions {}`
directly, because inside them `query` names the `JevQuery` parameter.

```kotlin
/** Asks a [JevQuery]'s questions about a text [state], with [images] attached after the query's own. */
@ExperimentalJevImages
suspend fun JevApi.ask(
    query: JevQuery,
    state: String,
    vararg images: JevImage,
    model: String? = null,
): JevResult = evaluate(JsonPrimitive(state), questionsWith(query, images), model)

/** Asks a [JevQuery]'s questions about a JSON [state], with [images] attached after the query's own. */
@ExperimentalJevImages
suspend fun JevApi.ask(
    query: JevQuery,
    state: JsonElement,
    vararg images: JevImage,
    model: String? = null,
): JevResult = evaluate(state, questionsWith(query, images), model)

/** Asks a [JevQuery]'s questions about a `@Serializable` [state], with [images] attached after the query's own. */
@ExperimentalJevImages
@JvmSynthetic
suspend inline fun <reified T : Any> JevApi.ask(
    query: JevQuery,
    state: T,
    vararg images: JevImage,
    model: String? = null,
): JevResult = evaluate(jsonEntry(state), questionsWith(query, images), model)

@PublishedApi
internal fun questionsWith(
    query: JevQuery,
    images: Array<out JevImage>,
): QuestionSet =
    questions {
        include(query)
        attach(*images)
    }
```

Overload resolution keeps every existing call on the old functions: with no images, a candidate without a `vararg` is
more specific. `ClientTest`'s `ask` tests pin that.

- [ ] **Step 7: Mirror them on `BlockingJev`**

In `src/jvmMain/kotlin/com/pambrose/jev4k/BlockingJev.jvm.kt`, add after the last `ask`:

```kotlin
    @ExperimentalJevImages
    @JvmOverloads
    @Throws(InterruptedException::class)
    fun ask(
        query: JevQuery,
        state: String,
        vararg images: JevImage,
        model: String? = null,
    ): JevResult = runBlocking { api.ask(query, state, *images, model = model) }

    @ExperimentalJevImages
    @JvmOverloads
    @Throws(InterruptedException::class)
    fun ask(
        query: JevQuery,
        state: JsonElement,
        vararg images: JevImage,
        model: String? = null,
    ): JevResult = runBlocking { api.ask(query, state, *images, model = model) }

    @ExperimentalJevImages
    @JvmSynthetic
    @Throws(InterruptedException::class)
    inline fun <reified T : Any> ask(
        query: JevQuery,
        state: T,
        vararg images: JevImage,
        model: String? = null,
    ): JevResult = runBlocking { api.ask(query, state, *images, model = model) }
```

- [ ] **Step 8: Add the blocking test**

In `src/jvmTest/kotlin/com/pambrose/jev4k/BlockingJevTest.kt`, inside `init`, add:

```kotlin
        "ask with images attaches them after the query's own" {
            val (api, blocking) = answering()
            val frame = JevImage.of(JPEG_BYTES, "<frame>")
            blocking.ask(Gestures, jsonState, frame)
            coVerify(exactly = 1) {
                api.evaluate(
                    jsonState,
                    match { set -> set.images.map { it.reference } == listOf("<thumb>", "<palm>", "<frame>") },
                    null,
                )
            }
        }
```

- [ ] **Step 9: Run the tests**

Run: `./gradlew jvmTest --tests "com.pambrose.jev4k.ImageRequestTest" --tests "com.pambrose.jev4k.BlockingJevTest" --tests "com.pambrose.jev4k.ClientTest" --tests "com.pambrose.jev4k.CallOptionsTest" --tests "com.pambrose.jev4k.ConsumerTest"`
Expected: PASS. If Ktor writes the part name quoted (`name="image-0"`), check whether Ktor's `escapeIfNeeded` quotes
`-` before changing anything. Change the two `Content-Disposition` expectations only if it does.

- [ ] **Step 10: Update the ABI dumps, run everything, check coverage**

Run: `make abi-update`, then `git diff api/ | grep '^-' | grep -v '^---'`
Expected: the second command prints nothing. The additions are the three `JevApiKt.ask` overloads, the three
`BlockingJev.ask` overloads (plus the `@JvmOverloads` variants), and `JevApiKt.questionsWith` (a `@PublishedApi`
function, so it appears in the dump).

Run: `make tests`, then `make coverage-verify`
Expected: BUILD SUCCESSFUL for both. The Kover line and branch floors still hold.

- [ ] **Step 11: Ready to commit**

Show the diff summary. Commit only when the user says so, with the message "Send images as multipart, with the images
header".

---

### Task 5: Java interop and the live test

**Files:**
- Modify: `src/jvmTest/java/website/JavaInterop.java`
- Modify: `src/jvmTest/kotlin/com/pambrose/jev4k/LiveSmokeTest.kt`
- Modify: `jev-docs/jev-api.md` (the filename probe's result)

**Interfaces:**
- Consumes: `JevImage.of`, `QueryBuilder.attach`, `QueryBuilder.box`, `JevResult.box`, `BlockingJev.query`
- Produces: the `images` snippet section in `JavaInterop.java`, used by Task 6's page

- [ ] **Step 1: Add the Java example**

In `src/jvmTest/java/website/JavaInterop.java`, add `import com.pambrose.jev4k.JevImage;` and
`import com.pambrose.jev4k.BoundingBox;` in import order. Then add this method to the class, after `testing()`:

```java
    // --8<-- [start:images]
    static BoundingBox findMug(BlockingJev blocking, byte[] photoBytes) throws InterruptedException {
        JevImage photo = JevImage.of(photoBytes);
        JevResult r = blocking.query("The photo: " + photo.getReference(), qb -> {
            qb.attach(photo);
            qb.noul("mug_visible", "Is there a coffee mug in the photo?");
            qb.box("mug", "the coffee mug in the photo");
            return Unit.INSTANCE;
        });
        return r.box("mug").getBox();
    }
    // --8<-- [end:images]
```

Run: `./gradlew compileJvmTestJava`
Expected: BUILD SUCCESSFUL. Java ignores the opt-in annotation, and `attach` takes a Java vararg.

- [ ] **Step 2: Add the live test**

In `src/jvmTest/kotlin/com/pambrose/jev4k/LiveSmokeTest.kt`, add these imports: `java.awt.Color`,
`java.awt.image.BufferedImage`, `java.io.ByteArrayOutputStream`, `javax.imageio.ImageIO`,
`io.kotest.matchers.doubles.shouldBeGreaterThan`, `io.kotest.matchers.doubles.shouldBeLessThan`,
`kotlinx.serialization.json.buildJsonObject` and `kotlinx.serialization.json.put`. Then add this test inside `init`:

```kotlin
        "an image and a Box question come back as typed answers".liveTest {
            val photo = JevImage.of(redSquarePng())
            JevClient().use { jev ->
                val r = jev.query(buildJsonObject { put("photo", photo.reference) }, model = "jev-preview") {
                    attach(photo)
                    noul("square", "Is there a red square in `photo`?")
                    box("where", "the red square in `photo`")
                }
                r.noul("square").noul shouldBeGreaterThan 0.5
                val box = r.box("where").box
                box.left shouldBeLessThan 0.7
                box.right shouldBeGreaterThan 0.7
                box.top shouldBeLessThan 1.0 / 3
                box.bottom shouldBeGreaterThan 1.0 / 3
            }
        }
```

and this function to the class, after `init`:

```kotlin
    /** A 400 × 300 white PNG with a red square centered at (280, 100): 0.7 of the width, a third of the height. */
    private fun redSquarePng(): ByteArray {
        System.setProperty("java.awt.headless", "true")
        val image = BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, 400, 300)
        graphics.color = Color.RED
        graphics.fillRect(240, 60, 80, 80)
        graphics.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }
```

- [ ] **Step 3: Run the live test (spends a few tokens)**

Ask the user before this step, since it spends tokens.
Run: `JEV4K_LIVE=1 ./gradlew jvmTest --rerun --tests "com.pambrose.jev4k.LiveSmokeTest"`
Expected: PASS, every test. If the API returns a 400, read its body in the failure. It names what it rejected (the
header, the image or a multipart rule): fix that, not the assertion.

- [ ] **Step 4: Probe whether image parts need a filename (spends a few tokens)**

Ask the user first. This is a one-off check, not a kept test. Write the probe image and send one part without a
filename (`<` makes curl send the file's bytes with no filename):

```bash
S=/private/tmp/claude-501/-Users-pambrose-git-jev4k/f1a2e0a7-73d0-4907-8f3c-3c4fca5f5d2f/scratchpad
python3 -I - "$S/probe.png" <<'EOF'
import struct, sys, zlib
w = h = 300
raw = b"".join(b"\x00" + b"\xff\x00\x00" * w for _ in range(h))
def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
png += chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
open(sys.argv[1], "wb").write(png)
EOF
set -a; . ./.env; set +a
curl -sS https://api.typesafe.ai/v1/systemone \
  -H "Authorization: Bearer $TYPESAFE_API_KEY" -H "TypeSafe-Danger-Zone-Images: true" \
  -F 'request={"model":"jev-latest","state":{"photo":"probe-ref"},"assets":{"probe-ref":{"type":"image","part":"image-0"}},"questions":{"red":{"type":"noul","instructions":"Is `photo` red?"}}};type=application/json' \
  -F "image-0=<$S/probe.png;type=image/png"
```

Expected: either JSON with `answers.red` (no filename needed) or a 400 naming the part. In both cases jev4k keeps
sending the filename.

- [ ] **Step 5: Record the probe in the API notes**

In `jev-docs/jev-api.md` §2, "Images and Box", in the **Multipart** bullet, append one sentence with the date and the
outcome, in the same style as the nesting probe. For example: "A part sent without a filename is accepted (probed
2026-10-16); jev4k sends one anyway, as curl does."

- [ ] **Step 6: Ready to commit**

Show the diff summary. Commit only when the user says so, with the message "Cover images from Java and against the live
API".

---

### Task 6: Documentation, changelog and project notes

**Files:**
- Create: `src/jvmTest/kotlin/website/ImageExamples.kt`
- Create: `website/jev4k/docs/queries/images.md`
- Modify: `website/jev4k/zensical.toml` (nav)
- Modify: `website/jev4k/docs/llms.txt`
- Modify: `website/jev4k/docs/concepts.md:112-114`
- Modify: `website/jev4k/docs/queries/state.md:63-65`
- Modify: `website/jev4k/docs/getting-started/installation.md:113-114`
- Modify: `README.md` (contents, lines 169-170, 317-318, 482-483, a new "Images" section)
- Modify: `CHANGELOG.md` (Unreleased)
- Modify: `jev-docs/jev-api.md:360-361` (§10)
- Modify: `CLAUDE.md` (Architecture, Build)

**Interfaces:**
- Consumes: every public name from Tasks 1–5.
- Produces: snippet sections `ImageExamples.kt:state`, `:instructions`, `:criteria`, `:serializable`,
  `:references`, `:box` and `:crop`, plus `JavaInterop.java:images`.

- [ ] **Step 1: Check that the example names are free**

Run: `grep -rn -E "fun (checkDelivery|sameProduct|readGesture|checkClaim|matchesCatalog|findMug|cropToBox)\b|(object|class) (HandGestures|ReturnClaim)\b" src/jvmTest/kotlin/website`
Expected: no output.

- [ ] **Step 2: Write the examples**

Create `src/jvmTest/kotlin/website/ImageExamples.kt`:

```kotlin
package website

import com.pambrose.jev4k.BoundingBox
import com.pambrose.jev4k.ExperimentalJevImages
import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.JevImage
import com.pambrose.jev4k.JevQuery
import com.pambrose.jev4k.ask
import com.pambrose.jev4k.entry
import com.pambrose.jev4k.jsonOf
import com.pambrose.jev4k.query
import com.pambrose.jev4k.read
import kotlinx.serialization.Serializable
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.math.roundToInt

// --8<-- [start:state]
@OptIn(ExperimentalJevImages::class)
suspend fun checkDelivery(jev: JevApi) {
    val door = JevImage.read(Path.of("door-camera.jpg"))       // read now, with a generated reference
    val result =
        jev.query(jsonOf(mapOf("door" to door))) {               // placed in the state as its reference
            attach(door)                                         // and attached, so its bytes are sent
            noul("person", "Is there a person in `door`?")
            choice("carrying", "What is the person in `door` carrying?") {
                "package" means "A box or parcel."
                "bag" means "A bag of groceries or shopping."
                "nothing" means "Empty hands."
            }
        }
    println(result.noul("person").noul)
}
// --8<-- [end:state]

// --8<-- [start:instructions]
suspend fun sameProduct(
    jev: JevApi,
    upload: JevImage,
    catalog: JevImage,
): Boolean {
    val result =
        jev.query("A seller uploaded $upload for a listing whose catalog photo is $catalog.") {
            attach(upload, catalog)
            noul("same", "Does $upload show the same product as $catalog?")
        }
    return result.noul("same").isTrue()
}
// --8<-- [end:instructions]

// --8<-- [start:criteria]
object HandGestures : JevQuery() {
    private val thumb = attach(JevImage.read(Path.of("gestures/thumb.png")))
    private val palm1 = attach(JevImage.read(Path.of("gestures/palm1.png")))
    private val palm2 = attach(JevImage.read(Path.of("gestures/palm2.png")))

    val gesture by choice("Which gesture is the hand in `camera` making?") {
        "thumbs_up" means thumb                                  // an option that is a picture
        "open_palm" means
            entry(
                "description" to "A hand held up with the palm facing the camera.",
                "examples" to listOf(palm1, palm2),              // or a description with example pictures
            )
        "none" means "No hand, or a hand that matches none of the examples."
    }
}

suspend fun readGesture(
    jev: JevApi,
    frameBytes: ByteArray,
) {
    val frame = JevImage.of(frameBytes)
    val result = jev.ask(HandGestures, jsonOf(mapOf("camera" to frame)), frame)   // the query's pictures, then this one
    println(result[HandGestures.gesture].choice)
}
// --8<-- [end:criteria]

// --8<-- [start:serializable]
@Serializable
data class ReturnClaim(
    val claim: String,
    val photo: JevImage,                                         // written as its reference
)

suspend fun checkClaim(
    jev: JevApi,
    claim: ReturnClaim,
) {
    val result =
        jev.query(claim) {
            attach(claim.photo)
            noul("damaged", "Is the product in `photo` damaged?")
            score("severity", "How severe is the damage in `photo`?") { levels("None", "Cosmetic", "Unusable") }
        }
    println(result.score("severity").nearestLevel)
}
// --8<-- [end:serializable]

// --8<-- [start:references]
suspend fun matchesCatalog(
    jev: JevApi,
    delivered: ByteArray,
    catalog: ByteArray,
): Double {
    val result =
        jev.query("A customer returned <delivered>. The catalog photo is <catalog>.") {
            attach(JevImage.of(delivered, reference = "<delivered>"), JevImage.of(catalog, reference = "<catalog>"))
            noul("matches", "Is <delivered> the product shown in <catalog>?")
        }
    return result.noul("matches").noul
}
// --8<-- [end:references]

// --8<-- [start:box]
suspend fun findMug(
    jev: JevApi,
    photo: JevImage,
) {
    val result =
        jev.query(jsonOf(mapOf("photo" to photo)), model = "jev-preview") {
            attach(photo)
            noul("mug_visible", "Is there a coffee mug in `photo`?")  // a Box always returns a box, so ask
            box("mug", "the coffee mug in `photo`")
        }
    if (result.noul("mug_visible").isTrue()) {
        val (top, bottom, left, right) = result.box("mug").box       // fractions of the height and the width
        println("mug across ${left..right} of the width, ${top..bottom} of the height")
    }
}
// --8<-- [end:box]

// --8<-- [start:crop]
fun cropToBox(
    image: BufferedImage,
    box: BoundingBox,
): BufferedImage {
    val x = (box.left * image.width).roundToInt()
    val y = (box.top * image.height).roundToInt()
    val width = ((box.right - box.left) * image.width).roundToInt().coerceIn(1, image.width - x)
    val height = ((box.bottom - box.top) * image.height).roundToInt().coerceIn(1, image.height - y)
    return image.getSubimage(x, y, width, height)
}
// --8<-- [end:crop]
```

Check the trailing-comment columns by eye within each run of consecutive lines, and keep every line within 120
columns.

Run: `./gradlew compileTestKotlinJvm lintKotlin detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Write the page**

Create `website/jev4k/docs/queries/images.md`:

````markdown
---
icon: lucide/image
---

# Images & Box

Jev reads PNG and JPEG images as well as text. An image can go anywhere text can: in the state, in a question's
instructions, and in its criteria. The Box question type finds where something is in an image.

!!! warning "Experimental"

    TypeSafe calls images and Box "Dreamer-ready": the request format, the answers and the limits can change between
    its releases, and so can this part of jev4k. Use them for prototypes, and see TypeSafe's
    [Images](https://docs.typesafe.ai/images) and [Box](https://docs.typesafe.ai/primitives/box) pages.

## Turning images on

Everything here is marked `@ExperimentalJevImages`. Opt in with `@OptIn(ExperimentalJevImages::class)` on the code
that uses it, or for a whole module with `optIn.add("com.pambrose.jev4k.ExperimentalJevImages")` in Gradle's
`compilerOptions`. jev4k then sends TypeSafe's `TypeSafe-Danger-Zone-Images: true` header with every request that
carries an image or a Box question. A header of the same name in the client's or the call's headers replaces it.

## Placing and attaching

A `JevImage` comes from bytes (`JevImage.of`), base64 text (`JevImage.fromBase64`), or a file (`JevImage.read`, with a
kotlinx-io `Path` anywhere, or a `java.nio.file.Path` or `File` on the JVM). Each one has a reference, a string that
stands for the image wherever it is placed. Its `toString()` is that reference, so string templates, `jsonOf`, `entry`
and `@Serializable` fields all place it. The API replaces every occurrence of a reference with the image.

Placing an image doesn't send it. `attach` it in the query block, or in a `JevQuery`, so its bytes go with the
questions. jev4k refuses to send a request that attaches an image nothing references, or that places a generated
reference without attaching its image.

```kotlin
--8<-- "ImageExamples.kt:state"
```

## Images in instructions

An image only one question needs can go straight into its instructions. An image several questions use belongs in the
state, where it is sent once; every place a reference appears adds another copy of the image to what the model reads.

```kotlin
--8<-- "ImageExamples.kt:instructions"
```

## Images in criteria

Choice options, Score levels and Noul definitions can be pictures, or descriptions with example pictures. Option names
are never replaced, so the picture goes in the option's description. A `JevQuery` attaches its own pictures, which
travel with every request that asks it; `ask(query, state, images...)` adds the call's own images after them.

```kotlin
--8<-- "ImageExamples.kt:criteria"
```

`JevImage.read` in an object's initializer, as above, reads the file there, and a missing file fails the object's
first use with an `ExceptionInInitializerError`. When that matters, read the bytes elsewhere and use `JevImage.of`.

A `@Serializable` field of type `JevImage` is written as its reference:

```kotlin
--8<-- "ImageExamples.kt:serializable"
```

## Your own references

A generated reference is `jev4k-image-` followed by a UUID. You can choose your own, for prompts kept in
configuration, but the API matches references as plain text anywhere inside a string: a reference `cat` also matches
inside `category`. Use delimiters that can't occur in your text.

```kotlin
--8<-- "ImageExamples.kt:references"
```

## Box

A Box question finds one target and answers with the box around it. Each edge is a fraction, from 0 to 1, of the
image's height (`top`, `bottom`) or width (`left`, `right`), measured from the top-left corner. A Box always returns a
box, even when the target isn't there, so ask a Noul in the same request. Box needs Jev 1.14 (`jev-preview` or
`jev-1.14.0`).

```kotlin
--8<-- "ImageExamples.kt:box"
```

To crop to the box, multiply each edge by the image's size in pixels:

```kotlin
--8<-- "ImageExamples.kt:crop"
```

## What jev4k checks

Before sending, jev4k reports every problem at once, as a `JevValidationException`:

- an image that is empty, isn't valid base64, or is neither a PNG nor a JPEG
- a reference that is blank or longer than 1,024 bytes, two images sharing a reference, or references over 64 KiB in
  total
- an attached image that no string references, or a generated reference placed without its image attached
- a Box question with no image attached, or `extraBody["assets"]` on a request that attaches images

The API enforces its own size and pixel limits (each side 256 to 1568 pixels, 16 MiB per file, 32 MiB per request),
answering with a 400 or a 413. Resize and rotate images before sending them; the API strips EXIF data and doesn't apply
its orientation.

A request with images is sent as `multipart/form-data`, the JSON body first and then one part per image, as the
official SDKs send it. Images count as input tokens at the text price.

If you cache results on the model, the state and the questions, include each attached image's bytes in the key too:
`QuestionSet.toJson()` holds only the references.

## From Java

Java ignores the opt-in annotation. Build an image with `JevImage.of(Files.readAllBytes(path))`:

```java
--8<-- "JavaInterop.java:images"
```
````

- [ ] **Step 4: Add the page to the nav and `llms.txt`**

In `website/jev4k/zensical.toml`, after `{ "State" = "queries/state.md" },`, add
`        { "Images & Box" = "queries/images.md" },`.

In `website/jev4k/docs/llms.txt`, after the State line, add:

```markdown
- [Images & Box](https://jev4k.com/queries/images/): experimental: PNG and JPEG images in the state, instructions and criteria, attached with `attach()` and sent as multipart, and Box questions that locate a target
```

- [ ] **Step 5: Update the pages that say jev4k has no images**

In `website/jev4k/docs/concepts.md`, replace lines 112–114 with:

```markdown
Jev reads text: strings, JSON objects, and arrays of text. It also reads PNG and JPEG images, and its Box question
type finds where something is in an image; both are experimental, as [Images & Box](queries/images.md) explains.
English is Jev's primary language; other languages work, but test them on your own data. It isn't trained on customer
requests.
```

In `website/jev4k/docs/queries/state.md`, replace the **Text.** bullet (lines 63–65) with:

```markdown
- **Text, and images.** Jev reads text: strings, JSON objects, and arrays of text. PNG and JPEG images can go in the
  state too, experimentally; see [Images & Box](images.md). Convert audio and other binary data to text or fields
  first.
```

In `website/jev4k/docs/getting-started/installation.md`, lines 113–114, change "four `compile` dependencies
(`ktor-client-core`, `kotlinx-serialization-json`, `kotlinx-coroutines-core`, `kotlin-stdlib`)" to "five `compile`
dependencies (`ktor-client-core`, `kotlinx-serialization-json`, `kotlinx-coroutines-core`, `kotlinx-io-core`,
`kotlin-stdlib`)", and re-wrap the paragraph at 120 columns.

- [ ] **Step 6: Update the README**

- Contents: after `- [State](#state)`, add `- [Images](#images)`.
- Lines 169–170: replace with

```markdown
TypeSafe's experimental Box question type finds where something is in an image. jev4k supports it, and images in the
state, instructions and criteria, behind the `@ExperimentalJevImages` opt-in; see [Images](#images).
```

- Lines 317–318: replace "Jev reads text, and English works best. TypeSafe's image support is experimental, and jev4k
  doesn't send images yet." with "Jev reads text, and English works best. Images can go in the state too,
  experimentally; see [Images](#images).", re-wrapped.
- Lines 482–483: make the same four-to-five `compile` dependency change as on the Installation page.
- Before `## Reading results`, add this section. The trailing comments are aligned:

````markdown
## Images

TypeSafe's image support is experimental, so jev4k marks it `@ExperimentalJevImages`. A `JevImage` stands in content
as its reference; `attach` it so its bytes are sent. jev4k adds the `TypeSafe-Danger-Zone-Images: true` header and
sends the request as multipart.

```kotlin
@OptIn(ExperimentalJevImages::class)
suspend fun findMug(jev: JevApi, bytes: ByteArray) {
    val photo = JevImage.of(bytes)                                   // or JevImage.read(path)
    val result =
        jev.query(jsonOf(mapOf("photo" to photo)), model = "jev-preview") {
            attach(photo)                                            // placing an image doesn't send it
            noul("mug_visible", "Is there a coffee mug in `photo`?")
            box("mug", "the coffee mug in `photo`")                  // a Box finds where it is
        }
    if (result.noul("mug_visible").isTrue()) println(result.box("mug").box)
}
```

Images also go in instructions (`"Is $upload the same product as $catalog?"`) and criteria (`"thumbs_up" means
thumb`). A `JevQuery` can attach its own example pictures. See [Images & Box](https://jev4k.com/queries/images/).
````

- [ ] **Step 7: Update the changelog**

In `CHANGELOG.md` under `## [Unreleased]`, add to `### Added`:

```markdown
- Experimental support for TypeSafe's images and Box questions, behind the new `@ExperimentalJevImages` opt-in.
  `JevImage` (from bytes, base64 or a file) is placed anywhere content goes as its reference and attached with
  `QueryBuilder.attach`, `JevQuery.attach` or the new `ask(query, state, images...)`. A request with images is sent as
  `multipart/form-data`, with the `TypeSafe-Danger-Zone-Images: true` header added for you. `box(...)` asks a Box
  question, read back as a `BoxAnswer` through `result.box(id)` or `result.boxes`. Structural problems, such as an
  image nothing references or one placed but not attached, fail before sending.
```

Under `### Changed`, replace the bullet beginning "The README and the site no longer say Jev reads only text" with:

```markdown
- `Question` and `Answer` gain the subtypes `BoxQuestion` and `BoxAnswer`, so an exhaustive `when` over either without
  an `else` branch no longer compiles. Naming either subtype needs the `@ExperimentalJevImages` opt-in.
- `kotlinx-io-core` (0.9.1, already brought by Ktor) is now a declared `api` dependency, since `JevImage.read` takes a
  kotlinx-io `Path`. The JVM POM lists five compile dependencies.
- The README and the site describe images and Box, which jev4k now supports, instead of saying it doesn't.
```

- [ ] **Step 8: Update the API notes and CLAUDE.md**

In `jev-docs/jev-api.md` §10, replace the bullet "Images and Box aren't modeled by jev4k. …" with:

```markdown
- jev4k models images and Box behind `@ExperimentalJevImages`
  (`docs/superpowers/specs/2026-10-15-image-support-design.md`): images attach to the `QuestionSet`, a request with
  images is sent as multipart, and the opt-in header is added automatically.
```

In `CLAUDE.md`, under **Architecture**, after the **Answers** bullet, add:

```markdown
- **Images and Box** (`JevImage.kt`, `internal/ImageRequest.kt`), all behind `@ExperimentalJevImages`, which the build
  opts the module into (`compilerOptions.optIn`), so every public declaration that names an image or Box type must
  carry the annotation by hand.
    - A `JevImage` stands in content as its `reference` (`toString()`, `jsonOf`, `JevImageSerializer`, the builders'
      image overloads); `JsonPrimitive` is sealed, so images can't be discovered in a JSON tree and must be attached:
      `QueryBuilder.attach`, `JevQuery.attach`, or `ask(query, state, images...)`. `QuestionSet.images` holds each
      instance once.
    - The factories never throw for bad content: the problem rides on the image and `imageDefinitionProblems` reports
      it when the set is validated, keeping definition errors out of `JevQuery` initializers. Only a file read throws.
    - `evaluate` runs `imageRequestProblems` (references matched as the API does, as plain text in string values,
      never keys), adds `TypeSafe-Danger-Zone-Images: true` as a built-in header, and sends a request with images as
      `MultiPartFormDataContent`: a `request` part with the JSON and a generated `assets` map, then `image-0`, … with a
      filename. Without images the request is the same `TextContent` as before.
    - `BoxQuestion`/`BoxAnswer` are the fourth question and answer types; the mapper requires edges in 0..1 with
      `left < right` and `top < bottom`.
```

In the **Build** section, extend the `compilerOptions.optIn` bullet to mention `com.pambrose.jev4k.ExperimentalJevImages`.
Change the `ktor-client-core`, `kotlinx-serialization-json` and `kotlinx-coroutines-core` `api` bullet to include
`kotlinx-io-core` (`JevImage.read` takes a kotlinx-io `Path`). In the paragraph on the JVM jar's manifest, change "five
POM dependencies (in `jev4k-jvm`'s POM: four compile, CIO at runtime)" to "six POM dependencies (in `jev4k-jvm`'s POM:
five compile, CIO at runtime)". Under **Testing**, add one line: "`TestSupport.kt`'s `parts()` splits a MockEngine
request's multipart body, for `ImageRequestTest`; `PNG_BYTES`/`JPEG_BYTES` are signature-only fixtures."

- [ ] **Step 9: Verify the POM, the site and the whole build**

Run: `./gradlew generatePomFileForJvmPublication && grep -A2 "kotlinx-io-core" build/publications/jvm/pom-default.xml`
Expected: a `<dependency>` for `org.jetbrains.kotlinx:kotlinx-io-core` with `<scope>compile</scope>`.

Run: `make tests`
Expected: BUILD SUCCESSFUL.

Run: `cd website/jev4k && uv run zensical build --clean --strict`
Expected: "No issues found". Open `site/queries/images/index.html` and check that the warning admonition's `<div>`
holds its body paragraph, not just its title.

Run: `make coverage-verify`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Ready to commit**

Show the diff summary. Commit only when the user says so, with the message "Document images and Box".
