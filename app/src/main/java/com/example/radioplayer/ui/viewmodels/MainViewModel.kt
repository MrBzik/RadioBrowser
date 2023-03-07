package com.example.radioplayer.ui.viewmodels

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_ID
import androidx.core.os.bundleOf
import androidx.lifecycle.*
import androidx.paging.*
import com.example.radioplayer.adapters.datasources.RadioStationsDataSource
import com.example.radioplayer.adapters.datasources.StationsPageLoader
import com.example.radioplayer.connectivityObserver.ConnectivityObserver
import com.example.radioplayer.connectivityObserver.NetworkConnectivityObserver
import com.example.radioplayer.data.local.entities.RadioStation
import com.example.radioplayer.data.local.entities.Recording
import com.example.radioplayer.data.models.PlayingItem
import com.example.radioplayer.exoPlayer.*
import com.example.radioplayer.repositories.DatabaseRepository
import com.example.radioplayer.utils.Constants.COMMAND_ADD_RECORDING_AT_POSITION
import com.example.radioplayer.utils.Constants.COMMAND_NEW_SEARCH
import com.example.radioplayer.utils.Constants.COMMAND_START_RECORDING
import com.example.radioplayer.utils.Constants.COMMAND_STOP_RECORDING
import com.example.radioplayer.utils.Constants.COMMAND_DELETE_RECORDING_AT_POSITION
import com.example.radioplayer.utils.Constants.FAB_POSITION_X
import com.example.radioplayer.utils.Constants.FAB_POSITION_Y
import com.example.radioplayer.utils.Constants.IS_FAB_UPDATED
import com.example.radioplayer.utils.Constants.PAGE_SIZE
import com.example.radioplayer.utils.Constants.RECORDING_ID
import com.example.radioplayer.utils.Constants.RECORDING_POSITION
import com.example.radioplayer.utils.Constants.REC_POSITION
import com.example.radioplayer.utils.Constants.SEARCH_FLAG
import com.example.radioplayer.utils.Constants.SEARCH_FULL_COUNTRY_NAME
import com.example.radioplayer.utils.Constants.SEARCH_PREF_COUNTRY
import com.example.radioplayer.utils.Constants.SEARCH_PREF_NAME
import com.example.radioplayer.utils.Constants.SEARCH_PREF_TAG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject


