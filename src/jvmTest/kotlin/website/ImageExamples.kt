package website

import com.pambrose.jev4k.BoundingBox
import com.pambrose.jev4k.ExperimentalJevImages
import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.JevCallOptions
import com.pambrose.jev4k.JevImage
import com.pambrose.jev4k.JevQuery
import com.pambrose.jev4k.RetryPolicy
import com.pambrose.jev4k.ask
import com.pambrose.jev4k.entry
import com.pambrose.jev4k.jsonOf
import com.pambrose.jev4k.query
import com.pambrose.jev4k.read
import com.pambrose.jev4k.withOptions
import kotlinx.serialization.Serializable
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration.Companion.seconds

// --8<-- [start:state]
@OptIn(ExperimentalJevImages::class)
suspend fun checkDelivery(jev: JevApi) {
    val door = JevImage.read(Path.of("door-camera.jpg"))       // read now, with a generated reference
    val state = jsonOf(mapOf("door" to door))                  // placed in the state as its reference
    val result =
        jev.query(state) {
            attach(door)                                       // and attached, so its bytes are sent
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
@OptIn(ExperimentalJevImages::class)
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
@OptIn(ExperimentalJevImages::class)
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

@OptIn(ExperimentalJevImages::class)
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
@OptIn(ExperimentalJevImages::class)
@Serializable
data class ReturnClaim(
    val claim: String,
    val photo: JevImage,                                         // written as its reference
)

@OptIn(ExperimentalJevImages::class)
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
@OptIn(ExperimentalJevImages::class)
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
@OptIn(ExperimentalJevImages::class)
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
        val box = result.box("mug").box                          // edges as fractions of the height and width
        println("mug across ${box.left..box.right} of the width, ${box.top..box.bottom} of the height")
    }
}
// --8<-- [end:box]

// --8<-- [start:crop]
@OptIn(ExperimentalJevImages::class)
fun cropToBox(
    image: BufferedImage,
    box: BoundingBox,
): BufferedImage {
    // Round each edge outward, so the crop never cuts into the box, and keep at least one pixel inside the image.
    val left = floor(box.left * image.width).toInt().coerceIn(0, image.width - 1)
    val top = floor(box.top * image.height).toInt().coerceIn(0, image.height - 1)
    val right = ceil(box.right * image.width).toInt().coerceIn(left + 1, image.width)
    val bottom = ceil(box.bottom * image.height).toInt().coerceIn(top + 1, image.height)
    return image.getSubimage(left, top, right - left, bottom - top)
}
// --8<-- [end:crop]

// --8<-- [start:timeout]
@OptIn(ExperimentalJevImages::class)
suspend fun inspectShelf(
    jev: JevApi,
    shelf: JevImage,
) {
    // Each attempt has 60 s to upload the image and get its answer, and a timed-out one isn't uploaded again.
    val upload =
        JevCallOptions {
            timeout = 60.seconds
            retry = RetryPolicy(retryOnTimeout = false)
        }
    val result =
        jev.withOptions(upload).query("The shelf: $shelf") {
            attach(shelf)
            noul("gap", "Is any slot on the shelf empty?")
        }
    println(result.noul("gap"))
}
// --8<-- [end:timeout]
