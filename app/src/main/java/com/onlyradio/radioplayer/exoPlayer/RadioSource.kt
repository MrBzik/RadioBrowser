package com.onlyradio.radioplayer.exoPlayer


import android.content.SharedPreferences
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_ID
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_URI
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import com.onlyradio.radioplayer.data.local.RadioDAO
import com.onlyradio.radioplayer.data.local.entities.RadioStation
import com.onlyradio.radioplayer.data.local.entities.Recording
import com.onlyradio.radioplayer.data.local.relations.DateWithStations
import com.onlyradio.radioplayer.data.remote.RadioApi
import com.onlyradio.radioplayer.data.remote.entities.RadioStations
import com.onlyradio.radioplayer.data.remote.entities.RadioStationsItem
import com.onlyradio.radioplayer.utils.Constants.API_RADIO_ALL_COUNTRIES
import com.onlyradio.radioplayer.utils.Constants.API_RADIO_LANGUAGES
import com.onlyradio.radioplayer.utils.Constants.BASE_RADIO_URL
import com.onlyradio.radioplayer.utils.Constants.BASE_RADIO_URL3
import com.onlyradio.radioplayer.utils.Constants.SEARCH_FROM_API
import com.onlyradio.radioplayer.utils.Constants.SEARCH_FROM_FAVOURITES
import com.onlyradio.radioplayer.utils.Constants.SEARCH_FROM_HISTORY
import com.onlyradio.radioplayer.utils.toMediaMetadataCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import retrofit2.Response
import javax.inject.Inject

