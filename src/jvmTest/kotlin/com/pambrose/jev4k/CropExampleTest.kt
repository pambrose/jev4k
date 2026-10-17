package com.pambrose.jev4k

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import website.cropToBox
import java.awt.image.BufferedImage

private const val MARKER = 0xFF0000

/** The site's crop example is code readers copy, so it is held to working at the image's edges too. */
class CropExampleTest : StringSpec() {
    /** A black image with a [MARKER] pixel at each of [points]. */
    private fun image(
        width: Int,
        height: Int,
        vararg points: Pair<Int, Int>,
    ): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).apply {
            points.forEach { (x, y) -> setRGB(x, y, MARKER) }
        }

    private fun BufferedImage.rgb(
        x: Int,
        y: Int,
    ): Int = getRGB(x, y) and 0xFFFFFF

    init {
        // The spans differ per axis, so swapped axes change the size, and markers at the box's top-left and
        // bottom-right pixels pin where the crop starts and ends.
        "cropToBox crops to the box, from its top-left corner" {
            val source = image(400, 300, 200 to 30, 299 to 149)
            val crop = cropToBox(source, BoundingBox(top = 0.1, bottom = 0.5, left = 0.5, right = 0.75))
            crop.width shouldBe 100
            crop.height shouldBe 120
            crop.rgb(0, 0) shouldBe MARKER
            crop.rgb(1, 1) shouldNotBe MARKER
            crop.rgb(99, 119) shouldBe MARKER
        }

        // The box spans 1.9 to 4.1 pixels on each axis, so pixels 1 through 4 are all in it, at least in part.
        "cropToBox rounds each edge outward, so no pixel inside the box is cut" {
            val box = BoundingBox(top = 0.19, bottom = 0.41, left = 0.19, right = 0.41)
            val crop = cropToBox(image(10, 10, 3 to 3), box)
            crop.width shouldBe 4
            crop.height shouldBe 4
            crop.rgb(2, 2) shouldBe MARKER
        }

        "cropToBox keeps a box at the right and bottom edges inside the image" {
            val crop = cropToBox(image(400, 300), BoundingBox(top = 0.999, bottom = 1.0, left = 0.999, right = 1.0))
            crop.width shouldBe 1
            crop.height shouldBe 1
        }
    }
}
