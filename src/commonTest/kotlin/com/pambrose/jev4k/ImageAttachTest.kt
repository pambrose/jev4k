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

        "two instances with the same reference and the same bytes are kept once, as a helper that reads again makes" {
            fun logo() = JevImage.of(PNG_BYTES, "<logo>")
            val first = logo()
            val set =
                questions {
                    attach(first, logo())
                    noul("brand", "Is <logo> the brand's logo?")
                }
            set.images shouldBe listOf(first)
        }

        "a reference inside another is refused, since the API can't tell them apart" {
            shouldThrow<JevValidationException> {
                questions {
                    attach(JevImage.of(PNG_BYTES, "img1"), JevImage.of(JPEG_BYTES, "img10"))
                    noul("same", "Is img1 the same shelf as img10?")
                }
            }.problems shouldBe listOf("image reference 'img1' occurs inside 'img10', so the API can't tell them apart")
            val generated = JevImage.of(PNG_BYTES)
            shouldThrow<JevValidationException> {
                questions {
                    attach(generated, JevImage.of(JPEG_BYTES, "image"))
                    noul("same", "Is $generated like image?")
                }
            }.problems shouldContain
                "image reference 'image' occurs inside '${generated.reference}', so the API can't tell them apart"
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
                    "image '<b64>' is not valid base64: use the standard alphabet, with no data: prefix",
                    "image references must not be blank",
                    "image reference '${"é".repeat(40)}…' is longer than 1024 bytes of UTF-8",
                    "two different images share the reference '<twin>'",
                )
        }

        "a bad image with a generated reference is named with its format and size" {
            val gif = JevImage.of("GIF89a......".encodeToByteArray())
            shouldThrow<JevValidationException> { questions { attach(gif) } }.problems shouldContain
                "image '${gif.reference}' (12 bytes) is neither a PNG nor a JPEG; the API takes only those two"
        }

        "a JevQuery with a bad image fails on first use, not in its initializer" {
            shouldThrow<JevValidationException> { BadImageQuery.questions }.problems shouldContain
                "image '<nothing>' is empty"
        }
    }
}
