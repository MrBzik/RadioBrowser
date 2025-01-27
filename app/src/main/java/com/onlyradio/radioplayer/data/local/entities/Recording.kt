package com.onlyradio.radioplayer.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity
data class Recording (

    @PrimaryKey(autoGenerate = false)
    val id : String,
    val iconUri : String,
    val timeStamp : Long,
    val name : String,
    @ColumnInfo(name = "durationMills", defaultValue = "0")
    val durationMills : Long
        )