@HiltViewModel
class MainViewModel @Inject constructor(
    app : Application,
    private val radioServiceConnection: RadioServiceConnection,
    val radioSource: RadioSource,
    private val repository: DatabaseRepository
) : AndroidViewModel(app) {

       val isConnected = radioServiceConnection.isConnected
       val currentRadioStation = radioServiceConnection.currentRadioStation
       val networkError = radioServiceConnection.networkError
       val playbackState = radioServiceConnection.playbackState
       private var listOfStations = listOf<RadioStation>()
       var isNewSearch = true
       var isDelayNeededForServiceConnection = true

       val newPlayingItem : MutableLiveData<PlayingItem> = MutableLiveData()


       var noResultDetection : MutableLiveData<Boolean> = MutableLiveData()

       val currentSongTitle = RadioService.currentSongTitle



    init {

        currentRadioStation.value?.let {
            viewModelScope.launch {
                val itemId = it.getString(METADATA_KEY_MEDIA_ID)
                if(itemId.contains(".ogg")) {
                    val item = repository.getCurrentRecording(itemId)
                    newPlayingItem.postValue(PlayingItem.FromRecordings(item))
                    isRadioTrueRecordingFalse = false
                } else {
                    val item = repository.getCurrentRadioStation(itemId)
                    newPlayingItem.postValue(PlayingItem.FromRadio(item))
                    isRadioTrueRecordingFalse = true
                }
            }
        }

    }




    var hasInternetConnection : MutableLiveData<Boolean> = MutableLiveData(false)

    private fun setConnectivityObserver() {

       val connectivityObserver = NetworkConnectivityObserver(getApplication())

        connectivityObserver.observe().onEach {

            when (it) {
                ConnectivityObserver.Status.Available -> {
                    hasInternetConnection.postValue(true)
                    if(wasSearchInterrupted){
                        wasSearchInterrupted = false
                        searchBy.postValue(true)
                    }
                }
                ConnectivityObserver.Status.Unavailable -> {
                    hasInternetConnection.postValue(false)
                }
                ConnectivityObserver.Status.Lost -> {
                    hasInternetConnection.postValue(false)
                }
                else -> {}
            }
        }.launchIn(viewModelScope)
    }

    init {
        setConnectivityObserver()
    }


       val searchPreferences = app.getSharedPreferences("SearchPref", Context.MODE_PRIVATE)

       val searchParamTag : MutableLiveData<String> = MutableLiveData()
       val searchParamName : MutableLiveData<String> = MutableLiveData()
       val searchParamCountry : MutableLiveData<String> = MutableLiveData()

       var lastSearchCountry = searchPreferences.getString(SEARCH_PREF_COUNTRY, "") ?: ""
       var lastSearchName = searchPreferences.getString(SEARCH_PREF_NAME, "")?: ""
       var lastSearchTag = searchPreferences.getString(SEARCH_PREF_TAG, "")?: ""
       var searchFullCountryName = searchPreferences.getString(SEARCH_FULL_COUNTRY_NAME, "")?: ""


    private val searchBy : MutableLiveData<Boolean> = MutableLiveData()



    @OptIn(ExperimentalCoroutinesApi::class)
    val stationsFlow = searchBy.asFlow()
        .flatMapLatest {
            searchStationsPaging()
        }
        .cachedIn(viewModelScope)

    private var wasSearchInterrupted = false


       init {
           searchParamTag.postValue(lastSearchTag)
           searchParamName.postValue(lastSearchName)
           searchParamCountry.postValue(lastSearchCountry)
           searchBy.postValue(true)

           viewModelScope.launch {
               delay(600)
               if(hasInternetConnection.value == false){
                   wasSearchInterrupted = true
               }
               this.cancel()
           }
       }


       var fabX = 0f
       var fabY = 0f

       var fabPref: SharedPreferences = app.getSharedPreferences("FabPref", Context.MODE_PRIVATE)
       var isFabMoved = fabPref.getBoolean(IS_FAB_UPDATED, false)
       var isFabUpdated = false
       init {
            if(isFabMoved){
                fabX = fabPref.getFloat(FAB_POSITION_X, 0f)
                fabY = fabPref.getFloat(FAB_POSITION_Y, 0f)
            }
        }



       private suspend fun searchWithNewParams(
            limit : Int, offset : Int) : List<RadioStation> {

               delay(3000)


               val calcOffset = limit * offset


                   val response = radioSource.getRadioStationsSource(
                       offset = calcOffset,
                       pageSize = limit,
                       country = lastSearchCountry,
                       tag = lastSearchTag,
                       name = lastSearchName
                   )

                   if(isNewSearch && response?.size == 0){
                       noResultDetection.postValue(true)
                   } else {
                       noResultDetection.postValue(false)
                   }


                   response?.let {

                       listOfStations = it.map { station ->

                           RadioStation(
                               favicon = station.favicon,
                               name = station.name,
                               stationuuid = station.stationuuid,
                               country = station.country,
                               url = station.url_resolved,
                               homepage = station.homepage,
                               tags = station.tags,
                               language = station.language,
                               favouredAt = 0
                           )
                       }
                   }


                if(isDelayNeededForServiceConnection){
                    delay(1000)
                    isDelayNeededForServiceConnection = false
                }

           val firstRunBundle = Bundle().apply {

             this.putBoolean("IS_NEW_SEARCH", isNewSearch)

           }

           radioServiceConnection.sendCommand(COMMAND_NEW_SEARCH, firstRunBundle)

           isNewSearch = false

           return listOfStations

        }



    private fun searchStationsPaging(): Flow<PagingData<RadioStation>> {
        val loader : StationsPageLoader = { pageIndex, pageSize ->
            searchWithNewParams(pageSize, pageIndex)
        }

        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                RadioStationsDataSource(loader, PAGE_SIZE)
            }
        ).flow
    }



    fun initiateNewSearch()  {
        if(
            lastSearchName == searchParamName.value &&
            lastSearchTag == searchParamTag.value &&
            lastSearchCountry == searchParamCountry.value
        ) return

         isNewSearch = true
         lastSearchName = searchParamName.value ?: ""
         lastSearchTag = searchParamTag.value ?: ""
         lastSearchCountry = searchParamCountry.value ?: ""


         if(hasInternetConnection.value == true){

             searchBy.postValue(true)

         } else {
             wasSearchInterrupted = true
         }
    }


        fun seekTo(position : Long){
            radioServiceConnection.transportControls.seekTo(position)

        }


        fun playOrToggleStation(
            station : RadioStation? = null,
            searchFlag : Int = 0,
            rec : Recording? = null,
            recPosition : Int = 0
        ) : Boolean {

            val isPrepared = playbackState.value?.isPrepared ?: false

            val id = station?.stationuuid ?: (rec?.id ?: "")

            if(isPrepared && id
                    == currentRadioStation.value?.getString(METADATA_KEY_MEDIA_ID)){
                playbackState.value?.let { playbackState ->
                    when {

                        playbackState.isPlaying -> {
                            radioServiceConnection.transportControls.pause()
                            return false
                        }

                        playbackState.isPlayEnabled -> {
                            radioServiceConnection.transportControls.play()
                            return true
                        }
                           else -> false
                    }
                }
            } else{

                if(station == null){
                    newPlayingItem.postValue(PlayingItem.FromRecordings(rec!!))
                    isRadioTrueRecordingFalse = false
                    RadioService.recordingPlaybackPosition.postValue(0)

                } else {
                    isRadioTrueRecordingFalse = true
                    newPlayingItem.postValue(PlayingItem.FromRadio(station))
                }

                radioServiceConnection.transportControls
                    .playFromMediaId(id, bundleOf(
                        Pair(SEARCH_FLAG, searchFlag),
                        Pair(REC_POSITION, recPosition)
                        ))
            }

            return false
        }

        var isRadioTrueRecordingFalse = true

        val currentPlayerPosition = RadioService.recordingPlaybackPosition

        // ExoRecord


        fun startRecording() {
            radioServiceConnection.sendCommand(COMMAND_START_RECORDING, null)
        }

        fun stopRecording(){
            radioServiceConnection.sendCommand(COMMAND_STOP_RECORDING, null)
        }
        val exoRecordFinishConverting = radioSource.exoRecordFinishConverting
        val exoRecordState = radioSource.exoRecordState
        val exoRecordTimer = radioSource.exoRecordTimer


        fun commandToDeleteRecordingAtPosition(position : Int, recId : String){
            radioServiceConnection.sendCommand(
                COMMAND_DELETE_RECORDING_AT_POSITION,
                bundleOf(
                    Pair(RECORDING_POSITION, position),
                    Pair(RECORDING_ID, recId)
                    )
                )
        }

        fun commandToInsertRecordingAtPosition(position : Int){
            radioServiceConnection.sendCommand(
                COMMAND_ADD_RECORDING_AT_POSITION,
                    bundleOf(
                        Pair(RECORDING_POSITION, position)
                    )
            )
        }


