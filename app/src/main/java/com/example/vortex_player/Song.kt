package com.example.vortex_player

import android.net.Uri

data class Song(val uri: Uri, val title: String, val artist: String, val lrcUri: Uri? = null)
