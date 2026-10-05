package com.example.vortex_player

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class SongAdapter(
    private val context: Context,
    private val artLoader: ArtLoader,
    private val styler: Styler,
) : BaseAdapter() {

    private class Holder(val art: ImageView, val title: TextView, val artist: TextView) {
        var styledWith: AppTheme? = null
    }

    var songs: List<Song> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var playingIndex = -1
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getCount() = songs.size
    override fun getItem(position: Int) = songs[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_song, parent, false).also {
            it.tag = Holder(it.findViewById(R.id.songArt), it.findViewById(R.id.songTitle), it.findViewById(R.id.songArtist))
        }
        val holder = view.tag as Holder
        if (holder.styledWith !== styler.theme) {
            styler.applyTree(view)
            view.background = styler.rowBackground()
            holder.styledWith = styler.theme
        }
        val song = songs[position]
        holder.title.text = song.title
        holder.title.setTextColor(if (position == playingIndex) styler.theme.accent else styler.theme.textPrimary)
        holder.artist.text = song.artist
        artLoader.load(song.uri, 160, holder.art)
        return view
    }
}
