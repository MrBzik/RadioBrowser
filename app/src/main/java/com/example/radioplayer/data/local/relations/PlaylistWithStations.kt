package com.example.radioplayer.data.local.relations

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.example.radioplayer.data.local.entities.Playlist
import com.example.radioplayer.data.local.entities.RadioStation

data class PlaylistWithStations (
    @Embedded val playlist: Playlist,
    @Relation(
        parentColumn = "playlistName",
        entityColumn = "stationuuid",
        associateBy = Junction(StationPlaylistCrossRef::class)

    )
        val radioStations : List<RadioStation>

        )

