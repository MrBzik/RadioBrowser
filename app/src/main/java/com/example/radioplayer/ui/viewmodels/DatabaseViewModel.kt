package com.example.radioplayer.ui.viewmodels

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.*
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.radioplayer.adapters.datasources.HistoryDataSource
import com.example.radioplayer.adapters.datasources.HistoryDateLoader
import com.example.radioplayer.adapters.models.StationWithDateModel
import com.example.radioplayer.data.local.entities.HistoryDate
import com.example.radioplayer.data.local.entities.Playlist
import com.example.radioplayer.data.local.entities.RadioStation
import com.example.radioplayer.data.local.relations.StationDateCrossRef
import com.example.radioplayer.data.local.relations.StationPlaylistCrossRef
import com.example.radioplayer.exoPlayer.RadioServiceConnection
import com.example.radioplayer.exoPlayer.RadioSource
import com.example.radioplayer.repositories.DatabaseRepository
import com.example.radioplayer.utils.Constants.COMMAND_LOAD_FROM_PLAYLIST
import com.example.radioplayer.utils.Constants.HISTORY_30_DATES
import com.example.radioplayer.utils.Constants.HISTORY_3_DATES
import com.example.radioplayer.utils.Constants.HISTORY_7_DATES
import com.example.radioplayer.utils.Constants.HISTORY_NEVER_CLEAN
import com.example.radioplayer.utils.Constants.HISTORY_ONE_DAY
import com.example.radioplayer.utils.Constants.HISTORY_OPTIONS
import com.example.radioplayer.utils.Utils.fromDateToString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.sql.Date
import java.util.*
import javax.inject.Inject

