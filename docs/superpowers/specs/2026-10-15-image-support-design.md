# Image and Box support: design

Status: design approved in conversation on 2026-10-15; awaiting review of this written spec.

## 1. Goal and constraints

**Goal.** Parity with TypeSafe's Python (0.7.5) and JS (0.7.0) SDKs for the experimental image features described in
`jev-docs/pages/images.md` and `jev-docs/pages/primitives/box.md` (summarized in `jev-docs/jev-api.md` §2, "Images and
Box"): images anywhere content goes (state, instructions, criteria), sent as `multipart/form-data`, and the Box question
type with its answer.

**Constraints agreed:**

- Ships as **0.3.0**. Adding subtypes to the sealed `Question` and `Answer` hierarchies is an accepted source break (an
  exhaustive `when` without `else` stops compiling). Every existing call (`evaluate`, `query`, `ask`, the builders, the
  `JevQuery` delegates) keeps compiling unchanged. No existing public signature changes, so the ABI diff is additions
  only.
- Image sources: `ByteArray`, base64 text, a kotlinx-io `Path` (common code), and on the JVM a `java.nio.file.Path` or
  `java.io.File`. No `BufferedImage` support.
- The whole image and Box surface is marked with a Kotlin `@RequiresOptIn` annotation. Because consent is given at
  compile time, jev4k sends the `TypeSafe-Danger-Zone-Images: true` header itself.
- Client-side checks are **structural only**: rules about the request's shape. Size and pixel limits, which TypeSafe says
  can change between releases, are left to the server.
- Everything lives in common code and works on every target. Java gets a usable path. No new artifacts: kotlinx-io is
  already on the classpath through `ktor-io` (0.9.1 with Ktor 3.6.0), and Ktor's multipart support is in
  `ktor-client-core`.

**Non-goals:** base64 on the wire (still possible by hand through `extraBody["assets"]`), request compression, image
resizing or re-encoding, automatic discovery of images inside hand-built `JsonElement`s, audio and video.

## 2. The core constraint: images must be attached

jev4k's content is `JsonElement`, and `JsonPrimitive` is sealed, so an image can't be stored inside a JSON tree the way
the SDKs store `Image` objects inside theirs. Once placed, an image is only its reference string. The client therefore
can't discover images; every image a request uses must be **attached** somewhere the client can find its bytes. (The SDKs
need the same for references inside plain strings: Python's `attach_assets`, the JS `assets` map.)

Images attach **to the questions** (`QuestionSet`), not to the call. This keeps `JevApi.evaluate(state, questions, model)`
and every `query`/`ask` signature unchanged, so fakes and MockK mocks of `JevApi` need no change, and it makes a
`JevQuery` whose criteria hold example pictures (TypeSafe's "Define options by example" pattern) self-contained.

## 3. Public API

### 3.1 Opt-in

```kotlin
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "TypeSafe's images and Box questions are experimental; this API can change in any jev4k release",
)
@Retention(AnnotationRetention.BINARY)
@Target(CLASS, FUNCTION, PROPERTY, CONSTRUCTOR, TYPEALIAS)
annotation class ExperimentalJevImages
```

Every public declaration in §3.2–§3.5 carries it. Java ignores opt-in annotations, as it does for Kotlin's own.

### 3.2 `JevImage`

```kotlin
@ExperimentalJevImages
@Serializable(with = JevImageSerializer::class)
class JevImage private constructor(
    val reference: String,
    internal val bytes: ByteArray,
    internal val format: ImageFormat?,   // internal enum PNG, JPEG; null when the signature matches neither
    internal val problem: String?,       // carried, not thrown (§5.1)
) {
    override fun toString(): String = reference

    companion object {
        @JvmStatic @JvmOverloads fun of(bytes: ByteArray, reference: String? = null): JevImage
        @JvmStatic @JvmOverloads fun fromBase64(text: String, reference: String? = null): JevImage
        @JvmStatic @JvmOverloads fun read(path: kotlinx.io.files.Path, reference: String? = null): JevImage
    }
}
```

- `of` copies the bytes. `fromBase64` decodes with `kotlin.io.encoding.Base64` (standard alphabet, padding). `read`
  reads the file at once through `SystemFileSystem`, which works on the JVM, native, and Node.js for both js and wasmJs.
- **Reference.** A null `reference` generates `jev4k-image-<uuid>` (lowercase hex `kotlin.uuid.Uuid`), a form that can't
  occur in ordinary text and that §5.2 can recognize. A caller-chosen reference (e.g. `"<delivered>"`) is kept as given.
- **No throwing for bad content.** Empty bytes, invalid base64 and a signature that is neither PNG (`89 50 4E 47 0D 0A
  1A 0A`) nor JPEG (`FF D8 FF`) become the image's `problem`, which validation reports (§5.1). Only a real I/O failure
  from `read` throws, at the call. This follows jev4k's rule that definition errors must not escape a `JevQuery`
  object's initializer.
- `toString()` returns the reference, so string templates place an image:
  `"Does $upload show the same product as $catalog?"`.
- `JevImageSerializer` encodes an image as its reference string, so a `@Serializable` state field of type `JevImage`
  works. Decoding throws a `SerializationException`, since an image can't be rebuilt from a reference.
- `jsonOf` gains an `is JevImage` branch that returns `JsonPrimitive(reference)`, so `entry(...)` and maps and lists
  accept images: `entry("examples" to listOf(palm1, palm2))`.
- **JVM.** `jvmMain` adds `JevImage.Companion.read(path: java.nio.file.Path, reference: String? = null)` and
  `read(file: java.io.File, reference: String? = null)` in a file with `@file:JvmName("JevImages")`, so Java calls
  `JevImages.read(path)`.
- **Build.** kotlinx-io-core becomes an explicit `api` dependency in the version catalog, pinned to the version Ktor
  brings (0.9.1), because `kotlinx.io.files.Path` appears in the public API. This adds no artifact, but the documented
  POM dependency count goes from five to six.

### 3.3 Placing images with the builders

New overloads that place an image as a whole value:

- `ChoiceBuilder`: `infix fun String.means(image: JevImage)` and `option(key, image)`.
- `ScoreBuilder`: `level(image: JevImage)`.
- `NoulBuilder`: `whenTrue(image: JevImage)` and `whenFalse(image: JevImage)`.

Placing an image never attaches it. There is one rule: attach every image with `attach` (§3.4).

### 3.4 Attaching images

- `QueryBuilder.attach(vararg images: JevImage)`.
- `JevQuery.attach(image: JevImage): JevImage` (protected), which returns its argument so a property can be declared and
  attached in one line: `val thumb = attach(JevImage.of(thumbBytes))`. Images are registered in declaration order, and
  `JevQuery.questions` is rebuilt when images have registered since the last read, as it is for questions.
- `QueryBuilder.include(query)` merges the query's images as well as its questions.
- `QuestionSet.images: List<JevImage>`: the attached images in attach order, deduplicated by identity.
- `JevApi.ask(query, state, vararg images: JevImage, model: String? = null)` for each state type (`String`,
  `JsonElement`, reified `@Serializable T`), and the same on `BlockingJev`. Each is shorthand for
  `query(state, model) { include(query); attach(*images) }`. When no images are given, overload resolution picks the
  existing non-vararg `ask`.

Example, with the gesture pattern from TypeSafe's docs:

```kotlin
@OptIn(ExperimentalJevImages::class)
object Gestures : JevQuery() {
    private val thumb = attach(JevImage.read(Path("gestures/thumb.png")))
    private val palm1 = attach(JevImage.read(Path("gestures/palm1.png")))
    private val palm2 = attach(JevImage.read(Path("gestures/palm2.png")))

    val gesture by choice("Which gesture is the hand in `camera` making?") {
        "thumbs_up" means thumb
        "open_palm" means entry("description" to "A hand held up, palm to the camera.", "examples" to listOf(palm1, palm2))
        "none" means "No hand, or a hand that matches none of the examples."
    }
}

val frame = JevImage.of(cameraBytes)
val result = jev.ask(Gestures, jsonOf(mapOf("camera" to frame)), frame)
```

`JevImage.read` in an object's initializer, as above, does I/O there: a missing file fails the object's first access
with an `ExceptionInInitializerError`, the one way an image can escape the carried-problem rule. The image page says
so, and suggests reading the bytes elsewhere (or from resources) when that matters.

### 3.5 Box

- `data class BoxQuestion(val instructions: JsonElement) : Question`; `Question.typeName` returns `"box"`.
- `QueryBuilder.box(id: String, instructions: String | JsonElement): QuestionRef<BoxAnswer>`.
- `JevQuery.box(instructions: String | JsonElement, id: String? = null): QuestionProvider<BoxAnswer>`, so
  `val mug by box("the coffee mug in `photo`")`.
- `QueryBuilder.question(id, q)` accepts a `BoxQuestion` and picks the type-checking decoder for it.
- `data class BoundingBox(val top: Double, val bottom: Double, val left: Double, val right: Double)`, each edge a
  fraction of the image's height (`top`, `bottom`) or width (`left`, `right`) from the top-left corner.
- `data class BoxAnswer(val box: BoundingBox, val confidence: Double?) : Answer`. Confidence is nullable, as in both SDKs.
- `JevResult.box(id: String): BoxAnswer` (type-checked like `noul`/`choice`/`score`), and `val boxes: Map<String,
  BoxAnswer>` beside `nouls`, `choices` and `scores`.
- The wire gains `WireQuestion.Box(instructions)` with `@SerialName("box")` and no `criteria`.

## 4. Request flow and wire format

`JevClient.evaluate` stays the only place a request is built.

1. **Images.** The request's images are `questions.images`. A request with no images and no Box question is sent exactly
   as today: the same JSON `TextContent`, byte for byte, which `ClientTest` already pins.
2. **Header.** A request with an image or a Box question gets `TypeSafe-Danger-Zone-Images: true` among the built-in
   headers in `setHeaders`, ahead of the client's and the call's headers, which can still replace it.
3. **Multipart.** A request with images is sent as Ktor's `MultiPartFormDataContent`, as both SDKs do whenever a request
   has assets:
    - The first part is named `request`, with `Content-Type: application/json`. It holds the body as sent today
      (`state`, `model`, `questions`, merged with `extraBody`) plus a generated
      `"assets": {"<reference>": {"type": "image", "part": "image-<n>"}, ...}`.
    - Then one part per image, in `QuestionSet.images` order, named `image-0`, `image-1`, … The `Content-Type` is
      `image/png` or `image/jpeg`, from the detected signature, and the `Content-Disposition` carries a filename such as
      `image-0.png`, because curl's `-F name=@file` always sends one. The filename is always sent; the live probe in
      §6.3 only records whether the API requires it.
    - Base64 is never sent; `fromBase64` decodes on the way in.
4. **`extraBody["assets"]`.** Still allowed on a request that attaches no images, so today's hand-built base64 workaround
   keeps working. On a request that attaches images, it is a validation problem (§5.2).
5. **Retries.** Every part is a `ByteArray`, so `HttpRequestRetry` can write the body again on each attempt. A test pins
   that a retried request's body is byte-for-byte the same.
6. **Responses.** `ResponseMapper` reads an answer with `"type": "box"` (or with no `type` when a Box was asked) as a
   `BoxAnswer` instead of `UnknownAnswer`, following §5.3.
7. **Unchanged.** The 16 MiB response cap, timeouts, retry rules, error mapping, request ids and `models()`. jev4k still
   doesn't compress request bodies. The server enforces size and pixel limits.
8. **Caching.** `QuestionSet.toJson()` stays "the `questions` object exactly as it is sent". The docs, which tell callers
   to cache on (model, state, questions), gain a note that a cache key must also cover each attached image's bytes.

## 5. Validation and errors

Each stage collects every problem it finds into one `JevValidationException`, as jev4k does today.

### 5.1 At set validation

These run in the `QuestionSet` constructor: at once for an inline `questions {}` builder, and at first use for a
`JevQuery`.

- An attached image's carried `problem`: empty bytes, invalid base64, or no PNG or JPEG signature.
- A reference that is blank, or longer than 1,024 bytes of UTF-8.
- Two different images (not the same instance) with the same reference.
- A Box question's instructions, under the rule the other types use: non-blank text, or a non-empty object or array.
  The nesting and finite-number checks cover Box instructions too.

### 5.2 Before sending

These run in `evaluate`, the only place that sees the state, the questions and `extraBody` together, after the existing
`stateProblem` check.

- **Unreferenced image.** An attached image whose reference doesn't appear in any string value in the state, the
  instructions or the criteria. Matching follows the API's plain-text rule: substring matches in string values, never in
  object keys. A reference that appears only as an object key (a Choice option name, say) is reported with a hint to put
  it in the option's description instead.
- **Placed but not attached.** A string containing a generated reference (`jev4k-image-` followed by a UUID) that isn't
  attached. The message names the reference and says to `attach()` it. A reference the caller chose can't be detected
  this way.
- **Reference total.** All attached references together over 65,536 bytes of UTF-8.
- **Box without an image.** A Box question in a request that attaches no image.
- **`extraBody` conflict.** `extraBody["assets"]` set on a request that attaches images.

### 5.3 Responses

- A Box answer needs a `box` object with four finite numbers `top`, `bottom`, `left` and `right`, each from 0 to 1, with
  `left < right` and `top < bottom`. `confidence` may be absent or null; when present it is finite and from 0 to 1. Any
  other shape is a `JevResponseValidationException` thrown through `ResponseInfo.fail`, with a field path such as
  `answers.mug.box.left`, the body as received, the status and the headers. When the answer is read, the timing follows
  the existing rules for other answer types.
- No new exception classes. A missing header, a rejected image or `max_tokens_exceeded` comes back as
  `JevBadRequestException` (400), and a size limit (413) as the plain `JevApiException`, as in both SDKs. Each keeps the
  raw body and `x-typesafe-request-id`.

## 6. Testing

Every test class is a Kotest `StringSpec` with an `init {}` block.

### 6.1 Common (every platform)

- **Fixtures.** `TestSupport.kt` gains a small valid PNG and a small valid baseline JPEG, embedded as base64 constants.
  Both are 280 × 280, so they meet the pixel minimum, and no platform needs to load resources.
- **`JevImageTest`.**
    - Signature detection for PNG, JPEG and neither, plus the empty-bytes, invalid-base64 and unrecognized-signature
      problems.
    - Generated references: their form, and that each is unique. Caller-chosen references are kept as given.
    - `toString`, the `jsonOf` branch, and `JevImageSerializer` in a `@Serializable` state.
    - `of` copies its bytes defensively.
    - `read` with a kotlinx-io `Path` to a temporary file.
- **`BoxTest`.**
    - Both builder layers, `question(id, BoxQuestion)`, and validation of the instructions.
    - The wire shape: no criteria.
    - Answer mapping, including each malformed case from §5.3. `result.box`, `boxes`, and the type mismatch errors.
- **`ImageRequestTest`** (`MockEngine` through `testJev`).
    - Framing: the multipart part order and names, the `request` part's JSON and generated `assets` map, the image
      parts' content types and filenames, and the header added automatically.
    - The JSON path is unchanged for a request without images, and a Box question with no attached image is rejected
      before sending (§5.2).
    - A retry sends identical bytes. `extraBody` is merged into the `request` part.
    - Every problem in §5.1 and §5.2. Images attached through `JevQuery`, through `include`, and through the `ask`
      overloads.

### 6.2 JVM

- **Readers.** `JevImages.read` with a `java.nio.file.Path` and with a `java.io.File`.
- **Mocks.** `ConsumerTest`'s MockK mock of `JevApi` runs unchanged.
- **`JavaInterop.java`.** Calls `JevImage.of`, `JevImages.read`, `QueryBuilder.attach` and `box`, so `compileJvmTestJava`
  guards the Java surface.

### 6.3 Live (`JEV4K_LIVE=1` only)

- **`LiveSmokeTest`.** Draws a red square at a known position on a white `BufferedImage` and encodes it as a PNG (no
  test class is in the library, so `java.desktop` is never a library dependency). It sends the image as the state with a
  Noul ("Is there a red square in `photo`?") and a Box, on `jev-preview`. The assertions are loose: the Noul is above
  0.5, and the box contains the square's center.
- **Filename probe.** A one-off probe, not a kept test, sends one image part without a filename. Whatever the outcome,
  jev4k keeps sending the filename (§4.3); the result is recorded in `jev-docs/jev-api.md`.
- **`LiveProbeTest`** stays token-free and unchanged.

## 7. Documentation

- **Site.**
    - A new page, `website/jev4k/docs/queries/images.md`, covering images and Box. Its examples go in
      `src/jvmTest/kotlin/website/Images.kt` (no Kotest or MockK; top-level names unique; trailing comments aligned).
    - The page is added to the nav and to `docs/llms.txt`.
    - `concepts.md` (line 112) and `queries/state.md` (lines 63–64) no longer say jev4k doesn't send images.
    - The caching advice gains the image note from §4.8.
- **README.**
    - Lines 169–170 and 317–318 are updated, and the README gets a short image example.
    - The dependency paragraph goes from five POM dependencies to six (kotlinx-io-core). The Installation page gets the
      same change.
- **`jev-docs/jev-api.md` §10.** The "Images and Box aren't modeled by jev4k" line is replaced with a pointer to this
  design.
- **CLAUDE.md.** Architecture bullets for `JevImage`, attachment, multipart and Box.
- **CHANGELOG.** A 0.3.0 entry that calls out the source break (new `Question` and `Answer` subtypes) and the
  `@ExperimentalJevImages` opt-in.

## 8. Release

- `make abi-update` on a Mac, with the `api/` diff reviewed: additions only (the new sealed subtypes are additions to
  the dump too; their source break is for consumers' exhaustive `when`s).
- `make tests`, `make coverage-verify`, and `cd website/jev4k && uv run zensical build --clean --strict` ("No issues
  found") are green.
- Moving the version to 0.3.0 (`gradle.properties`, branch naming) is a release step outside this spec.
