package com.example.radioplayer.exoPlayer

import android.animation.ValueAnimator
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import android.media.audiofx.Visualizer.MEASUREMENT_MODE_PEAK_RMS
import android.os.Bundle
import android.provider.MediaStore.Audio.Radio
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.media.MediaBrowserServiceCompat
import com.bumptech.glide.RequestManager
import com.example.radioplayer.data.local.entities.*
import com.example.radioplayer.data.local.relations.StationDateCrossRef
import com.example.radioplayer.exoPlayer.callbacks.RadioPlaybackPreparer
import com.example.radioplayer.exoPlayer.callbacks.RadioPlayerEventListener
import com.example.radioplayer.exoPlayer.callbacks.RadioPlayerNotificationListener
import com.example.radioplayer.repositories.DatabaseRepository
import com.example.radioplayer.ui.fragments.FavStationsFragment
import com.example.radioplayer.utils.Constants.BUFFER_FOR_PLAYBACK
import com.example.radioplayer.utils.Constants.BUFFER_PREF
import com.example.radioplayer.utils.Constants.BUFFER_SIZE_IN_MILLS
import com.example.radioplayer.utils.Constants.COMMAND_ADD_MEDIA_ITEM
import com.example.radioplayer.utils.Constants.COMMAND_CHANGE_BASS_LEVEL
import com.example.radioplayer.utils.Constants.COMMAND_CHANGE_REVERB_MODE
import com.example.radioplayer.utils.Constants.COMMAND_CLEAR_MEDIA_ITEMS

import com.example.radioplayer.utils.Constants.MEDIA_ROOT_ID
import com.example.radioplayer.utils.Constants.COMMAND_NEW_SEARCH
import com.example.radioplayer.utils.Constants.COMMAND_ON_DROP_STATION_IN_PLAYLIST
import com.example.radioplayer.utils.Constants.COMMAND_START_RECORDING
import com.example.radioplayer.utils.Constants.COMMAND_STOP_RECORDING

import com.example.radioplayer.utils.Constants.COMMAND_REMOVE_RECORDING_MEDIA_ITEM
import com.example.radioplayer.utils.Constants.COMMAND_REMOVE_MEDIA_ITEM
import com.example.radioplayer.utils.Constants.COMMAND_RESTART_PLAYER
import com.example.radioplayer.utils.Constants.COMMAND_RESTORE_RECORDING_MEDIA_ITEM
import com.example.radioplayer.utils.Constants.COMMAND_UPDATE_FAV_PLAYLIST
import com.example.radioplayer.utils.Constants.COMMAND_UPDATE_HISTORY
import com.example.radioplayer.utils.Constants.COMMAND_UPDATE_HISTORY_MEDIA_ITEMS
import com.example.radioplayer.utils.Constants.COMMAND_UPDATE_HISTORY_ONE_DATE_MEDIA_ITEMS
import com.example.radioplayer.utils.Constants.COMMAND_UPDATE_RADIO_PLAYBACK_PITCH
import com.example.radioplayer.utils.Constants.COMMAND_UPDATE_RADIO_PLAYBACK_SPEED
import com.example.radioplayer.utils.Constants.COMMAND_UPDATE_REC_PLAYBACK_SPEED
import com.example.radioplayer.utils.Constants.FOREGROUND_PREF
import com.example.radioplayer.utils.Constants.HISTORY_BOOKMARK_PREF_DEFAULT
import com.example.radioplayer.utils.Constants.HISTORY_DATES_PREF_DEFAULT
import com.example.radioplayer.utils.Constants.HISTORY_PREF
import com.example.radioplayer.utils.Constants.HISTORY_PREF_BOOKMARK
import com.example.radioplayer.utils.Constants.HISTORY_PREF_DATES
import com.example.radioplayer.utils.Constants.IS_ADAPTIVE_LOADER_TO_USE
import com.example.radioplayer.utils.Constants.IS_NEW_SEARCH
import com.example.radioplayer.utils.Constants.IS_TO_CLEAR_HISTORY_ITEMS
import com.example.radioplayer.utils.Constants.ITEM_ID
import com.example.radioplayer.utils.Constants.ITEM_INDEX
import com.example.radioplayer.utils.Constants.NO_ITEMS
import com.example.radioplayer.utils.Constants.NO_PLAYLIST
import com.example.radioplayer.utils.Constants.RECONNECT_PREF


import com.example.radioplayer.utils.Constants.RECORDING_QUALITY_PREF
import com.example.radioplayer.utils.Constants.REC_QUALITY_DEF
import com.example.radioplayer.utils.Constants.SEARCH_FROM_API
import com.example.radioplayer.utils.Constants.SEARCH_FROM_FAVOURITES
import com.example.radioplayer.utils.Constants.SEARCH_FROM_HISTORY
import com.example.radioplayer.utils.Constants.SEARCH_FROM_HISTORY_ONE_DATE
import com.example.radioplayer.utils.Constants.SEARCH_FROM_LAZY_LIST
import com.example.radioplayer.utils.Constants.SEARCH_FROM_PLAYLIST
import com.example.radioplayer.utils.Constants.SEARCH_FROM_RECORDINGS
import com.example.radioplayer.utils.Constants.TITLE_UNKNOWN
import com.example.radioplayer.utils.Utils
import com.example.radioplayer.utils.setPreset
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.DefaultLoadControl.*
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.analytics.PlaybackStats
import com.google.android.exoplayer2.analytics.PlaybackStatsListener
import com.google.android.exoplayer2.audio.*
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.extractor.ExtractorOutput
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource.Factory
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy
import dagger.hilt.android.AndroidEntryPoint
import dev.brookmg.exorecord.lib.ExoRecord
import dev.brookmg.exorecord.lib.IExoRecord
import dev.brookmg.exorecordogg.ExoRecordOgg
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.sql.Date
import java.util.*
import javax.inject.Inject



private const val SERVICE_TAG = "service tag"

private const val PLAY_DURATION_MILLS_CHECK = 604800000

