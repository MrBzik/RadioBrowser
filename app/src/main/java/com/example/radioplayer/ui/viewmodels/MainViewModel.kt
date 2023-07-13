package com.example.radioplayer.ui.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.os.bundleOf
import androidx.lifecycle.*
import androidx.paging.*
import com.example.radioplayer.adapters.datasources.RadioStationsDataSource
import com.example.radioplayer.adapters.datasources.StationsPageLoader
import com.example.radioplayer.connectivityObserver.ConnectivityObserver
import com.example.radioplayer.connectivityObserver.NetworkConnectivityObserver
import com.example.radioplayer.data.local.entities.RadioStation
import com.example.radioplayer.data.remote.entities.RadioStations
import com.example.radioplayer.exoPlayer.*
import com.example.radioplayer.ui.dialogs.*
import com.example.radioplayer.utils.Constants
import com.example.radioplayer.utils.Commands.COMMAND_NEW_SEARCH
import com.example.radioplayer.utils.Constants.IS_CHANGE_MEDIA_ITEMS
import com.example.radioplayer.utils.Constants.IS_NAME_EXACT
import com.example.radioplayer.utils.Constants.IS_NEW_SEARCH
import com.example.radioplayer.utils.Constants.IS_SAME_STATION
import com.example.radioplayer.utils.Constants.IS_TAG_EXACT
import com.example.radioplayer.utils.Constants.ITEM_INDEX
import com.example.radioplayer.utils.Constants.PAGE_SIZE
import com.example.radioplayer.utils.Constants.PLAY_WHEN_READY
import com.example.radioplayer.utils.Constants.SEARCH_FLAG
import com.example.radioplayer.utils.Constants.SEARCH_FROM_RECORDINGS
import com.example.radioplayer.utils.Constants.SEARCH_FULL_COUNTRY_NAME
import com.example.radioplayer.utils.Constants.SEARCH_PREF_COUNTRY
import com.example.radioplayer.utils.Constants.SEARCH_PREF_FULL_AUTO
import com.example.radioplayer.utils.Constants.SEARCH_PREF_MAX_BIT
import com.example.radioplayer.utils.Constants.SEARCH_PREF_MIN_BIT
import com.example.radioplayer.utils.Constants.SEARCH_PREF_NAME
import com.example.radioplayer.utils.Constants.SEARCH_PREF_ORDER
import com.example.radioplayer.utils.Constants.SEARCH_PREF_TAG
import com.example.radioplayer.utils.toRadioStation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject


