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
        val box = result.box("mug").box                          // edges as fractions of the height and width
        println("mug across ${box.left..box.right} of the width, ${box.top..box.bottom} of the height")
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