private const val DURATION_UPDATE_PREF = "duration update recovery shared pref"
private const val LAST_PLAYED_STATION_ID = "last played station Id"
private const val LAST_PLAYED_STATION_DURATION = "last played station duration"

@AndroidEntryPoint
class RadioService : MediaBrowserServiceCompat() {

    @Inject
    lateinit var dataSourceFactory : Factory

    @Inject
    lateinit var radioServiceConnection: RadioServiceConnection

    @Inject
    lateinit var renderersFactory: DefaultRenderersFactory

    @Inject
    lateinit var audioAttributes: AudioAttributes

    lateinit var exoPlayer: ExoPlayer

    @Inject
    lateinit var radioSource: RadioSource

    @Inject
    lateinit var glide : RequestManager

    @Inject
    lateinit var exoRecord: ExoRecord

    val exoRecordImp by lazy {
        ExoRecordImpl(this)
    }

    @Inject
    lateinit var databaseRepository: DatabaseRepository

    private val serviceJob = SupervisorJob()

    val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    lateinit var radioNotificationManager: RadioNotificationManager

    private lateinit var mediaSession : MediaSessionCompat
    private lateinit var mediaSessionConnector : MediaSessionConnector

    private lateinit var radioPlayerEventListener: RadioPlayerEventListener

    private val historySettingsPref by lazy{
        this@RadioService.getSharedPreferences(HISTORY_PREF, Context.MODE_PRIVATE)
    }

    private val durationPref by lazy {
        this@RadioService.getSharedPreferences(DURATION_UPDATE_PREF, Context.MODE_PRIVATE)
    }


    var isForegroundService = false

    var isPlaybackStatePlaying = false

    private var isPlayerInitialized = false


    private val calendar = Calendar.getInstance()

//    private lateinit var visualizer: Visualizer





    private var isFavPlaylistPendingUpdate = false

    private var favStationsTemp : List<RadioStation> = emptyList()

//    private val observerForDatabase by lazy {
//        Observer<List<RadioStation>>{
//
//            if(currentMediaItems == SEARCH_FROM_FAVOURITES){
//                if(isInStationDetails)
//                isFavPlaylistPendingUpdate = true
//            } else {
//                radioSource.createMediaItemsFromDB(it, exoPlayer, currentRadioStation)
//            }
//
//        }
//    }



    var stationsFromRecordings = mutableListOf<Recording>()
    var stationsFromRecordingsMediaItems = mutableListOf<MediaItem>()
//    var isStationsFromRecordingUpdated = true

    private val observerForRecordings = Observer<List<Recording>>{

        if(currentMediaItems != SEARCH_FROM_RECORDINGS){
            stationsFromRecordings = it.toMutableList()
            val fileDir = this@RadioService.filesDir.path
            stationsFromRecordingsMediaItems = it.map { rec ->
                MediaItem.fromUri("$fileDir/${rec.id}")
            }.toMutableList()
//            isStationsFromRecordingUpdated = true
        }



//        radioSource.isRecordingUpdated.postValue(true)
    }

     var currentRadioStation: RadioStation? = null
     var currentRecording : Recording? = null

    var lastInsertedSong = ""

    companion object{

        var historyPrefBookmark = 20

        var currentPlaylistName = ""
        var selectedHistoryDate = -1L

        var lastDeletedStation : RadioStation? = null
        var lastDeletedRecording : Recording? = null


        var currentlyPlayingSong = TITLE_UNKNOWN
//        var currentStation : MediaMetadataCompat? = null
        var currentMediaItems = -1

        var isInStationDetails = false

        var currentPlayingStation : MutableLiveData<RadioStation> = MutableLiveData()
        var currentPlayingRecording : MutableLiveData<Recording> = MutableLiveData()
        var currentPlayingItemPosition = -1

        var isFromRecording = false

//        var canOnDestroyBeCalled = false

        var isToKillServiceOnAppClose = false

        var currentDateLong : Long = 0
        var currentDateString = ""

        val currentSongTitle = MutableLiveData<String>()
        val recordingPlaybackPosition = MutableLiveData<Long>()
        val recordingDuration = MutableLiveData<Long>()

        var playbackSpeedRec = 100
        var playbackSpeedRadio = 100
        var playbackPitchRadio = 100
        var isSpeedPitchLinked = true

        var isToReconnect = true

        var isToUpdateLiveData = true

        var bufferSizeInMills = 0
//        var bufferSizeInBytes = 0
        var bufferForPlayback = 0
//        var isToSetBufferInBytes = false
        var isAdaptiveLoaderToUse = false

        var reverbMode = 0

        var isVirtualizerEnabled = false


    }


    private var recSampleRate = 0
    private var recChannelsCount = 2

    private fun searchRadioStations(isNewSearch : Boolean) {
        if(isNewSearch && currentMediaItems == SEARCH_FROM_API){
            clearMediaItems()
        }

        radioSource.getRadioStations(isNewSearch, exoPlayer)
    }



    private val environmentalReverb : EnvironmentalReverb by lazy {
        EnvironmentalReverb(1, 0)
    }

    private val effectVirtualizer : Virtualizer by lazy{
        Virtualizer(1, exoPlayer.audioSessionId)
    }


