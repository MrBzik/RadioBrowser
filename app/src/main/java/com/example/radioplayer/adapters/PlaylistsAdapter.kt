package com.example.radioplayer.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.RequestManager
import com.example.radioplayer.R
import com.example.radioplayer.data.local.entities.Playlist
import com.example.radioplayer.databinding.ItemPlaylistCoverBinding
import javax.inject.Inject


private const val FOOTER_ADD_PLAYLIST = 1



class PlaylistsAdapter @Inject constructor(
    private val glide : RequestManager
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {


     class FooterViewHolder (itemView : View) : RecyclerView.ViewHolder(itemView)

     class PlaylistHolder (itemView: View) : RecyclerView.ViewHolder(itemView){

         var bind : ItemPlaylistCoverBinding

         init {
             bind = ItemPlaylistCoverBinding.bind(itemView)
         }

     }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        if(viewType == FOOTER_ADD_PLAYLIST){
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_add_playlist_footer, parent, false)
            val footer = FooterViewHolder(view)
            footer.itemView.setOnClickListener {
                addPlaylistClickListener?.let { click ->
                    click(it)
                }
            }
            return footer
        }


             val view = LayoutInflater.from(parent.context)
                 .inflate(R.layout.item_playlist_cover, parent, false)
            val playlist = PlaylistHolder(view)
            playlist.itemView.setOnClickListener {
                playlistClickListener?.let { click ->
                    click(differ.currentList[playlist.absoluteAdapterPosition])
                }
            }
            return playlist
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        if(holder is PlaylistHolder) {
            val playlist = differ.currentList[position]
            holder.bind.tvPlaylistName.text = playlist.playlistName
            glide.load(playlist.coverURI).into(holder.bind.ivPlaylistCover)
        }
    }

    override fun getItemCount(): Int {

       return differ.currentList.size +1
    }


    override fun getItemViewType(position: Int): Int {

        if(position == differ.currentList.size){
            return FOOTER_ADD_PLAYLIST
        }


        return super.getItemViewType(position)
    }

    private val differCallback = object : DiffUtil.ItemCallback<Playlist>(){

        override fun areItemsTheSame(oldItem: Playlist, newItem: Playlist): Boolean {
            return oldItem.playlistName == newItem.playlistName
        }

        override fun areContentsTheSame(oldItem: Playlist, newItem: Playlist): Boolean {
            return oldItem.playlistName == newItem.playlistName
        }
    }

    val differ = AsyncListDiffer(this, differCallback)


    private var addPlaylistClickListener : ((View) -> Unit)? = null

    fun setAddPlaylistClickListener (listener : (View) -> Unit){

        addPlaylistClickListener = listener

    }



    private var playlistClickListener : ((Playlist) -> Unit)? = null

    fun setPlaylistClickListener (listener : (Playlist) -> Unit){

        playlistClickListener = listener

    }


}