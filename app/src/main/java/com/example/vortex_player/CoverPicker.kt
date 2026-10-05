package com.example.vortex_player

import android.net.Uri

/** Hands out the photos of the chosen cover folder in a shuffled order, never repeating the last one. */
class CoverPicker {

    private var covers: List<Uri> = emptyList()
    private val bag = ArrayDeque<Uri>()
    private var last: Uri? = null

    val isEmpty get() = covers.isEmpty()

    fun setCovers(list: List<Uri>) {
        covers = list
        bag.clear()
        last = null
    }

    fun next(): Uri? {
        if (covers.isEmpty()) return null
        if (bag.isEmpty()) {
            bag.addAll(covers.shuffled())
            if (bag.size > 1 && bag.first() == last) bag.addLast(bag.removeFirst())
        }
        return bag.removeFirst().also { last = it }
    }
}