    override fun onCreate() {
        super.onCreate()

        val activityIntent = packageManager?.getLaunchIntentForPackage(packageName)?.let {

            PendingIntent.getActivity(this, 0, it, FLAG_IMMUTABLE)
        }

        mediaSession = MediaSessionCompat(this, SERVICE_TAG).apply {
            setSessionActivity(activityIntent)
            isActive = true
        }

        sessionToken = mediaSession.sessionToken

        radioNotificationManager = RadioNotificationManager(
            this,
            mediaSession.sessionToken,
            RadioPlayerNotificationListener(this),
            glide, {
                upsertNewBookmark()
            }, {
                exoRecordImp.stopRecording()
                 }
            )


        val radioPlaybackPreparer = RadioPlaybackPreparer(
            radioSource, { flag, playWhenReady, itemIndex, isToChangeMediaItems, isSameStation ->

             var index = itemIndex

            val isFromRecordings = flag == SEARCH_FROM_RECORDINGS

            if(flag == SEARCH_FROM_HISTORY && !isInStationDetails){
                index = adjustIndexFromHistory(itemIndex)
            } else if (flag == SEARCH_FROM_HISTORY_ONE_DATE && !isInStationDetails) {
                index --
            }

            preparePlayer(
                playNow = playWhenReady,
                itemIndex =  index,
                isToChangeMediaItems = isToChangeMediaItems,
                isFromRecordings = isFromRecordings,
                isSameStation = isSameStation
            )

        }, {
            command, extras ->

            when(command){

                COMMAND_NEW_SEARCH -> {

                   val isNewSearch = extras?.getBoolean(IS_NEW_SEARCH) ?: false

                    searchRadioStations(isNewSearch)

                }

//                COMMAND_UPDATE_HISTORY -> {
//                    val id = extras?.getString(ITEM_ID)
//                    id?.let{
//                        checkDateAndUpdateHistory(it)
//                    }
//                }

                COMMAND_START_RECORDING -> {

                    if(isPlaybackStatePlaying){

                        exoRecordImp.onCommandStartRecording()
                    }
                }

                COMMAND_STOP_RECORDING -> {
                    exoRecordImp.stopRecording()
                }



                COMMAND_UPDATE_REC_PLAYBACK_SPEED -> {

                    val isToPlay = isPlaybackStatePlaying
                    exoPlayer.pause()
                    val newValue = playbackSpeedRec.toFloat()/100
                    val params = PlaybackParameters(newValue, newValue)
                    exoPlayer.playbackParameters = params
//                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = isToPlay
                }

                COMMAND_UPDATE_RADIO_PLAYBACK_SPEED -> {

                    if(!isFromRecording){
                        val isToPlay = isPlaybackStatePlaying
                        exoPlayer.pause()
                        if(isSpeedPitchLinked){
                            playbackPitchRadio = playbackSpeedRadio
                        }
                        val params = PlaybackParameters(
                            playbackSpeedRadio.toFloat()/100,
                            playbackPitchRadio.toFloat()/100
                        )
                        exoPlayer.playbackParameters = params
//                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = isToPlay
                    }
                }

                COMMAND_UPDATE_RADIO_PLAYBACK_PITCH -> {

                    if(!isFromRecording){
                        val isToPlay = isPlaybackStatePlaying
                        exoPlayer.pause()
                        if(isSpeedPitchLinked){
                            playbackSpeedRadio = playbackPitchRadio
                        }
                        val params = PlaybackParameters(
                            playbackSpeedRadio.toFloat()/100,
                            playbackPitchRadio.toFloat()/100
                        )
                        exoPlayer.playbackParameters = params
//                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = isToPlay
                    }
                }

                COMMAND_RESTART_PLAYER -> {
                    recreateExoPlayer()
                }


                COMMAND_CHANGE_REVERB_MODE -> {
                    changeReverbMode()
                }

                COMMAND_CHANGE_BASS_LEVEL -> {
                    changeVirtualizerLevel()
                }

//                COMMAND_COMPARE_DATES_PREF_AND_CLEAN -> {
//                    compareDatesWithPrefAndCLeanIfNeeded()
//                }

                COMMAND_UPDATE_FAV_PLAYLIST -> {


                    if(isFavPlaylistPendingUpdate){
                        isFavPlaylistPendingUpdate = false

                        radioSource.stationsFavoured = favStationsTemp.toMutableList()
                        favStationsTemp = emptyList()

                        clearMediaItems(false)

                        radioSource.createMediaItemsFromDB(exoPlayer, currentRadioStation)

                    }
                }


                COMMAND_REMOVE_RECORDING_MEDIA_ITEM ->{

                    val index = extras?.getInt(ITEM_INDEX)


                    index?.let{ pos ->

                        lastDeletedRecording = stationsFromRecordings[pos]
                        stationsFromRecordings.removeAt(pos)
                        stationsFromRecordingsMediaItems.removeAt(pos)


                        if(pos == currentPlayingItemPosition) {
                            currentMediaItems = NO_ITEMS
                            exoPlayer.stop()
                            exoPlayer.clearMediaItems()

                        } else {

                            if(pos < currentPlayingItemPosition)
                                currentPlayingItemPosition --
                            exoPlayer.removeMediaItem(pos)
                        }
                    }
                }


                COMMAND_RESTORE_RECORDING_MEDIA_ITEM -> {

                    val index = extras?.getInt(ITEM_INDEX)

                    index?.let{ pos ->

                        if(pos <= currentPlayingItemPosition)
                            currentPlayingItemPosition ++

                        lastDeletedRecording?.let { rec ->
                            stationsFromRecordings.add(pos, rec)

                            val path = this@RadioService.filesDir.absolutePath + "/" + rec.id

                            val mediaItem = MediaItem.fromUri(path)

                            stationsFromRecordingsMediaItems.add(
                                pos, mediaItem
                            )
                            exoPlayer.addMediaItem(pos, mediaItem)
                        }
                    }
                }


                COMMAND_REMOVE_MEDIA_ITEM -> {

                    val index = extras?.getInt(ITEM_INDEX, -1) ?: -1

                    if(index != -1){

                        if(index == exoPlayer.currentMediaItemIndex){

                            clearMediaItems(true)

                        } else {

                            if(index < currentPlayingItemPosition)
                                currentPlayingItemPosition --
                            exoPlayer.removeMediaItem(index)

                            if(currentMediaItems == SEARCH_FROM_FAVOURITES){

                                lastDeletedStation = radioSource.stationsFavoured[index]
                                radioSource.stationsFavoured.removeAt(index)
                                radioSource.stationsFavouredMediaItems.removeAt(index)

                            } else if(currentMediaItems == SEARCH_FROM_PLAYLIST){

                                lastDeletedStation = RadioSource.stationsInPlaylist[index]
                                RadioSource.stationsInPlaylist.removeAt(index)
                                RadioSource.stationsInPlaylistMediaItems.removeAt(index)

                            }
                        }
                    }
                }

                COMMAND_ADD_MEDIA_ITEM -> {

                    val index = extras?.getInt(ITEM_INDEX, -1) ?: -1

                    if(index != -1){

                        lastDeletedStation?.let { station ->
                            val mediaItem = MediaItem.fromUri(station.url!!)
                            exoPlayer.addMediaItem(index, mediaItem)

                            if(index <= currentPlayingItemPosition)
                                currentPlayingItemPosition ++

                            when (currentMediaItems) {
                                SEARCH_FROM_FAVOURITES -> {
                                    radioSource.stationsFavoured.add(index, station)
                                    radioSource.stationsFavouredMediaItems.add(index, mediaItem)

                                }
                                SEARCH_FROM_PLAYLIST -> {
                                    RadioSource.stationsInPlaylist.add(index, station)
                                    RadioSource.stationsInPlaylistMediaItems.add(index, mediaItem)
                                }
                            }
                        }
                    }
                }



                COMMAND_ON_DROP_STATION_IN_PLAYLIST -> {


                    val radioStation =  FavStationsFragment.dragAndDropStation

                    radioStation?.let { station ->

                        currentPlayingItemPosition ++
                        val mediaItem = MediaItem.fromUri(station.url!!)
                        exoPlayer.addMediaItem(0, mediaItem)
                        RadioSource.stationsInPlaylist.add(0, station)
                        RadioSource.stationsInPlaylistMediaItems.add(0, mediaItem)
                    }
                }

                COMMAND_CLEAR_MEDIA_ITEMS -> {
                    clearMediaItems()
                }


                COMMAND_UPDATE_HISTORY_MEDIA_ITEMS -> {

                    val isToClearMediaItems = extras?.getBoolean(IS_TO_CLEAR_HISTORY_ITEMS) ?: true

                    if(isToClearMediaItems){

                        clearMediaItems(false)

                        for(i in 1 until radioSource.stationsFromHistoryMediaItems.size){


                            exoPlayer.addMediaItem(
                                radioSource.stationsFromHistoryMediaItems[i]
                            )
                        }

                    } else {

                        for(i in exoPlayer.mediaItemCount until radioSource.stationsFromHistoryMediaItems.size){
                            exoPlayer.addMediaItem(
                                radioSource.stationsFromHistoryMediaItems[i]
                            )
                        }
                    }
//                    radioSource.isStationsFromHistoryUpdated = false
                }

//                COMMAND_CHANGE_MEDIA_ITEMS -> {
//
//                    val playWhenReady = extras?.getBoolean(PLAY_WHEN_READY) ?: false
//
//                    val flag = extras?.getInt(SEARCH_FLAG) ?: -1
//
//                    var index = extras?.getInt(Constants.ITEM_INDEX, -1) ?: 0
//
//
//                    isFromRecording = flag == SEARCH_FROM_RECORDINGS
//
//
//                    updateMediaItems(true, flag)
//
//
//                    currentMediaItems = flag
//
//                    if(flag == SEARCH_FROM_HISTORY){
//                        index = adjustIndexFromHistory(index)
//                    }
//
//                    exoPlayer.seekTo(index, 0L)
//
//                    exoPlayer.prepare()
//
//                    exoPlayer.playWhenReady = playWhenReady
//
//                }

                COMMAND_UPDATE_HISTORY_ONE_DATE_MEDIA_ITEMS -> {

                    currentPlayingItemPosition = 0

                    clearMediaItems(false)

                    for(i in 1 until RadioSource.stationsFromHistoryOneDateMediaItems.size){

                        exoPlayer.addMediaItem(
                            RadioSource.stationsFromHistoryOneDateMediaItems[i]
                        )
                    }

//                    RadioSource.isStationsFromHistoryOneDateUpdated = false
                }

            }
        })

        mediaSessionConnector = MediaSessionConnector(mediaSession)
        mediaSessionConnector.setPlaybackPreparer(radioPlaybackPreparer)
        mediaSessionConnector.setQueueNavigator(RadioQueueNavigator())
//        mediaSessionConnector.setPlayer(exoPlayer)


        radioPlayerEventListener = RadioPlayerEventListener(this)

//        exoPlayer.addListener(radioPlayerEventListener)

//        radioNotificationManager.showNotification(exoPlayer)


        exoRecordImp.checkRecordingAndRecoverIfNeeded()

        isToReconnect = this@RadioService
            .getSharedPreferences(RECONNECT_PREF, Context.MODE_PRIVATE).getBoolean(
            RECONNECT_PREF, true)

        Log.d("CHECKTAGS", "servie code")

        initialCheckForBuffer()

        exoPlayer = provideExoPlayer()
        mediaSessionConnector.setPlayer(exoPlayer)
        radioNotificationManager.showNotification(exoPlayer)

        isToKillServiceOnAppClose = this@RadioService.getSharedPreferences(
            FOREGROUND_PREF, Context.MODE_PRIVATE).getBoolean(FOREGROUND_PREF, false)

        initialHistoryPref()

        registerNewDateReceiver()


        serviceScope.launch(Dispatchers.IO) {
            radioSource.subscribeToFavouredStations.collectLatest {

                if(currentMediaItems == SEARCH_FROM_FAVOURITES){
                    if(isInStationDetails)
                        isFavPlaylistPendingUpdate = true
                        favStationsTemp = it
                } else {
                    radioSource.stationsFavoured = it.toMutableList()
                    radioSource.createMediaItemsFromDB(exoPlayer, currentRadioStation)
                }

            }
        }


        radioSource.allRecordingsLiveData.observeForever(observerForRecordings)

        getLastDateAndCheck()

//        visualizer = Visualizer(exoPlayer.audioSessionId)


    }


