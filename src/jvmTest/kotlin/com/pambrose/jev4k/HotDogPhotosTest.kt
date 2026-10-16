package com.pambrose.jev4k

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * The photos HotDogExample sends, held to the API's image limits: jev4k leaves sizes to the API, so a photo outside
 * them would only fail as a 400 when someone runs the example.
 */
class HotDogPhotosTest : StringSpec() {
    init {
        for (name in listOf("hot-day-dog.jpg", "hot-dog.jpg")) {
            "the example's $name is a JPEG within the API's limits" {
                val bytes = javaClass.getResourceAsStream("/hotdog/$name").shouldNotBeNull().use { it.readBytes() }
                JevImage.of(bytes).format shouldBe ImageFormat.JPEG
                bytes.size shouldBeLessThan 16 * 1024 * 1024
                val image = ImageIO.read(ByteArrayInputStream(bytes)).shouldNotBeNull()
                withClue("${image.width} x ${image.height}") {
                    image.width shouldBeInRange 256..1568
                    image.height shouldBeInRange 256..1568
                    image.width * image.height shouldBeGreaterThanOrEqual 78_400
                }
            }
        }
    }
}
