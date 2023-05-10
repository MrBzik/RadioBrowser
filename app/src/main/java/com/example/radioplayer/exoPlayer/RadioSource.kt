package com.example.radioplayer.exoPlayer


import android.content.SharedPreferences
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.MediaMetadataCompat.*
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import com.example.radioplayer.data.local.RadioDAO
import com.example.radioplayer.data.local.entities.RadioStation
import com.example.radioplayer.data.local.entities.Recording
import com.example.radioplayer.data.local.entities.Title
import com.example.radioplayer.data.local.relations.DateWithStations
import com.example.radioplayer.data.remote.RadioApi
import com.example.radioplayer.data.remote.entities.*
import com.example.radioplayer.utils.Constants.API_RADIO_ALL_COUNTRIES
import com.example.radioplayer.utils.Constants.API_RADIO_LANGUAGES

import com.example.radioplayer.utils.Constants.API_RADIO_SEARCH_URL
import com.example.radioplayer.utils.Constants.BASE_RADIO_URL
import com.example.radioplayer.utils.Constants.BASE_RADIO_URL3
import com.example.radioplayer.utils.Constants.listOfUrls
import com.example.radioplayer.utils.toMediaMetadataCompat
import com.google.android.exoplayer2.MediaItem

import javax.inject.Inject

class RadioSource @Inject constructor(
    private val radioApi: RadioApi,
    private val radioDAO: RadioDAO,
    private val validBaseUrlPref : SharedPreferences

) {

    // Search tab

    var stationsFromLastSearch: RadioStations? = RadioStations()
    var stationsFromApi = mutableListOf<RadioStationsItem>()
    var stationsFromApiMetadata = mutableListOf<MediaMetadataCompat>()
    var stationsFromApiMediaItems = mutableListOf<MediaItem>()
    var isStationsFromApiUpdated = false

    // Favoured tab

    val subscribeToFavouredStations = radioDAO.getAllFavouredStations()
    var stationsFavoured = listOf<RadioStation>()
    var stationsFavouredMetadata = mutableListOf<MediaMetadataCompat>()
    var stationsFavouredMediaItems = listOf<MediaItem>()
    var isStationsFavouredUpdated = false



    var stationsFromPlaylistMetadata = mutableListOf<MediaMetadataCompat>()
    companion object{

        var stationsInPlaylist = listOf<RadioStation>()
        var stationsInPlaylistMediaItems = listOf<MediaItem>()
        var isStationsInPlaylistUpdated = false


        fun updatePlaylistStations(list : List<RadioStation>){
           stationsInPlaylist = list
           stationsInPlaylistMediaItems = list.map {
               MediaItem.fromUri(it.url!!)
           }
            isStationsInPlaylistUpdated = true
        }

    }


    // History

    var stationsFromHistory = mutableListOf<RadioStation>()
    var stationsFromHistoryOneDate = listOf<RadioStation>()

    var stationsFromHistoryMetadata = mutableListOf<MediaMetadataCompat>()
    var stationsFromHistoryOneDateMetadata = listOf<MediaMetadataCompat>()

    var stationsFromHistoryMediaItems = mutableListOf<MediaItem>()
    var stationsFromHistoryOneDateMediaItems = listOf<MediaItem>()


    var isStationsFromHistoryUpdated = false
    var isStationsFromHistoryOneDateUpdated = false

    // Recordings

    val allRecordingsLiveData =  radioDAO.getAllRecordings()
    var recordings = mutableListOf<MediaMetadataCompat>()
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


    //Title
    suspend fun insertNewTitle(title : Title) = radioDAO.insertNewTitle(title)
    suspend fun checkTitleTimestamp(title : String, date : Long) = radioDAO.checkTitleTimestamp(title, date)
    suspend fun deleteTitle(title : Title) = radioDAO.deleteTitle(title)




    suspend fun getStationsInAllDates(limit: Int, offset: Int): DateWithStations {
        val response = radioDAO.getStationsInAllDates(limit, offset)
        val date = response.date.time

        Log.d("CHECKTAGS", "getting all dates")

        if (date == RadioService.currentDateLong) {

            stationsFromHistory = response.radioStations.reversed().toMutableList()

            stationsFromHistoryMetadata = response.radioStations.reversed().map { station ->
                station.toMediaMetadataCompat()
            }.toMutableList()

            stationsFromHistoryMediaItems = response.radioStations.reversed().map { station ->
                MediaItem.fromUri(station.url!!)
            }.toMutableList()


        } else {

            stationsFromHistory.addAll(response.radioStations.reversed())

            response.radioStations.reversed().map { station ->
                stationsFromHistoryMetadata.add(
                    station.toMediaMetadataCompat()
                )
            }
            response.radioStations.reversed().map { station ->
                stationsFromHistoryMediaItems.add(
                    MediaItem.fromUri(station.url!!)
                )
            }
        }
        isStationsFromHistoryUpdated = true

        return response
    }

    suspend fun getStationsInOneDate(time : Long) : DateWithStations {
        val response = radioDAO.getStationsInOneDate(time)

        Log.d("CHECKTAGS", "getting one date")

        stationsFromHistoryOneDate = response.radioStations.reversed()

        stationsFromHistoryOneDateMetadata = response.radioStations.reversed().map{ station ->
            station.toMediaMetadataCompat()
        }.toMutableList()

        stationsFromHistoryOneDateMediaItems = response.radioStations.reversed().map { station ->
            MediaItem.fromUri(station.url!!)
        }.toMutableList()
        isStationsFromHistoryOneDateUpdated = true

        return response
    }



    fun createMediaItemsFromDB(listOfStations : List<RadioStation>){

        stationsFavoured = listOfStations

        stationsFavouredMetadata = listOfStations.map { station ->
            station.toMediaMetadataCompat()
        }.toMutableList()
        stationsFavouredMediaItems = listOfStations.map{ station ->
            MediaItem.fromUri(station.url!!)
        }
        isStationsFavouredUpdated = true
    }

    val isRecordingUpdated : MutableLiveData<Boolean> = MutableLiveData()


    fun handleRecordingsUpdates(
        listOfRecordings : List<Recording>
    ){
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

            recordings = listOfRecordings.map { recording ->
                createMediaMetadataCompatFromRecording(recording)
            }.toMutableList()
            isRecordingUpdated.postValue(true)
    }


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

       val list = response?.radioStations ?: emptyList()

        stationsFromPlaylistMetadata = list.map { station ->
            station.toMediaMetadataCompat()
        }.toMutableList()

    }



    private var validBaseUrl = validBaseUrlPref.getString(BASE_RADIO_URL, BASE_RADIO_URL3)