    fun insertNewTitle(title: String){

        serviceScope.launch(Dispatchers.IO){

           val checkTitle = radioSource.checkTitleTimestamp(title, currentDateLong)

           val isTitleBookmarked = checkTitle?.isBookmarked

         checkTitle?.let {
             radioSource.deleteTitle(it)
         }
                val stationName = currentRadioStation?.name ?: ""

                val stationUri = currentRadioStation?.favicon ?: ""


                radioSource.insertNewTitle(Title(
                    timeStamp = System.currentTimeMillis(),
                    date = currentDateLong,
                    title = title,
                    stationName = stationName,
                    stationIconUri = stationUri,
                    isBookmarked = isTitleBookmarked ?: false
                ))

            lastInsertedSong = title

        }
    }



    private fun upsertNewBookmark() = CoroutineScope(Dispatchers.IO).launch{

        if(currentlyPlayingSong != TITLE_UNKNOWN){

            databaseRepository.deleteBookmarksByTitle(currentlyPlayingSong)

            databaseRepository.insertNewBookmarkedTitle(
                BookmarkedTitle(
                    timeStamp = System.currentTimeMillis(),
                    date = currentDateLong,
                    title = currentlyPlayingSong,
                    stationName = currentRadioStation?.name ?: "",
                    stationIconUri = currentRadioStation?.favicon ?: ""
                )
            )

            val count = databaseRepository.countBookmarkedTitles()

            if(count > historyPrefBookmark && historyPrefBookmark != 100){

                val bookmark = databaseRepository.getLastValidBookmarkedTitle(historyPrefBookmark -1)

                databaseRepository.cleanBookmarkedTitles(bookmark.timeStamp)
            }
        }
    }