@HiltViewModel
class MainViewModel @Inject constructor(
    app : Application,
    private val radioServiceConnection: RadioServiceConnection,
    val radioSource: RadioSource
) : AndroidViewModel(app) {

//       val isConnected = radioServiceConnection.isConnected
       val currentRadioStation = radioServiceConnection.currentRadioStation
       val networkError = radioServiceConnection.networkError
       val playbackState = radioServiceConnection.playbackState

        var isPlayerFragInitialized = false


//       private var listOfStations = listOf<RadioStation>()


//       val newPlayingItem : MutableLiveData<PlayingItem> = MutableLiveData()


       private val _isInDetailsFragment = MutableLiveData(false)
       val isInDetailsFragment : LiveData<Boolean> = _isInDetailsFragment
       fun updateIsInDetails(value : Boolean){
           _isInDetailsFragment.postValue(value)
       }

       private val _isToPlayLoadAnim = MutableLiveData(false)
       val isToPlayLoadAnim : LiveData<Boolean> = _isToPlayLoadAnim

       fun updateIsToPlayLoadAnim(value: Boolean){
           _isToPlayLoadAnim.postValue(value)
       }

       var currentFragment = 0
       val currentSongTitle = RadioService.currentSongTitle
       var isInitialLaunchOfTheApp = true
       val isPlayerBuffering = radioSource.isPlayerBuffering




       var noResultDetection : MutableLiveData<Boolean> = MutableLiveData()
       var isNewSearch = true



//     fun disconnectMediaBrowser(){
//            radioServiceConnection.sendCommand(COMMAND_STOP_SERVICE, null)
//         radioServiceConnection.disconnectBrowser()
//    }

    fun connectMediaBrowser(){
        radioServiceConnection.connectBrowser()
    }



    val connectivityObserver = NetworkConnectivityObserver(getApplication())

    var hasInternetConnection : MutableLiveData<Boolean> = MutableLiveData(
        connectivityObserver.isNetworkAvailable()
    )

    private fun setConnectivityObserver() {

        connectivityObserver.observe().onEach {

            when (it) {
                ConnectivityObserver.Status.Available -> {
                    hasInternetConnection.postValue(true)

//                    if(wasSearchInterrupted){
//                        wasSearchInterrupted = false
//                        searchBy.postValue(true)
//                    }
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

       var isFullAutoSearch = searchPreferences.getBoolean(SEARCH_PREF_FULL_AUTO, true)

       var lastSearchCountry = searchPreferences.getString(SEARCH_PREF_COUNTRY, "") ?: ""
       var lastSearchName = searchPreferences.getString(SEARCH_PREF_NAME, "")?: ""
       var lastSearchTag = searchPreferences.getString(SEARCH_PREF_TAG, "")?: ""
       var searchFullCountryName = searchPreferences.getString(SEARCH_FULL_COUNTRY_NAME, "")?: ""

       val searchParamTag : MutableLiveData<String> = MutableLiveData(lastSearchTag)
       val searchParamName : MutableLiveData<String> = MutableLiveData(lastSearchName)
       val searchParamCountry : MutableLiveData<String> = MutableLiveData(lastSearchCountry)


       var isTagExact = searchPreferences.getBoolean(IS_TAG_EXACT, false)
       var isNameExact = searchPreferences.getBoolean(IS_NAME_EXACT, false)
       var wasTagExact = isTagExact
       var wasNameExact = isNameExact

       var oldSearchOrder = searchPreferences.getString(SEARCH_PREF_ORDER, ORDER_POP) ?: ORDER_POP
       var newSearchOrder = oldSearchOrder

       var minBitrateOld = searchPreferences.getInt(SEARCH_PREF_MIN_BIT, BITRATE_0)
       var minBitrateNew = minBitrateOld

       var maxBitrateOld = searchPreferences.getInt(SEARCH_PREF_MAX_BIT, BITRATE_MAX)
       var maxBitrateNew = maxBitrateOld

       var isSearchFilterLanguage = searchPreferences.getBoolean(Constants.IS_SEARCH_FILTER_LANGUAGE, false)
       var wasSearchFilterLanguage = isSearchFilterLanguage



    fun saveSearchPrefs(){
        searchPreferences.edit().apply {
            putString(SEARCH_PREF_TAG, searchParamTag.value)
            putString(SEARCH_PREF_NAME, searchParamName.value)
            putString(SEARCH_PREF_COUNTRY, searchParamCountry.value)
            putString(SEARCH_FULL_COUNTRY_NAME, searchFullCountryName)
            putString(SEARCH_PREF_ORDER, newSearchOrder)
            putBoolean(IS_NAME_EXACT, isNameExact)
            putBoolean(IS_TAG_EXACT, isTagExact)
            putInt(SEARCH_PREF_MIN_BIT, minBitrateNew)
            putInt(SEARCH_PREF_MAX_BIT, maxBitrateNew)
            putBoolean(Constants.IS_SEARCH_FILTER_LANGUAGE, isSearchFilterLanguage)
            putBoolean(SEARCH_PREF_FULL_AUTO, isFullAutoSearch)

        }.apply()
    }



    private val searchBy : MutableLiveData<Boolean> = MutableLiveData()


    @OptIn(ExperimentalCoroutinesApi::class)
    val stationsFlow = searchBy.asFlow()
        .flatMapLatest {
            if(it)
            searchStationsPaging()
            else {
                searchBy.postValue(true)

                flowOf(PagingData.empty())
            }
        }
        .cachedIn(viewModelScope)

//     var wasSearchInterrupted = false


       init {
           searchBy.postValue(true)
       }

// REPOSITION OF SEARCH FAB
//       var fabX = 0f
//       var fabY = 0f
//
//       var fabPref: SharedPreferences = app.getSharedPreferences(SEARCH_BTN_PREF, Context.MODE_PRIVATE)
//       var isFabMoved = fabPref.getBoolean(IS_FAB_UPDATED, false)
//
//       var isFabUpdated = false
//       init {
//            if(isFabMoved){
//                fabX = fabPref.getFloat(FAB_POSITION_X, 0f)
//                fabY = fabPref.getFloat(FAB_POSITION_Y, 0f)
//            }
//        }



//       var isWaitingForNewPage = false
//       var isWaitingForNewSearch = false
//       val isServerNotResponding : MutableLiveData<Boolean> = MutableLiveData(false)
//

       val searchLoadingState : MutableLiveData<Boolean> = MutableLiveData()


        var searchJob : Job = SupervisorJob()



       private suspend fun searchWithNewParams(

            limit : Int, offset : Int) : List<RadioStation> {

            searchLoadingState.postValue(true)

           delay(2000)

           Log.d("CHECKTAGS", "is new search? $isNewSearch")

               val calcOffset = limit * offset

//                var isReversedOrder = true


                val orderSetting = when(newSearchOrder){
                    ORDER_VOTES -> "votes"
                    ORDER_POP -> "clickcount"
                    ORDER_TREND -> "clicktrend"

//                    ORDER_BIT_MIN -> {
//                        isReversedOrder = false
//                        "bitrate"
//                    }
//                    ORDER_BIT_MAX -> "bitrate"
                    else -> "random"
                }

           val lang = if(isSearchFilterLanguage) Locale.getDefault().isO3Language
                        else ""

           var response : RadioStations? = null
           var listOfStations = emptyList<RadioStation>()

           searchJob = viewModelScope.launch {

               while(true){

//                   Log.d("CHECKTAGS", "search is looping")

                   response = radioSource.getRadioStationsSource(
                       offset = calcOffset,
                       pageSize = limit,
                       country = lastSearchCountry,
                       language = lang,
                       tag = lastSearchTag,
                       isTagExact = isTagExact,
                       name = lastSearchName,
                       isNameExact = isNameExact,
                       order = orderSetting,
                       minBit = minBitrateNew,
                       maxBit = maxBitrateNew,

                       )

                   if(response == null) {

                    hasInternetConnection.postValue(false)

                       delay(1000)
                       ensureActive()
                   }

                   else break
               }


               hasInternetConnection.postValue(true)

               if(isNewSearch && response?.size == 0){
                   noResultDetection.postValue(true)
               } else {
                   noResultDetection.postValue(false)
               }

               listOfStations = response?.let {

                   it.map { station ->

                       station.toRadioStation()
                   }
               } ?: emptyList()

               while(!RadioServiceConnection.isConnected){
                   Log.d("CHECKTAGS", "not connected")
                   delay(50)
               }

               radioServiceConnection.sendCommand(COMMAND_NEW_SEARCH,
                   bundleOf(Pair(IS_NEW_SEARCH, isNewSearch)))

               isNewSearch = false

               searchLoadingState.postValue(false)
           }

           searchJob.join()

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



    fun initiateNewSearch() : Boolean {

        if(
            lastSearchName == searchParamName.value &&
            lastSearchTag == searchParamTag.value &&
            lastSearchCountry == searchParamCountry.value &&
            wasTagExact == isTagExact &&
            wasNameExact == isNameExact &&
            newSearchOrder == oldSearchOrder &&
            minBitrateNew == minBitrateOld &&
            maxBitrateNew == maxBitrateOld &&
            isSearchFilterLanguage == wasSearchFilterLanguage

        )
            return false


            return true.also {
                isNewSearch = true
                lastSearchName = searchParamName.value ?: ""
                lastSearchTag = searchParamTag.value ?: ""
                lastSearchCountry = searchParamCountry.value ?: ""
                wasTagExact = isTagExact
                wasNameExact = isNameExact
                oldSearchOrder = newSearchOrder
                minBitrateOld = minBitrateNew
                maxBitrateOld = maxBitrateNew
                wasSearchFilterLanguage = isSearchFilterLanguage

                if(searchJob.isActive){
                    searchJob.cancel()
                }


                searchBy.postValue(false)

            }
    }


        fun playOrToggleStation(
            station : RadioStation? = null,
            searchFlag : Int,
            playWhenReady : Boolean = true,
            itemIndex : Int = -1,
//            historyItemId : String? = null,
            isToChangeMediaItems : Boolean
        ) : Boolean {

            val isPrepared = playbackState.value?.isPrepared ?: false

            val id = station?.stationuuid

            if(isPrepared && id == RadioService.currentPlayingStation.value?.stationuuid
                && RadioService.currentMediaItems != SEARCH_FROM_RECORDINGS
                    ){

                RadioService.currentMediaItems = searchFlag

                var isToPlay = false

                playbackState.value?.let { playbackState ->
                    when {
                        playbackState.isPlaying -> {

                            if(isToChangeMediaItems) isToPlay = false
                            else
                            radioServiceConnection.transportControls.pause()
                        }

                        playbackState.isPlayEnabled -> {
                            if(isToChangeMediaItems) isToPlay = true
                            else
                            radioServiceConnection.transportControls.play()
                        }
                    }
                }

                if(isToChangeMediaItems){

                    radioServiceConnection.transportControls
                        .playFromMediaId(id, bundleOf(
                            Pair(SEARCH_FLAG, searchFlag),
                            Pair(PLAY_WHEN_READY, isToPlay),
                            Pair(ITEM_INDEX, itemIndex),
                            Pair(IS_CHANGE_MEDIA_ITEMS, true),
                            Pair(IS_SAME_STATION, true)
                        ))
                }

                return false
            } else {

//                id?.let {
//                    radioServiceConnection.sendCommand(COMMAND_UPDATE_HISTORY,
//                    bundleOf(Pair(ITEM_ID, it))
//                    )
//                }

                RadioService.currentMediaItems = searchFlag
                radioServiceConnection.transportControls
                    .playFromMediaId(id, bundleOf(
                        Pair(SEARCH_FLAG, searchFlag),
                        Pair(PLAY_WHEN_READY, playWhenReady),
                        Pair(ITEM_INDEX, itemIndex),
                        Pair(IS_CHANGE_MEDIA_ITEMS, isToChangeMediaItems),
                        Pair(IS_SAME_STATION, false)
                    ))

                return true
            }
        }


}