@HiltViewModel
class DatabaseViewModel @Inject constructor(
        app : Application,
        private val repository: DatabaseRepository,
        private val radioSource: RadioSource
) : AndroidViewModel(app) {


    val isStationInDB: MutableLiveData<Boolean> = MutableLiveData()

    val isStationFavoured: MutableLiveData<Boolean> = MutableLiveData()

    var currentPlaylistName: MutableLiveData<String> = MutableLiveData()


    fun ifStationAlreadyInDatabase(stationID: String) = viewModelScope.launch {
        val check = repository.checkIfRadioStationInDB(stationID)
        isStationInDB.postValue(check)
    }

    fun checkIfStationIsFavoured(stationID: String) = viewModelScope.launch {
        val check = repository.checkIfStationIsFavoured(stationID)
        isStationFavoured.postValue(check)
    }

    fun updateIsFavouredState(value: Int, stationID: String) = viewModelScope.launch {
        repository.updateIsFavouredState(value, stationID)
    }



    fun insertRadioStation(station: RadioStation) = viewModelScope.launch {
        repository.insertRadioStation(station)
    }

    fun insertNewPlayList(playlist: Playlist) = viewModelScope.launch {
        repository.insertNewPlaylist(playlist)
    }


    fun checkIfInPlaylistOrIncrement(playlistName: String, stationID: String) =
        viewModelScope.launch {
            val check = repository.checkIfInPlaylist(playlistName, stationID)
            if (!check) {
                incrementRadioStationPlaylist(stationID)
            }
        }

    fun insertStationPlaylistCrossRef(crossRef: StationPlaylistCrossRef) = viewModelScope.launch {
        repository.insertStationPlaylistCrossRef(crossRef)

    }

    fun deleteStationPlaylistCrossRef(crossRef: StationPlaylistCrossRef) = viewModelScope.launch {
        repository.deleteStationPlaylistCrossRef(crossRef)
    }

    fun incrementRadioStationPlaylist(stationID: String) = viewModelScope.launch {
        repository.incrementRadioStationPlaylist(stationID)
    }

    fun decrementRadioStationPlaylist(stationID: String) = viewModelScope.launch {
        repository.decrementRadioStationPlaylist(stationID)
    }

    val listOfAllPlaylists = repository.getAllPlaylists()


    private val stationsInPlaylist: MutableLiveData<List<RadioStation>> = MutableLiveData()

    private val stationInFavoured = repository.getAllFavouredStations()

    var isInFavouriteTab: MutableLiveData<Boolean> = MutableLiveData(true)


    val observableListOfStations = MediatorLiveData<List<RadioStation>>()


    init {

        observableListOfStations.addSource(stationInFavoured) { favStations ->
            if (isInFavouriteTab.value!!) {
                observableListOfStations.value = favStations

            }
        }
        observableListOfStations.addSource(stationsInPlaylist) { playlistStations ->
            if (!isInFavouriteTab.value!!) {
                observableListOfStations.value = playlistStations

            }
        }

    }


    fun getStationsInPlaylist(playlistName: String, isForUpdate: Boolean = false) =
        viewModelScope.launch {

            if (!isForUpdate) {

                currentPlaylistName.postValue(playlistName)
                isInFavouriteTab.postValue(false)
            }

            val playlist = radioSource.getStationsInPlaylist(playlistName)

            stationsInPlaylist.postValue(playlist)


        }


    fun getAllFavouredStations() = viewModelScope.launch {

        isInFavouriteTab.postValue(true)

        observableListOfStations.value = stationInFavoured.value

    }


    fun deletePlaylistAndContent(playlistName: String, stations: List<RadioStation>) =
        viewModelScope.launch {

            stations.forEach {
                repository.decrementRadioStationPlaylist(it.stationuuid)
            }
            repository.deleteAllCrossRefOfPlaylist(playlistName)

            repository.deletePlaylist(playlistName)

        }



    // date




    private var initialDate: String = ""
    private val calendar = Calendar.getInstance()


    fun checkDateAndUpdateHistory(stationID: String) = viewModelScope.launch {

        val newTime = System.currentTimeMillis()
        calendar.time = Date(newTime)
        val update = fromDateToString(calendar)

        if (update == initialDate) {/*DO NOTHING*/
        } else {
            val check = repository.checkLastDateRecordInDB(update)
            if (!check) {
                repository.insertNewDate(HistoryDate(update, newTime))
                initialDate = update
                compareDatesWithPrefAndCLeanIfNeeded()
            }
        }

        repository.insertStationDateCrossRef(StationDateCrossRef(stationID, update))
        updateHistory.postValue(true)
    }


    // For RecyclerView


    private suspend fun getStationsInDate(limit: Int, offset: Int): List<StationWithDateModel> {

        val response = radioSource.getStationsInDate(limit, offset, initialDate)

        val date = response.date.date

        val stationsWithDate: MutableList<StationWithDateModel> = mutableListOf()

        stationsWithDate.add(StationWithDateModel.DateSeparator(date))

        response.radioStations.reversed().forEach {
            stationsWithDate.add(StationWithDateModel.Station(it))
        }

        stationsWithDate.add(StationWithDateModel.DateSeparatorEnclosing(date))

        return stationsWithDate
    }

    private val updateHistory : MutableLiveData<Boolean> = MutableLiveData(true)

    val historyFlow = updateHistory.asFlow()
        .flatMapLatest {
            stationsHistoryFlow()
        }.cachedIn(viewModelScope)



    private fun stationsHistoryFlow() : Flow<PagingData<StationWithDateModel>> {
        val loader : HistoryDateLoader = { dateIndex ->
            getStationsInDate(1, dateIndex)
        }

        return Pager(
            config = PagingConfig(
                pageSize = 10,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                HistoryDataSource(loader)
            }
        ).flow
    }

        // Cleaning up database

    private suspend fun removeUnusedStationsOnStart(){

        val stations = repository.gatherStationsForCleaning()

        stations.forEach {

          val check = repository.checkIfRadioStationInHistory(it.stationuuid)

            if(!check){

                repository.deleteRadioStation(it)
            }
        }
    }

    init {
        viewModelScope.launch {
            removeUnusedStationsOnStart()
        }
    }


    // Handle history options and cleaning history

    private val historyOptionsPref = app.getSharedPreferences(HISTORY_OPTIONS, Context.MODE_PRIVATE)

    private val editor = historyOptionsPref.edit()

    fun getHistoryOptionsPref() : String {

        return historyOptionsPref.getString(HISTORY_OPTIONS, HISTORY_NEVER_CLEAN).toString()
    }

    fun setHistoryOptionsPref(newOption : String) {

        editor.putString(HISTORY_OPTIONS, newOption)
        editor.commit()
    }


    fun compareDatesWithPrefAndCLeanIfNeeded()
            = viewModelScope.launch {

        val pref = getHistoryOptionsPref()

        if(pref == HISTORY_NEVER_CLEAN) return@launch

        val prefValue = getDatesValueOfPref(pref)

        val numberOfDatesInDB =  repository.getNumberOfDates()

        if(prefValue >= numberOfDatesInDB) return@launch
        else {

            val numberOfDatesToDelete = numberOfDatesInDB - prefValue
            val deleteList = repository.getDatesToDelete(numberOfDatesToDelete)

            deleteList.forEach {
                repository.deleteAllCrossRefWithDate(it.date)
                repository.deleteDate(it)
            }
        }
            updateHistory.postValue(true)

    }


    fun getDatesValueOfPref(pref : String) : Int {

       return when (pref){
            HISTORY_ONE_DAY -> 1
            HISTORY_3_DATES -> 3
            HISTORY_7_DATES -> 7
            HISTORY_30_DATES -> 30
            else -> 666
        }

    }

}