     fun fadeInPlayer(){
        val anim = ValueAnimator.ofFloat(0f, 1f)

        anim.addUpdateListener {
            exoPlayer.volume = it.animatedValue as Float
        }

        anim.duration = 800
        anim.start()

    }

    private fun fadeOutPlayer(){
        val anim = ValueAnimator.ofFloat(1f, 0f)

        anim.addUpdateListener {
            exoPlayer.volume = it.animatedValue as Float
        }

        anim.duration = 200
        anim.start()

    }



    private var wasReverbSet = false

    private fun changeReverbMode() {

        setPreset(environmentalReverb, reverbMode)


        if(reverbMode == 0){
            environmentalReverb.enabled = false
        } else {
            if(!environmentalReverb.enabled){
                environmentalReverb.enabled = true
            }
        }

//        if(!wasReverbSet){
            exoPlayer.setAuxEffectInfo(AuxEffectInfo(environmentalReverb.id, 1f))
//            wasReverbSet = true
//        }
    }

    private var wasBassBoostSet = false

    private fun changeVirtualizerLevel() {

        if(!isVirtualizerEnabled){
            effectVirtualizer.enabled = false
        } else {

            effectVirtualizer.setStrength(666)

            if(!effectVirtualizer.enabled){
                effectVirtualizer.enabled = true
            }
        }

//        if(!wasBassBoostSet){
            exoPlayer.setAuxEffectInfo(AuxEffectInfo(effectVirtualizer.id, 1f))
//            wasBassBoostSet = true
//        }
    }


//    private fun changeReverbMode() {
//
//        if(!wasReverbSet){
//            environmentalReverb = EnvironmentalReverb(0, 0)
//        }
//
//        setPreset(environmentalReverb, reverbMode)
//
//        if(reverbMode == 0){
//            environmentalReverb.enabled = false
//            environmentalReverb.release()
//            wasReverbSet = false
//            exoPlayer.clearAuxEffectInfo()
//        } else {
//            environmentalReverb.enabled = true
//            if(!wasReverbSet){
//                exoPlayer.setAuxEffectInfo(AuxEffectInfo(environmentalReverb.id, 1f))
//                wasReverbSet = true
//            }
//        }
//    }

//    private fun changeReverbMode() {
//
//        effectReverb.preset = reverbMode.toShort()
//
//        effectReverb.enabled = reverbMode != 0
//
//      if(!wasReverbSet){
//          exoPlayer.setAuxEffectInfo(AuxEffectInfo(effectReverb.id, 1f))
//          wasReverbSet = true
//      }
//
//    }


    private fun initialCheckForBuffer(){
        val buffPref = this@RadioService.getSharedPreferences(BUFFER_PREF, Context.MODE_PRIVATE)

        bufferSizeInMills = buffPref.getInt(BUFFER_SIZE_IN_MILLS, DEFAULT_MAX_BUFFER_MS)
//        bufferSizeInBytes = buffPref.getInt(BUFFER_SIZE_IN_BYTES, DEFAULT_TARGET_BUFFER_BYTES)
        bufferForPlayback = buffPref.getInt(BUFFER_FOR_PLAYBACK, DEFAULT_BUFFER_FOR_PLAYBACK_MS)
//        isToSetBufferInBytes = buffPref.getBoolean(IS_TO_SET_BUFFER_IN_BYTES, false)
        isAdaptiveLoaderToUse = buffPref.getBoolean(IS_ADAPTIVE_LOADER_TO_USE, false)

    }


//    lateinit var bandwidthMeter: DefaultBandwidthMeter

//    val handler = android.os.Handler(Looper.getMainLooper())
//    val listener = BandwidthMeter.EventListener { elapsedMs, bytesTransferred, bitrateEstimate ->
//
//        Log.d("CHECKTAGS", "elapse : $elapsedMs, bytes transfered : $bytesTransferred, bitrate : $bitrateEstimate")
//
//    }