class RadioSource @Inject constructor(
    private val radioApi: RadioApi,
    private val radioDAO: RadioDAO,
    private val validBaseUrlPref : SharedPreferences

) {


    val isPlayerBuffering : MutableLiveData<Boolean> = MutableLiveData()


    // Search tab

    var stationsFromLastSearch: RadioStations? = RadioStations()
    var stationsFromApi = mutableListOf<RadioStationsItem>()
//    var stationsFromApiMetadata = mutableListOf<MediaMetadataCompat>()
    var stationsFromApiMediaItems = mutableListOf<MediaItem>()
//    var isStationsFromApiUpdated = false

    // Favoured tab

    val subscribeToFavouredStations = radioDAO.getAllFavStationsDistinct()

    var stationsFavoured = mutableListOf<RadioStation>()
//    var stationsFavouredMetadata = mutableListOf<MediaMetadataCompat>()
    var stationsFavouredMediaItems = mutableListOf<MediaItem>()
//    var isStationsFavouredUpdated = false



//    var stationsFromPlaylistMetadata = mutableListOf<MediaMetadataCompat>()

    companion object{

        var stationsInPlaylist = mutableListOf<RadioStation>()
        var stationsInPlaylistMediaItems = mutableListOf<MediaItem>()
//        var isStationsInPlaylistUpdated = false


        fun updatePlaylistStations(list : List<RadioStation>){
           stationsInPlaylist = list.toMutableList()
           stationsInPlaylistMediaItems = list.map {
               MediaItem.fromUri(it.url!!)
           }.toMutableList()
//            isStationsInPlaylistUpdated = true
        }


        var stationInOneDateResponse = listOf<RadioStation>()
        var stationsFromHistoryOneDate = listOf<RadioStation>()
        var stationsFromHistoryOneDateMetadata = listOf<MediaMetadataCompat>()
        var stationsFromHistoryOneDateMediaItems = listOf<MediaItem>()
//        var isStationsFromHistoryOneDateUpdated = false


        fun updateHistoryOneDateStations(){
            stationsFromHistoryOneDate = stationInOneDateResponse

            stationsFromHistoryOneDateMetadata = stationInOneDateResponse.map{ station ->
                station.toMediaMetadataCompat()
            }.toMutableList()

            stationsFromHistoryOneDateMediaItems = stationInOneDateResponse.map { station ->
                MediaItem.fromUri(station.url!!)
            }.toMutableList()
//            isStationsFromHistoryOneDateUpdated = true
        }


        var lazyListStations = mutableListOf<RadioStation>()
        var lazyListMediaItems = mutableListOf<MediaItem>()
//        var isLazyListUpdated = false

        fun initiateLazyList(list : List<RadioStation>){
            lazyListStations = list.toMutableList()
            lazyListMediaItems = lazyListStations.map {station ->
            MediaItem.fromUri(station.url!!)
            }.toMutableList()
//            isLazyListUpdated = true
        }

        fun removeItemFromLazyList(index : Int){
            RadioService.lastDeletedStation = lazyListStations[index]
            lazyListStations.removeAt(index)
            lazyListMediaItems.removeAt(index)
        }

        fun restoreItemFromLazyList(index : Int){
            RadioService.lastDeletedStation?.let {station ->
                lazyListStations.add(index, station)
                val mediaItem = MediaItem.fromUri(station.url!!)
                lazyListMediaItems.add(index, mediaItem)

            }
        }

        fun clearLazyList(){
            lazyListStations = mutableListOf()
            lazyListMediaItems = mutableListOf()
        }
    }


    // History

    var allHistoryMap = mutableListOf<Int>()

    var stationsFromHistory = mutableListOf<RadioStation>()


//    var stationsFromHistoryMetadata = mutableListOf<MediaMetadataCompat>()


    var stationsFromHistoryMediaItems = mutableListOf<MediaItem>()



//    var isStationsFromHistoryUpdated = false


    // Recordings

    val allRecordingsLiveData =  radioDAO.getAllRecordings()
//    var recordings = mutableListOf<MediaMetadataCompat>()
//    var stationsFromRecordings = listOf<Recording>()
//    var stationsFromRecordingsMediaItems = listOf<MediaItem>()
//    var isStationsFromRecordingUpdated = true


    val exoRecordState : MutableLiveData<Boolean> = MutableLiveData(false)
    val exoRecordTimer : MutableLiveData<String> = MutableLiveData()
    val exoRecordFinishConverting : MutableLiveData<Boolean> = MutableLiveData()



//    suspend fun getAllCountries() = radioApi.getAllCountries()


    suspend fun getAllTags() = radioApi.getAllTags()

    suspend fun insertRecording(recording : Recording) = radioDAO.insertRecording(recording)
    suspend fun deleteRecording(recId : String) = radioDAO.deleteRecording(recId)




    suspend fun getStationsInAllDates(limit: Int, offset: Int): DateWithStations {
        val response = radioDAO.getStationsInAllDates(limit, offset)
        val date = response.date.time


        if(RadioService.currentMediaItems == SEARCH_FROM_HISTORY){
            RadioService.currentPlayingItemPosition = 0
        }

        if (offset == 0) {

            allHistoryMap.clear()

            allHistoryMap.add(response.radioStations.size + 2)

            stationsFromHistory = response.radioStations.reversed().toMutableList()


                stationsFromHistoryMediaItems = response.radioStations.reversed().map { station ->
                    MediaItem.fromUri(station.url!!)
                }.toMutableList()

//                stationsFromHistoryMetadata = response.radioStations.reversed().map { station ->
//                    station.toMediaMetadataCompat()
//                }.toMutableList()


        } else {

            val addUp = if(allHistoryMap.isNotEmpty()) allHistoryMap.last() + 2
                        else 2


            allHistoryMap.add(response.radioStations.size + addUp)

            stationsFromHistory.addAll(response.radioStations.reversed())

//                response.radioStations.reversed().map { station ->
//                    stationsFromHistoryMetadata.add(
//                        station.toMediaMetadataCompat()
//                    )
//                }
                response.radioStations.reversed().map { station ->
                    stationsFromHistoryMediaItems.add(
                        MediaItem.fromUri(station.url!!)
                    )
                }

        }

//        isStationsFromHistoryUpdated = true

        return response
    }




    suspend fun getStationsInOneDate(time : Long) : DateWithStations {
        return radioDAO.getStationsInOneDate(time)
    }

    fun updateStationsInOneDate(list : List<RadioStation>){
        stationInOneDateResponse = list
    }


    fun createMediaItemsFromDB(player : ExoPlayer, currentStation : RadioStation?){

//        stationsFavouredMetadata = stationsFavoured.map { station ->
//            station.toMediaMetadataCompat()
//        }.toMutableList()


//        isStationsFavouredUpdated = true

        if(RadioService.currentMediaItems == SEARCH_FROM_FAVOURITES){

            var index = 0

            stationsFavouredMediaItems = stationsFavoured.map{ station ->

                val item = MediaItem.fromUri(station.url!!)

                if(station.url != currentStation?.url){
                    player.addMediaItem(index, item)
                    index ++
                } else {
                    index ++
                }

                item
            }.toMutableList()
        } else {
            stationsFavouredMediaItems = stationsFavoured.map{ station ->
                MediaItem.fromUri(station.url!!)
            }.toMutableList()
        }
    }

//    val isRecordingUpdated : MutableLiveData<Boolean> = MutableLiveData()


//    fun handleRecordingsUpdates(
//        listOfRecordings : List<Recording>
//    ){
//        if(deleteAt != -1){
//            recordings.removeAt(deleteAt)
//        }
//        else if(addRecordingAt != -1){
//            val rec = listOfRecordings[addRecordingAt]
//            val mediaRec = createMediaMetadataCompatFromRecording(rec)
//            recordings.add(addRecordingAt, mediaRec)
//        }

//            stationsFromRecordings = listOfRecordings
//
//            stationsFromRecordingsMediaItems = listOfRecordings.map {
//                MediaItem.fromUri(it.id)
//            }

//            recordings = listOfRecordings.map { recording ->
//                createMediaMetadataCompatFromRecording(recording)
//            }.toMutableList()
//            isRecordingUpdated.postValue(true)
//    }


    private fun createMediaMetadataCompatFromRecording(recording : Recording) : MediaMetadataCompat{

        return MediaMetadataCompat.Builder()
            .putString(METADATA_KEY_TITLE, recording.name)
            .putString(METADATA_KEY_DISPLAY_TITLE, recording.name)
            .putString(METADATA_KEY_MEDIA_ID, recording.id)
            .putString(METADATA_KEY_ALBUM_ART_URI, recording.iconUri)
            .putString(METADATA_KEY_DISPLAY_ICON_URI, recording.iconUri)
            .putString(METADATA_KEY_MEDIA_URI, recording.id)
            .putString(METADATA_KEY_DISPLAY_SUBTITLE, "recording")
            .build()
    }



//    fun createConcatenatingMediaFromRecordings(
//        dataSourceFactory: DefaultDataSource.Factory,
//        fileDirPath : String
//    )  : ConcatenatingMediaSource {
//        val concatenatingMediaSource = ConcatenatingMediaSource()
//        recordings.forEach { recording ->
//
//            val uri = "$fileDirPath/${recording.getString(METADATA_KEY_MEDIA_URI)}"
//            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
//                .createMediaSource(MediaItem.fromUri(uri))
//            concatenatingMediaSource.addMediaSource(mediaSource)
//        }
//            return concatenatingMediaSource
//    }



    suspend fun getStationsInPlaylist(playlistName: String) {

        val response = radioDAO.getStationsInPlaylist(playlistName)

//       val list = response?.radioStations ?: emptyList()

//        stationsFromPlaylistMetadata = list.map { station ->
//            station.toMediaMetadataCompat()
//        }.toMutableList()

    }



//    private var validBaseUrl = validBaseUrlPref.getString(BASE_RADIO_URL, BASE_RADIO_URL3)

//    private var validBaseUrl = BASE_RADIO_URLTEST
//
//    private var initialBaseUrlIndex = listOfUrls.indexOf(validBaseUrl)
//
//    private var currentUrlIndex = 0

//    url = "${validBaseUrl}$API_RADIO_SEARCH_URL"

    suspend fun getRadioStationsSource(
        country: String = "", language : String = "",
        tag: String = "", isTagExact : Boolean,
        name: String = "", isNameExact : Boolean, order : String,
       minBit : Int, maxBit : Int,
        offset: Int = 0, pageSize: Int,
        url : String

    ): Response<RadioStations>? {

        val tagExact = if(tag.isBlank()) false else isTagExact
        val nameExact = if(name.isBlank()) false else isNameExact

        val response = try {

//            if(tag == "" && name == "" && country == ""){
//                radioApi.getTopVotedStations(
//                    offset = offset, limit = pageSize,
//                    url =  "${validBaseUrl}$API_RADIO_TOP_VOTE_SEARCH_URL"
//                )
//            } else {
                if(country != "") {
                    radioApi.searchRadio(
                        country = country, language = language,
                        tag = tag, tagExact = tagExact,
                        name = name, nameExact = nameExact,
                        sortBy = order,
                        bitrateMin = minBit, bitrateMax = maxBit,
                        offset = offset, limit = pageSize,
                        url = url
                    )
                } else {
                    radioApi.searchRadioWithoutCountry(
                        language = language,
                        tag = tag, tagExact = tagExact,
                        name = name, nameExact = nameExact,
                        sortBy = order,
                        bitrateMin = minBit, bitrateMax = maxBit,
                        offset = offset, limit = pageSize,
                        url = url
                    )
                }

        } catch (e : Exception){

            null
        }
//                if(response?.body() == null) {
//
//                    for(i in currentUrlIndex until listOfUrls.size){
//
//                        if(i == initialBaseUrlIndex) {
//                            currentUrlIndex++
//                        }
//                        else {
//                            currentUrlIndex ++
//                            validBaseUrl = listOfUrls[i]
//                            return getRadioStationsSource(
//                               country = country, language = language,
//                               tag = tag, isTagExact = isTagExact,
//                               name = name, isNameExact = isNameExact,
//                               order = order,
//                               minBit = minBit, maxBit = maxBit,
//                               offset = offset,
//                               pageSize = pageSize)
//                        }
//                    }
//                }
//
//                if(currentUrlIndex > 0){
//                    validBaseUrlPref.edit().putString(BASE_RADIO_URL, listOfUrls[currentUrlIndex-1]).apply()
//                    currentUrlIndex = 0
//                }

            stationsFromLastSearch = response?.body()

        return response

        }

    suspend fun getAllCountries() = radioApi.getAllCountries(
        validBaseUrlPref.getString(BASE_RADIO_URL, BASE_RADIO_URL3) + API_RADIO_ALL_COUNTRIES)

    suspend fun getLanguages(language : String)
        = radioApi.getLanguages(validBaseUrlPref.getString(BASE_RADIO_URL, BASE_RADIO_URL3)
            + API_RADIO_LANGUAGES + language)

    fun getRadioStations (isNewSearch : Boolean, exoPlayer: ExoPlayer)   {

//            state = STATE_PROCESSING

              stationsFromLastSearch?.let {

                  if(isNewSearch) {

                      stationsFromApi = it

                      stationsFromApiMediaItems = it.map { station ->
                          MediaItem.fromUri(station.url_resolved.toUri())
                      }.toMutableList()


//                      stationsFromApiMetadata = it.map { station ->
//                          stationItemToMediaMetadataCompat(station)
//                      }.toMutableList()

//                      isStationsFromApiUpdated = true

                  } else {

                      stationsFromApi.addAll(it)


                      if(RadioService.currentMediaItems == SEARCH_FROM_API){

                          it.map { station ->

//                              stationsFromApiMetadata.add(
//                                  stationItemToMediaMetadataCompat(station)
//                              )

                              val item = MediaItem.fromUri(station.url_resolved.toUri())
                              exoPlayer.addMediaItem(item)
                              stationsFromApiMediaItems.add(item)

                          }
                      }

                      else {

                          it.map { station ->

//                              stationsFromApiMetadata.add(
//                                  stationItemToMediaMetadataCompat(station)
//                              )

                              stationsFromApiMediaItems.add(
                                  MediaItem.fromUri(station.url_resolved.toUri())
                              )
                          }

//                          isStationsFromApiUpdated = true

                      }
                  }
              }
    }


    private fun stationItemToMediaMetadataCompat(station : RadioStationsItem)
            = MediaMetadataCompat.Builder()
        .putString(METADATA_KEY_TITLE, station.name)
        .putString(METADATA_KEY_DISPLAY_TITLE, station.name)
        .putString(METADATA_KEY_MEDIA_ID, station.stationuuid)
        .putString(METADATA_KEY_ALBUM_ART_URI, station.favicon)
        .putString(METADATA_KEY_DISPLAY_ICON_URI, station.favicon)
        .putString(METADATA_KEY_MEDIA_URI, station.url_resolved)
        .putString(METADATA_KEY_DISPLAY_SUBTITLE, station.country)
        .build()


//    private fun stationToMediaMetadataCompat(station : RadioStation)
//        = MediaMetadataCompat.Builder()
//        .putString(METADATA_KEY_TITLE, station.name)
//        .putString(METADATA_KEY_DISPLAY_TITLE, station.name)
//        .putString(METADATA_KEY_MEDIA_ID, station.stationuuid)
//        .putString(METADATA_KEY_ALBUM_ART_URI, station.favicon)
//        .putString(METADATA_KEY_DISPLAY_ICON_URI, station.favicon)
//        .putString(METADATA_KEY_MEDIA_URI, station.url)
//        .putString(METADATA_KEY_DISPLAY_SUBTITLE, station.country)
//        .build()




//    private val onReadyListeners = mutableListOf<(Boolean) -> Unit>()

//    private var state = STATE_CREATED
//        set(value) {
//            if (value == STATE_INITIALIZED || value == STATE_ERROR) {
//                synchronized(onReadyListeners) {
//                    field = value
//                    onReadyListeners.forEach { listener ->
//                        listener(value == STATE_INITIALIZED)
//                    }
//                }
//            } else {
//                field = value
//            }
//        }

//    fun whenReady(action : (Boolean) -> Unit) : Boolean {
//
//        if(state == STATE_CREATED || state == STATE_PROCESSING) {
//            onReadyListeners += action
//            return false
//        }
//        else {
//            action(state == STATE_INITIALIZED)
//            return true
//        }
//    }


}

//enum class State {
//
//    STATE_CREATED,
//    STATE_PROCESSING,
//    STATE_INITIALIZED,
//    STATE_ERROR
//
//}