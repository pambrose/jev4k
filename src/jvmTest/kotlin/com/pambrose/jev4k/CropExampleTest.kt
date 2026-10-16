package com.pambrose.jev4k

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import website.cropToBox
import java.awt.image.BufferedImage

/** The site's crop example is code readers copy, so it is held to working at the image's edges too. */
class CropExampleTest : StringSpec() {
    init {
        "cropToBox crops to the box" {
            val crop = cropToBox(BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB), BoundingBox(0.25, 0.5, 0.5, 0.75))
            crop.width shouldBe 100
            crop.height shouldBe 75
        }

        "cropToBox keeps a box at the right and bottom edges inside the image" {
            val image = BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB)
            val crop = cropToBox(image, BoundingBox(top = 0.999, bottom = 1.0, left = 0.999, right = 1.0))
            crop.width shouldBe 1
            crop.height shouldBe 1
        }
    }
}
