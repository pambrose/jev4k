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
import kotlinx.serialization.json.putJsonObject

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
            parts[1].headers["Content-Disposition"] shouldBe "form-data; name=\"image-0\"; filename=\"image-0.png\""
            parts[1].body shouldBe PNG_BYTES
            parts[2].headers["Content-Type"] shouldBe "image/jpeg"
            parts[2].headers["Content-Disposition"] shouldBe "form-data; name=\"image-1\"; filename=\"image-1.jpg\""
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
            json(parts[0].text).jsonObject["assets"]!!.jsonObject.keys.toList() shouldBe
                listOf("<thumb>", "<palm>", "<frame>")
        }

        "ask with images takes a text or a @Serializable state too" {
            val jev = testJev { respondJson(NO_ANSWERS) }
            val frame = JevImage.of(JPEG_BYTES, "<frame>")
            jev.client.ask(Gestures, "The camera frame: $frame", frame)
            jev.client.ask(Gestures, Frame(0, frame), frame, model = "jev-preview")
            val (text, typed) = jev.requests.map { json(it.parts()[0].text).jsonObject }
            text["state"] shouldBe JsonPrimitive("The camera frame: <frame>")
            typed["state"] shouldBe json("""{"secondsAgo":0,"frame":"<frame>"}""")
            typed["model"] shouldBe JsonPrimitive("jev-preview")
            typed["assets"]!!.jsonObject.keys.toList() shouldBe listOf("<thumb>", "<palm>", "<frame>")
        }

        "a Box whose image comes through extraBody's assets is sent, with the images header" {
            val jev = testJev { respondJson(NO_ANSWERS) }
            val assets =
                buildJsonObject {
                    putJsonObject("<photo>") {
                        put("format", "png")
                        put("data_base64", "iVBORw0KGgo=")
                    }
                }
            jev.client.withOptions(JevCallOptions { extraBody["assets"] = assets }).query("A photo: <photo>") {
                box("cat", "the cat in the photo")
            }
            val request = jev.requests.single()
            request.headers[IMAGES_HEADER] shouldBe "true"
            request.bodyJson().jsonObject["assets"] shouldBe assets
        }

        "an unreferenced image with a generated reference is named with its format and size" {
            val jev = testJev { respondJson(NO_ANSWERS) }
            val stray = JevImage.of(PNG_BYTES)
            shouldThrow<JevValidationException> {
                jev.client.query(PAYOUT_TICKET) {
                    attach(stray)
                    noul("urgent", "Is this urgent?")
                }
            }.problems shouldBe
                listOf(
                    "image '${stray.reference}' (PNG, 16 bytes) is attached but appears in no string in the state, " +
                        "instructions or criteria",
                )
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
                json(
                    """
                    {"frames": [{"secondsAgo": 2, "frame": "<f2>"}, {"secondsAgo": 1, "frame": "<f1>"},
                                {"secondsAgo": 0, "frame": "<f0>"}]}
                    """,
                )
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
            val request = jev.requests.single()
            request.bodyJson().jsonObject["assets"] shouldBe JsonObject(emptyMap())
            request.headers[IMAGES_HEADER] shouldBe "true"
        }

        "text shaped like another process's generated reference is sent as text" {
            val jev = testJev { respondJson(NO_ANSWERS) }
            val stored = foreignReference(JevImage.of(PNG_BYTES).reference)
            jev.client.query("Customer wrote: $stored") { noul("urgent", "Is this urgent?") }
            val request = jev.requests.single()
            request.body.shouldBeInstanceOf<TextContent>()
            request.headers[IMAGES_HEADER] shouldBe null
        }

        "a generated reference whose bytes are in a hand-built extraBody assets is sent" {
            val jev = testJev { respondJson(NO_ANSWERS) }
            val photo = JevImage.of(PNG_BYTES)
            val assets =
                buildJsonObject {
                    putJsonObject(photo.reference) {
                        put("format", "png")
                        put("data_base64", "iVBORw0KGgo=")
                    }
                }
            jev.client.withOptions(JevCallOptions { extraBody["assets"] = assets }).query("Photo: $photo") {
                noul("cat", "Is there a cat in the photo?")
            }
            jev.requests.single().bodyJson().jsonObject["assets"] shouldBe assets
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
