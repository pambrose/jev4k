package com.pambrose.jev4k.examples

import com.pambrose.jev4k.ExperimentalJevImages
import com.pambrose.jev4k.JevClient
import com.pambrose.jev4k.JevImage
import com.pambrose.jev4k.JevOption
import com.pambrose.jev4k.JevQuery
import com.pambrose.jev4k.ask
import com.pambrose.jev4k.jsonOf

/*
 * Hot dog or not, with images. Run with `make hotdog-example` (needs TYPESAFE_API_KEY). It asks about two photos, one
 * request each: a dog panting in the sun on a hot day, and an actual hot dog. src/jvmTest/resources/hotdog/CREDITS.md
 * credits both.
 */

// --8<-- [start:query]
// What a photo mainly shows.
enum class Subject(
    override val description: String,
) : JevOption {
    HOT_DOG("A hot dog: a cooked sausage served in a sliced bun"),
    DOG("A live dog"),
    SOMETHING_ELSE("Anything else"),
}

object HotDogCheck : JevQuery() {
    val hotDog by noul("Is `photo` a picture of a hot dog?") {
        whenTrue("The food: a cooked sausage served in a sliced bun")
        whenFalse("Anything else, including a live dog, however hot the weather")
    }
    val hotDay by noul("Does `photo` look like it was taken outdoors on a hot, sunny day?")
    val subject by choice<Subject>("What is the main subject of `photo`?")
}
// --8<-- [end:query]

object HotDogExample {
    private val photos = listOf("hot-day-dog.jpg", "hot-dog.jpg")

    @OptIn(ExperimentalJevImages::class)
    @JvmStatic
    fun main(args: Array<String>) {
        JevClient().use { jev ->
            for (name in photos) {
                // --8<-- [start:ask]
                val photo = JevImage.of(photoBytes(name))
                val result = jev.blocking.ask(HotDogCheck, jsonOf(mapOf("photo" to photo)), photo)
                val hotDog = result[HotDogCheck.hotDog]
                val verdict = if (hotDog.isTrue()) "Hotdog!" else "Not hotdog"
                val hotDay = result[HotDogCheck.hotDay].noul
                val subject = result[HotDogCheck.subject].choice
                println("$name: $verdict (hot dog %.2f, hot day %.2f, looks like $subject)".format(hotDog.noul, hotDay))
                // --8<-- [end:ask]
            }
        }
    }

    private fun photoBytes(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/hotdog/$name")) { "no photo $name" }.use { it.readBytes() }
}
