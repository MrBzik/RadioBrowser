package com.example.radioplayer.adapters

import android.content.ClipDescription
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.radioplayer.R
import com.example.radioplayer.data.local.entities.Playlist
import com.example.radioplayer.databinding.ItemHeaderPlaylistBinding
import com.example.radioplayer.databinding.ItemPlaylistCoverBinding
import com.example.radioplayer.ui.MainActivity
import com.example.radioplayer.utils.Constants.SEARCH_FROM_FAVOURITES
import javax.inject.Inject


private const val FOOTER_ADD_PLAYLIST = 1
private const val HEADER_LAZY_PLAYLIST = 2



class PlaylistsAdapter @Inject constructor(
    private val glide : RequestManager,
    private val isFooterNeeded : Boolean
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {


    var strokeWidth = 0
    var strokeColor = 0
    var isDarkMode = MainActivity.uiMode == Configuration.UI_MODE_NIGHT_YES
    var highlightedViewHolder : PlaylistHolder? = null
    var currentPlaylistName = ""
    var currentTab = SEARCH_FROM_FAVOURITES

    private fun highlightPlaylist(bind : ItemPlaylistCoverBinding){

        if(isDarkMode){
            bind.ivPlaylistCover.alpha = 1f
        } else {
            bind.ivPlaylistCover.foreground = ColorDrawable(0x33000000)
        }

        bind.cardView.strokeWidth = strokeWidth
        bind.cardView.strokeColor = strokeColor
    }

    private fun defaultPlaylist(bind : ItemPlaylistCoverBinding) {

        if(isDarkMode){
            bind.ivPlaylistCover.alpha = 0.6f
        } else {
            bind.ivPlaylistCover.foreground = ColorDrawable(0xFFFFFF)
        }

        bind.cardView.strokeWidth = 0
    }

    fun unselectPlaylist(){
        highlightedViewHolder?.let {
            defaultPlaylist(it.bind)
        }
    }


     class HeaderViewHolder (val bind : ItemHeaderPlaylistBinding) : RecyclerView.ViewHolder(bind.root)

     class FooterViewHolder (itemView : View) : RecyclerView.ViewHolder(itemView)

     class PlaylistHolder (val bind : ItemPlaylistCoverBinding) : RecyclerView.ViewHolder(bind.root)

//     {
//
//         var bind : ItemPlaylistCoverBinding
//
//         init {
//             bind = ItemPlaylistCoverBinding.bind(itemView)
//         }
//     }




    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        if(viewType == FOOTER_ADD_PLAYLIST){
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_add_playlist_footer, parent, false)
            val footer = FooterViewHolder(view)
            footer.itemView.setOnClickListener {
                addPlaylistClickListener?.let { click ->
                    click()
                }
            }
            return footer
        } else
            if(viewType == HEADER_LAZY_PLAYLIST){
            val header = HeaderViewHolder(
                ItemHeaderPlaylistBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )

            header.bind.cardView.setOnClickListener {
                lazyListClickListener?.let { click ->
                    click()
                }
            }

            return header
        }

            val playlist = PlaylistHolder(
                ItemPlaylistCoverBinding.inflate(
                    LayoutInflater.from(parent.context),  parent, false
                )
            )

            playlist.bind.cardView.setOnClickListener {

                playlistClickListener?.let { click ->
                    click(differ.currentList[playlist.absoluteAdapterPosition],
                            playlist.absoluteAdapterPosition - 1
                        )
                    highlightedViewHolder?.let {
                        defaultPlaylist(it.bind)
                    }
                    highlightedViewHolder = playlist
                    highlightPlaylist(playlist.bind)
                }
            }

            playlist.itemView.setOnDragListener { view, event ->

                when(event.action){

                    DragEvent.ACTION_DRAG_STARTED ->{
                        event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                    }
                    DragEvent.ACTION_DRAG_ENTERED ->{

                        playlist.bind.cardView.isPressed = true
                        view.invalidate()
                        true
                    }
                    DragEvent.ACTION_DRAG_LOCATION -> true
                    DragEvent.ACTION_DRAG_EXITED -> {

                        playlist.bind.cardView.isPressed = false
                        view.invalidate()
                        true
                    }
                    DragEvent.ACTION_DROP ->{

                        playlist.bind.cardView.isPressed = false

                        val item = event.clipData.getItemAt(0)
                        val data = item.text

                        handleDragAndDrop?.let {
                            it(data.toString(), playlist.bind.tvPlaylistName.text.toString())
                        }

                        view.invalidate()
                        true
                    } DragEvent.ACTION_DRAG_ENDED -> {
                    view.invalidate()
                    true
                }
                    else -> false
                }
            }

            return playlist
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        if(holder is PlaylistHolder) {
            val playlist = differ.currentList[position]
            holder.bind.apply {
                tvPlaylistName.text = playlist.playlistName

                if(currentTab != SEARCH_FROM_FAVOURITES && currentPlaylistName == playlist.playlistName){
                    highlightedViewHolder = holder
                    highlightPlaylist(holder.bind)
                } else {
                    defaultPlaylist(holder.bind)
                }

                glide
                    .load(playlist.coverURI)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(ivPlaylistCover)

            }
        }
    }

    override fun getItemCount(): Int {

       return if(isFooterNeeded) differ.currentList.size +1
       else differ.currentList.size
    }


    override fun getItemViewType(position: Int): Int {

        if(position == differ.currentList.size){
            return FOOTER_ADD_PLAYLIST
        } else if(position == 0 && isFooterNeeded){
            return HEADER_LAZY_PLAYLIST
        }
        return super.getItemViewType(position)
    }

    private val differCallback = object : DiffUtil.ItemCallback<Playlist>(){

        override fun areItemsTheSame(oldItem: Playlist, newItem: Playlist): Boolean {
            return oldItem.playlistName == newItem.playlistName
        }

        override fun areContentsTheSame(oldItem: Playlist, newItem: Playlist): Boolean {
            return oldItem.playlistName == newItem.playlistName &&
                   oldItem.coverURI == newItem.coverURI
        }
    }

    val differ = AsyncListDiffer(this, differCallback)


    private var addPlaylistClickListener : (() -> Unit)? = null

    fun setAddPlaylistClickListener (listener : () -> Unit){
        addPlaylistClickListener = listener

    }


    private var lazyListClickListener : (() -> Unit)? = null

    fun setLazyListClickListener (listener : () -> Unit){
        lazyListClickListener = listener
    }




    private var playlistClickListener : ((Playlist, position : Int) -> Unit)? = null

    fun setPlaylistClickListener (listener : (Playlist, position : Int) -> Unit){

        playlistClickListener = listener

    }

    var handleDragAndDrop : ((stationID: String, playlistName : String) -> Unit)? = null




}