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
        var styledGlass = -1f
        var row: SurfaceDrawable? = null
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
        if (holder.styledWith !== styler.theme || holder.styledGlass != styler.glass) {
            styler.applyTree(view)
            holder.row = styler.groupRow(RowPosition.SINGLE, styler.dp(DIVIDER_INSET)).also { view.background = it }
            holder.styledWith = styler.theme
            holder.styledGlass = styler.glass
        }
        holder.row?.let {
            it.position = rowPosition(position, songs.size)
            it.invalidateSelf()
        }
        val song = songs[position]
        holder.title.text = song.title
        holder.title.setTextColor(if (position == playingIndex) styler.theme.accent else styler.theme.textPrimary)
        holder.artist.text = song.artist
        artLoader.load(song.uri, 160, holder.art)
        return view
    }

    private companion object {
        /** Dividers start after the cover: 14 padding + 52 art + 14 gap. */
        const val DIVIDER_INSET = 80f
    }
}