//    private var validBaseUrl = BASE_RADIO_URLTEST

    private var initialBaseUrlIndex = listOfUrls.indexOf(validBaseUrl)

    private var currentUrlIndex = 0

    suspend fun getRadioStationsSource(
        country: String = "", language : String = "",
        tag: String = "", isTagExact : Boolean,
        name: String = "", isNameExact : Boolean, order : String,
       minBit : Int, maxBit : Int,
        offset: Int = 0, pageSize: Int

    ): RadioStations? {

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
                        url = "${validBaseUrl}$API_RADIO_SEARCH_URL"
                    )
                } else {
                    radioApi.searchRadioWithoutCountry(
                        language = language,
                        tag = tag, tagExact = tagExact,
                        name = name, nameExact = nameExact,
                        sortBy = order,
                        bitrateMin = minBit, bitrateMax = maxBit,
                        offset = offset, limit = pageSize,
                        url = "${validBaseUrl}$API_RADIO_SEARCH_URL"
                    )
                }

        } catch (e : Exception){
            null
        }
                if(response == null) {

                    for(i in currentUrlIndex until listOfUrls.size){

                        if(i == initialBaseUrlIndex) {
                            currentUrlIndex++
                        }
                        else {
                            currentUrlIndex ++
                            validBaseUrl = listOfUrls[i]
                            return getRadioStationsSource(
                               country = country, language = language,
                               tag = tag, isTagExact = isTagExact,
                               name = name, isNameExact = isNameExact,
                               order = order,
                               minBit = minBit, maxBit = maxBit,
                               offset = offset,
                               pageSize = pageSize)
                        }
                    }
                }

                if(currentUrlIndex > 0){
                    validBaseUrlPref.edit().putString(BASE_RADIO_URL, listOfUrls[currentUrlIndex-1]).apply()
                    currentUrlIndex = 0
                }

            stationsFromLastSearch = response?.body()
        return response?.body()

        }

    suspend fun getAllCountries() = radioApi.getAllCountries(validBaseUrl + API_RADIO_ALL_COUNTRIES)

    suspend fun getLanguages(language : String)
        = radioApi.getLanguages(validBaseUrl + API_RADIO_LANGUAGES + language)

    suspend fun getRadioStations (isNewSearch : Boolean)   {

//            state = STATE_PROCESSING

              stationsFromLastSearch?.let {

                  if(isNewSearch) {

                      stationsFromApi = it

                      stationsFromApiMediaItems = it.map { station ->
                          MediaItem.fromUri(station.url_resolved.toUri())
                      }.toMutableList()



                      stationsFromApiMetadata = it.map { station ->
                          stationItemToMediaMetadataCompat(station)
                      }.toMutableList()

                  } else {

                      stationsFromApi.addAll(it)

                      it.map { station ->

                          stationsFromApiMetadata.add(
                              stationItemToMediaMetadataCompat(station)
                          )

                          stationsFromApiMediaItems.add(
                              MediaItem.fromUri(station.url_resolved.toUri())
                          )


                      }
                  }

                  isStationsFromApiUpdated = true
//                     state = STATE_INITIALIZED
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