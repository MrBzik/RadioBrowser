package com.onlyradio.radioplayer.ui.viewmodels

import android.app.Application
import androidx.core.os.bundleOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.onlyradio.radioplayer.data.local.entities.Recording
import com.onlyradio.radioplayer.exoPlayer.RadioService
import com.onlyradio.radioplayer.exoPlayer.RadioServiceConnection
import com.onlyradio.radioplayer.exoPlayer.RadioSource
import com.onlyradio.radioplayer.repositories.RecRepo
import com.onlyradio.radioplayer.utils.Commands.COMMAND_REMOVE_RECORDING_MEDIA_ITEM
import com.onlyradio.radioplayer.utils.Commands.COMMAND_RESTORE_RECORDING_MEDIA_ITEM
import com.onlyradio.radioplayer.utils.Commands.COMMAND_START_RECORDING
import com.onlyradio.radioplayer.utils.Commands.COMMAND_STOP_RECORDING
import com.onlyradio.radioplayer.utils.Commands.COMMAND_UPDATE_REC_PLAYBACK_SPEED
import com.onlyradio.radioplayer.utils.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val app : Application,
    private val repository: RecRepo,
    radioSource: RadioSource,
    private val radioServiceConnection: RadioServiceConnection

) : AndroidViewModel(app) {

    val isPlaying = radioServiceConnection.isPlaying

    // Recordings

//    fun insertNewRecording (
//        recordID : String,
//        iconURI : String,
//        name : String,
//        duration : String
//        ) = viewModelScope.launch {
//            repository.insertRecording(
//                Recording(
//                    id = recordID,
//                    iconUri = iconURI,
//                    name = name,
//                    timeStamp = System.currentTimeMillis(),
//                    duration = duration)
//            )
//    }


    private var isRecordingsCheckNeeded = true

    val durationWithPosition = RadioService.recordingDuration
        .asFlow()
        .combine(RadioService.recordingPlaybackPosition.asFlow()){ dur, pos ->
            dur - pos
        }



    fun checkRecordingsForCleanUp(recList : List<Recording>){

        if(isRecordingsCheckNeeded){

            viewModelScope.launch(Dispatchers.IO){

                val fileList = app.fileList().filter {
                    it.endsWith(".ogg")
                }

                if(fileList.size > recList.size){

                    fileList.forEach { fileName ->

                        val rec = recList.find { rec ->
                            rec.id == fileName
                        }

                        if(rec == null){
                            app.deleteFile(fileName)
                        }
                    }
                }

                isRecordingsCheckNeeded = false
            }
        }
    }


    fun insertNewRecording(rec : Recording) =
        viewModelScope.launch {
            repository.insertRecording(rec)
        }

    fun deleteRecording(recId : String) = viewModelScope.launch {
        repository.deleteRecording(recId)
    }


    val allRecordingsLiveData = radioSource.allRecordingsLiveData



    fun removeRecordingFile(recordingID : String) = viewModelScope.launch(Dispatchers.IO) {
        app.deleteFile(recordingID)
    }


    fun renameRecording(id : String, newName: String) = viewModelScope.launch {
        repository.renameRecording(id, newName)
    }



    fun playOrToggleRecording(
        rec : Recording,
        playWhenReady : Boolean = true,
        itemIndex : Int? = -1
    ): Boolean {

        val isToChangeMediaItems = RadioService.currentMediaItems != Constants.SEARCH_FROM_RECORDINGS

        val isPrepared = radioServiceConnection.isPlaybackStatePrepared

        val id = rec.id

        if(isPrepared && id == RadioService.currentPlayingRecording.value?.id
            && RadioService.currentMediaItems == Constants.SEARCH_FROM_RECORDINGS
        ) {
            isPlaying.value.let { isPlaying ->

                when {
                    isPlaying -> {

                        radioServiceConnection.transportControls.pause()
                        return false
                    }

                    !isPlaying -> {

                        radioServiceConnection.transportControls.play()
                        return true
                    }
                    else -> false
                }
            }

        } else {

            RadioService.currentMediaItems = Constants.SEARCH_FROM_RECORDINGS
            RadioService.recordingPlaybackPosition.postValue(0)

            radioServiceConnection.transportControls
                .playFromMediaId(id, bundleOf(
                    Pair(Constants.SEARCH_FLAG, Constants.SEARCH_FROM_RECORDINGS),
                    Pair(Constants.PLAY_WHEN_READY, playWhenReady),
                    Pair(Constants.ITEM_INDEX, itemIndex),
                    Pair(Constants.IS_CHANGE_MEDIA_ITEMS, isToChangeMediaItems)
                )
                )
        }

        return false
    }


    // ExoRecord

    val currentPlayerPosition = RadioService.recordingPlaybackPosition
    val currentRecordingDuration = RadioService.recordingDuration

    fun startRecording() {
        radioServiceConnection.sendCommand(COMMAND_START_RECORDING, null)
    }

    fun stopRecording(){
        radioServiceConnection.sendCommand(COMMAND_STOP_RECORDING, null)
    }

    val exoRecordState = radioSource.exoRecordState
    val exoRecordTimer = radioSource.exoRecordTimer

//        val isRecordingUpdated = radioSource.isRecordingUpdated

    fun updateRecPlaybackSpeed(){
        radioServiceConnection.sendCommand(COMMAND_UPDATE_REC_PLAYBACK_SPEED, null)
    }


    fun seekTo(position : Long){
        radioServiceConnection.transportControls.seekTo(position)

    }


    fun removeRecordingMediaItem(index : Int){

        radioServiceConnection.sendCommand(
            COMMAND_REMOVE_RECORDING_MEDIA_ITEM,
            bundleOf(Pair(Constants.ITEM_INDEX, index)))
    }

    fun restoreRecordingMediaItem(index : Int){
        radioServiceConnection.sendCommand(
            COMMAND_RESTORE_RECORDING_MEDIA_ITEM,
            bundleOf(Pair(Constants.ITEM_INDEX, index)))
    }



}