    private fun provideExoPlayer () : ExoPlayer {

//        val bites = if (!isToSetBufferInBytes) -1
//        else bufferSizeInBytes * 1024

//        bandwidthMeter = DefaultBandwidthMeter.Builder(this@RadioService).build()
//        bandwidthMeter.addEventListener(handler, listener)


       val checkIntervals = if(isAdaptiveLoaderToUse){
           bufferSizeInMills/1000 * 10
       } else 1024


        return ExoPlayer.Builder(this@RadioService, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(MyMediaSourceFactory(this@RadioService, dataSourceFactory, checkIntervals))
            .setLoadControl(DefaultLoadControl.Builder()
                .setBufferDurationsMs(bufferSizeInMills, bufferSizeInMills, bufferForPlayback, 5000)
//                .setTargetBufferBytes(bites)
                .build())
//            .setBandwidthMeter(
//                bandwidthMeter
//            )
            .build().apply {
                addListener(radioPlayerEventListener)
            }

    }


    private fun recreateExoPlayer(){

        val isToPlay = exoPlayer.playWhenReady

        exoPlayer.stop()
        exoPlayer = provideExoPlayer()
        mediaSessionConnector.setPlayer(exoPlayer)

        preparePlayer(
            playNow = isToPlay,
            itemIndex = currentPlayingItemPosition,
            isToChangeMediaItems = true,
            isFromRecordings = currentMediaItems == SEARCH_FROM_RECORDINGS,
            isSameStation = false
            )
    }




//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//
//        intent?.let {
//
//            if(it.action == COMMAND_STOP_RECORDING) {
//
//                stopRecording()

//                NotificationManagerCompat.from(this@RadioService).cancel(RECORDING_NOTIFICATION_ID)
//            }
//        }
//
//        return super.onStartCommand(intent, flags, startId)
//    }



//    private fun createRecordingNotificationChannel(){
//
//        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//
//            val channel = NotificationChannel(
//                RECORDING_CHANNEL_ID, "Recording",
//                NotificationManager.IMPORTANCE_DEFAULT
//            )
//            channel.description = "Shows ongoing recording"
//
//            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//            notificationManager.createNotificationChannel(channel)
//        }
//    }



    private var isRecordingDurationListenerRunning = false





    fun listenToRecordDuration ()  {


        val format = exoPlayer.audioFormat
        val sampleRate = format?.sampleRate ?: 0
        val channels = format?.channelCount ?: 0

        Log.d("CHECKTAGS", "sampleRate is $sampleRate, channels : $channels")


        if(reverbMode != 0)
            exoPlayer.setAuxEffectInfo(AuxEffectInfo(environmentalReverb.id, 1f))

        if(isVirtualizerEnabled)
            exoPlayer.setAuxEffectInfo(AuxEffectInfo(effectVirtualizer.id, 1f))


//        visualizer.measurementMode = MEASUREMENT_MODE_PEAK_RMS
//
//        visualizer.enabled = true

  //      val peaks = visualizer.getMeasurementPeakRms(android.media.audiofx.Visualizer.MeasurementPeakRms())
//



//        val rate = Visualizer.getMaxCaptureRate()
//
//        visualizer.setDataCaptureListener(object : Visualizer.OnDataCaptureListener{
//            override fun onWaveFormDataCapture(
//                visualizer: Visualizer?,
//                waveform: ByteArray?,
//                samplingRate: Int
//            ) {
//                waveform?.let{
//                    val intensity = (it[0].toFloat() + 128f) / 256
//                    Log.d("CHECKTAGS", "intensity is: $intensity")
//                }
//            }
//
//            override fun onFftDataCapture(
//                visualizer: Visualizer?,
//                fft: ByteArray?,
//                samplingRate: Int
//            ) {
//
//            }
//        }, rate,  true, false)





        // FOR SKIPPING EXCLUSIVE AD

//        serviceScope.launch {
//
//            exoPlayer.playbackParameters = PlaybackParameters(6f)
//            exoPlayer.volume = 0f
//
//            while (true) {
//
//               delay(200)
//
//                if(exoPlayer.currentPosition > 5000)
//                    break
//
//            }
//
//            exoPlayer.playbackParameters = PlaybackParameters(1f)
//            exoPlayer.volume = 1f
//
//        }



        if(isFromRecording && !isRecordingDurationListenerRunning){

            isRecordingDurationListenerRunning = true

                  serviceScope.launch {

                while (isFromRecording && isPlaybackStatePlaying){

                    val pos = mediaSession.controller.playbackState.currentPlaybackPosition


                    recordingPlaybackPosition.postValue(pos)

                    if(exoPlayer.duration > 0){
                        recordingDuration.postValue(exoPlayer.duration)

                        val delay = exoPlayer.duration - pos

                        if(delay > 500){
                            delay(400)
                        } else {
                            delay(delay)
                            exoPlayer.seekTo(0)
                            exoPlayer.pause()
                            recordingPlaybackPosition.postValue(0)
                            break
                        }
                    } else {
                        delay(400)
                    }

                }
                isRecordingDurationListenerRunning = false

            }
        }
    }


    fun invalidateNotification(){
        mediaSessionConnector.invalidateMediaSessionQueue()
        mediaSessionConnector.invalidateMediaSessionMetadata()
        radioNotificationManager.resetBookmarkIcon()
        radioNotificationManager.updateNotification()
    }


   private inner class RadioQueueNavigator : TimelineQueueNavigator(mediaSession){

       override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat {

           if(currentMediaItems != SEARCH_FROM_RECORDINGS){
               if(windowIndex != currentPlayingItemPosition){
                   return MediaDescriptionCompat.Builder().build()
               } else {
                   val extra = Bundle()
                   extra.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentlyPlayingSong)
                   return MediaDescriptionCompat.Builder()
                       .setExtras(extra)
                       .setIconUri(currentRadioStation?.favicon?.toUri())
                       .setTitle(currentRadioStation?.name)
                       .setSubtitle(currentlyPlayingSong)
                       .build()
               }
           } else{
               return MediaDescriptionCompat.Builder()
                   .setIconUri(stationsFromRecordings[windowIndex].iconUri.toUri())
                   .setTitle(stationsFromRecordings[windowIndex].name)
                   .build()
           }


//           return currentStation?.description ?: radioSource.stationsFromApiMetadata.first().description

       }
   }