//    private fun getCountries() = viewModelScope.launch {
//
//        val response = radioSource.getAllCountries()
//
//        var count = 0
//
//        val listOfCountries = mutableListOf<Country>()
//
//        val builder = StringBuilder()
//        val builder2 = StringBuilder()
//        val builder3 = StringBuilder()
//        val builder4 = StringBuilder()
//        val builder5 = StringBuilder()
//
//
//        response.forEach {
//
//            if(count < 45) {
//                count++
//                builder.append(
//                    "Country(\"${it.name}\", \"${it.iso_3166_1}\"), "
//                )
//            } else if(count < 90) {
//            count++
//            builder2.append(
//                "Country(\"${it.name}\", \"${it.iso_3166_1}\"), "
//            )
//        } else if(count < 135) {
//                count++
//                builder3.append(
//                    "Country(\"${it.name}\", \"${it.iso_3166_1}\"), "
//                )
//            } else if(count < 175) {
//                count++
//                builder4.append(
//                    "Country(\"${it.name}\", \"${it.iso_3166_1}\"), "
//                )
//            } else {
//                count++
//                builder5.append(
//                    "Country(\"${it.name}\", \"${it.iso_3166_1}\"), "
//                )
//            }
//
//        }
//
//        Log.d("CHECKTAGS", builder.toString())
//        Log.d("CHECKTAGS", builder2.toString())
//        Log.d("CHECKTAGS", builder3.toString())
//        Log.d("CHECKTAGS", builder4.toString())
//        Log.d("CHECKTAGS", builder5.toString())
//
//    }


//    init {
//
//        viewModelScope.launch {
//           val list = repository.getAllStations()
//            Log.d("CHECKTAGS", list.toString())
//        }
//
//    }

}