    private fun clearMediaItems(isNoList : Boolean = true){

//        if(exoPlayer.mediaItemCount != 1){
//
//            if(exoPlayer.currentMediaItemIndex != 0)
//                exoPlayer.moveMediaItem(exoPlayer.currentMediaItemIndex, 0)
//
//            exoPlayer.removeMediaItems(1, exoPlayer.mediaItemCount)
//        }
//


        if(exoPlayer.currentMediaItemIndex != 0){

            exoPlayer.removeMediaItems(0, exoPlayer.currentMediaItemIndex)
        }

        if(exoPlayer.mediaItemCount != 1){
            exoPlayer.removeMediaItems(1, exoPlayer.mediaItemCount)
        }

        if(isNoList){
            currentPlayingItemPosition = 0
            currentMediaItems = NO_PLAYLIST
        }

    }


    private fun preparePlayer(
//        playlist : List<MediaMetadataCompat>,
//        itemToPlay : MediaMetadataCompat?,
        playNow : Boolean,
        itemIndex : Int = -1,
        isToChangeMediaItems : Boolean = true,
        isFromRecordings : Boolean = false,
        isSameStation : Boolean

    ){

        isFromRecording = isFromRecordings

        val playbackSpeed = (if(isFromRecording) playbackSpeedRec
        else playbackSpeedRadio).toFloat()/100

        val playbackPitch = if(isFromRecording) playbackSpeed
        else playbackPitchRadio.toFloat()/100

        val params = PlaybackParameters(playbackSpeed, playbackPitch)
        exoPlayer.playbackParameters = params


        fadeOutPlayer()

        serviceScope.launch {
            delay(200)

            if(isToChangeMediaItems)
                updateMediaItems( currentMediaItems, isSameStation, itemIndex)

            if(!isSameStation){
                if(itemIndex != -1){
                    exoPlayer.seekTo(itemIndex, 0L)
                }
                exoPlayer.prepare()
            }

            exoPlayer.playWhenReady = playNow
        }


//        playFromUri(uri, playNow, itemIndex, isSamePlaylist)

      }


    private fun updateMediaItems(
        flag: Int,
        isSameStation: Boolean,
        itemIndex : Int
    ){

       val listOfMediaItems = when(flag){

            SEARCH_FROM_API -> {
                radioSource.stationsFromApiMediaItems
            }

            SEARCH_FROM_FAVOURITES -> {

                radioSource.stationsFavouredMediaItems

            }

            SEARCH_FROM_PLAYLIST -> {

                RadioSource.stationsInPlaylistMediaItems

            }

            SEARCH_FROM_HISTORY -> {

                radioSource.stationsFromHistoryMediaItems

            }
            SEARCH_FROM_HISTORY_ONE_DATE -> {

                RadioSource.stationsFromHistoryOneDateMediaItems

            }
            SEARCH_FROM_LAZY_LIST -> {
                RadioSource.lazyListMediaItems

            }
            SEARCH_FROM_RECORDINGS -> {
                stationsFromRecordingsMediaItems
            }
           else -> emptyList()
        }

        handleMediaItemsUpdate(listOfMediaItems, isSameStation, itemIndex)

    }


    private fun handleMediaItemsUpdate(items : List<MediaItem>, isSameStation: Boolean, index : Int){

        if(isSameStation){

            clearMediaItems(false)


            if(index == 0){
                if(items.size != 1)
                exoPlayer.addMediaItems(items.subList(1, items.lastIndex))
            } else {

                exoPlayer.addMediaItems(0, items.subList(0, index))

                if(index < items.lastIndex){
                    exoPlayer.addMediaItems(items.subList(index + 1, items.lastIndex))
                }
            }

            currentPlayingItemPosition = index

        } else {

            if(index != 0)
                isToIgnoreMediaItem = true

            exoPlayer.setMediaItems(items)
        }
    }

    var isToIgnoreMediaItem = false

    private fun adjustIndexFromHistory(index : Int) : Int {

        return if(index < radioSource.allHistoryMap[0])
            index -1
        else {

            var shift = 3

            for(i in 1 until radioSource.allHistoryMap.size){

                if( index < radioSource.allHistoryMap[i])
                    break
                else shift += 2
            }
            index - shift
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
            super.onTaskRemoved(rootIntent)

//        canOnDestroyBeCalled = true

        radioServiceConnection.disconnectBrowser()

        if(isToKillServiceOnAppClose){

            savePlayDurationOnServiceKill()

            exoPlayer.stop()
        }

        if(!exoPlayer.isPlaying){

//            NotificationManagerCompat.from(this@RadioService).cancel(RECORDING_NOTIFICATION_ID)

            radioNotificationManager.removeNotification()

        }
    }



    override fun onDestroy() {

        unregisterNewDateReceiver()

        mediaSessionConnector.setPlayer(null)
        mediaSessionConnector.setQueueNavigator(null)
        mediaSessionConnector.setPlaybackPreparer(null)

//        if(canOnDestroyBeCalled){
            Log.d("CHECKTAGS", "on destroy")
            mediaSession.run {
                isActive = false
                release()
            }


//            NotificationManagerCompat.from(this@RadioService).cancel(RECORDING_NOTIFICATION_ID)

            radioSource.allRecordingsLiveData.removeObserver(observerForRecordings)

            exoRecord.removeExoRecordListener("MainListener")



            exoPlayer.removeListener(radioPlayerEventListener)
            exoPlayer.release()


            serviceJob.cancel()

            serviceScope.cancel()
            android.os.Process.killProcess(android.os.Process.myPid())
//        }
    }


    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
//       when(parentId){
//           MEDIA_ROOT_ID -> {
//               val resultSent = radioSource.whenReady { isInitialized ->
//                   if(isInitialized){
//                        try{

//                            result.sendResult(radioSource.asMediaItems())

//                            if(!isPlayerInitialized && radioSource.stations.isNotEmpty()) {
//                                preparePlayer(radioSource.stations, radioSource.stations[0], false)
//                                isPlayerInitialized = true
//                            }
//                        } catch (e : java.lang.IllegalStateException){
//                         notifyChildrenChanged(MEDIA_ROOT_ID)
//                        }
//                   } else {
//                       mediaSession.sendSessionEvent(NETWORK_ERROR, null)
//                       result.sendResult(null)
//                   }
//               }
//               if(!resultSent) {
//                   result.detach()
//               }
//           }
//       }
    }


    fun updateStationLastClicked(stationId : String) = serviceScope.launch(Dispatchers.IO){
        isToUpdateLiveData = false
        databaseRepository.updateRadioStationLastClicked(stationId)
    }

    private var previousPlayedStationId = ""
    private var stationStartPlayingTime = 0L

    fun updateStationPlayedDuration() = serviceScope.launch(Dispatchers.IO){

        if(isPlaybackStatePlaying){
            if(previousPlayedStationId.isBlank()){
                previousPlayedStationId = currentRadioStation?.stationuuid ?: ""
                stationStartPlayingTime = System.currentTimeMillis()

                Log.d("CHECKTAGS", "duration start for id : $previousPlayedStationId")

            } else {
                durationUpdateHelper()
                stationStartPlayingTime = System.currentTimeMillis()
                previousPlayedStationId = currentRadioStation?.stationuuid ?: ""
            }

        } else {
            durationUpdateHelper()
            previousPlayedStationId = ""
        }
    }
    private suspend fun durationUpdateHelper(){
        val duration = System.currentTimeMillis() - stationStartPlayingTime
        if(duration > 20000 && previousPlayedStationId.isNotBlank()){
            databaseRepository.updateRadioStationPlayedDuration(
                previousPlayedStationId, duration
            )
        }
    }

    private fun savePlayDurationOnServiceKill(){
        if(isPlaybackStatePlaying){
            val duration = System.currentTimeMillis() - stationStartPlayingTime
            if(duration > 20000 && previousPlayedStationId.isNotBlank()){
                durationPref.edit().apply {
                    putString(LAST_PLAYED_STATION_ID, previousPlayedStationId)
                    putLong(LAST_PLAYED_STATION_DURATION, duration)
                }.commit()
            }
        }
    }

    private suspend fun recoverAndUpdateLastPlayDuration(){
       val lastStationId = durationPref.getString(LAST_PLAYED_STATION_ID, "") ?: ""
       if(lastStationId.isNotBlank()){
           val duration = durationPref.getLong(LAST_PLAYED_STATION_DURATION, 0)
           databaseRepository.updateRadioStationPlayedDuration(
               lastStationId, duration
           )
           durationPref.edit().putString(LAST_PLAYED_STATION_ID, "").apply()
       }
    }

    fun insertRadioStation(station : RadioStation) = serviceScope.launch(Dispatchers.IO){

        databaseRepository.insertRadioStation(station)

    }

    private fun initialHistoryPref(){
        historyPrefBookmark = historySettingsPref.getInt(HISTORY_PREF_BOOKMARK, HISTORY_BOOKMARK_PREF_DEFAULT)
    }


    private var isLastDateUpToDate = true


    private fun getLastDateAndCheck() = serviceScope.launch(Dispatchers.IO) {

        val date = databaseRepository.getLastDate()
        date?.let {
            currentDateString = it.date
            currentDateLong = it.time
        }

        val newTime = System.currentTimeMillis()
        calendar.time = Date(newTime)
        val update = Utils.fromDateToString(calendar)

        if(update != currentDateString){
            currentDateString = update
            currentDateLong = newTime
            isLastDateUpToDate = false
        }

        recoverAndUpdateLastPlayDuration()

        compareDatesWithPrefAndCLeanIfNeeded()
    }


    fun checkDateAndUpdateHistory(stationID: String) = serviceScope.launch(Dispatchers.IO) {

        if(!isLastDateUpToDate){
            isLastDateUpToDate = true
            val newDate = HistoryDate(currentDateString, currentDateLong)
            databaseRepository.insertNewDate(newDate)

            checkStationsAndReducePlayDurationIfNeeded()

        }
        databaseRepository.insertStationDateCrossRef(StationDateCrossRef(stationID, currentDateString))

    }



    private suspend fun checkStationsAndReducePlayDurationIfNeeded(){

        val stations = databaseRepository.getStationsForDurationCheck()

        val time = System.currentTimeMillis()

        stations.forEach {
            if(time - it.lastClick > PLAY_DURATION_MILLS_CHECK)
                databaseRepository.updateStationPlayDuration(it.playDuration / 2, it.stationuuid)
        }
    }

    private suspend fun compareDatesWithPrefAndCLeanIfNeeded() {

        val numberOfDatesInDB = databaseRepository.getNumberOfDates()

        val historyDatesPref = historySettingsPref.getInt(HISTORY_PREF_DATES, HISTORY_DATES_PREF_DEFAULT
        )

        if(historyDatesPref < numberOfDatesInDB) {

            val numberOfDatesToDelete = numberOfDatesInDB - historyDatesPref
            val deleteList = databaseRepository.getDatesToDelete(numberOfDatesToDelete)

            deleteList.forEach {

                databaseRepository.deleteAllCrossRefWithDate(it.date)
                databaseRepository.deleteDate(it)
                databaseRepository.deleteTitlesWithDate(it.time)
            }

            val stations = databaseRepository.gatherStationsForCleaning()

            stations.forEach {

//                val checkIfInPlaylists = databaseRepository.checkIfInPlaylists(it.stationuuid)
                val checkIfInHistory = databaseRepository.checkIfRadioStationInHistory(it.stationuuid)

                if(!checkIfInHistory){

                    databaseRepository.deleteRadioStation(it)
                }
            }
        }
    }



    private val newDateIntentFilter = IntentFilter().apply {
        addAction(Intent.ACTION_DATE_CHANGED)
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
    }


    private val newDateReceiver = NewDateReceiver(){

        val newTime = System.currentTimeMillis()
        calendar.time = Date(newTime)
        val update = Utils.fromDateToString(calendar)

        if(update != currentDateString){
            currentDateString = update
            currentDateLong = newTime
            isLastDateUpToDate = false
        }
    }

    private fun registerNewDateReceiver(){
       registerReceiver(newDateReceiver, newDateIntentFilter)
    }

    private fun unregisterNewDateReceiver(){
        unregisterReceiver(newDateReceiver)
    }


}