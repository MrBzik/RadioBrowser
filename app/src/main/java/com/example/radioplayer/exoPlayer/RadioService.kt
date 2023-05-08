package com.example.radioplayer.exoPlayer

import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Virtualizer
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.media.MediaBrowserServiceCompat
import com.bumptech.glide.RequestManager
import com.example.radioplayer.data.local.entities.RadioStation
import com.example.radioplayer.data.local.entities.Recording
import com.example.radioplayer.data.local.entities.Title
import com.example.radioplayer.exoPlayer.callbacks.RadioPlaybackPreparer
import com.example.radioplayer.exoPlayer.callbacks.RadioPlayerEventListener
import com.example.radioplayer.exoPlayer.callbacks.RadioPlayerNotificationListener
import com.example.radioplayer.utils.Constants.BUFFER_FOR_PLAYBACK
import com.example.radioplayer.utils.Constants.BUFFER_PREF
import com.example.radioplayer.utils.Constants.BUFFER_SIZE_IN_MILLS
import com.example.radioplayer.utils.Constants.COMMAND_CHANGE_BASS_LEVEL
import com.example.radioplayer.utils.Constants.COMMAND_CHANGE_REVERB_MODE

import com.example.radioplayer.utils.Constants.MEDIA_ROOT_ID
import com.example.radioplayer.utils.Constants.COMMAND_NEW_SEARCH
import com.example.radioplayer.utils.Constants.COMMAND_START_RECORDING
import com.example.radioplayer.utils.Constants.COMMAND_STOP_RECORDING

import com.example.radioplayer.utils.Constants.COMMAND_REMOVE_CURRENT_PLAYING_ITEM
import com.example.radioplayer.utils.Constants.COMMAND_RESTART_PLAYER
import com.example.radioplayer.utils.Constants.COMMAND_STOP_SERVICE
import com.example.radioplayer.utils.Constants.COMMAND_UPDATE_RADIO_PLAYBACK_PITCH
import com.example.radioplayer.utils.Constants.COMMAND_UPDATE_RADIO_PLAYBACK_SPEED
import com.example.radioplayer.utils.Constants.COMMAND_UPDATE_REC_PLAYBACK_SPEED
import com.example.radioplayer.utils.Constants.FOREGROUND_PREF
import com.example.radioplayer.utils.Constants.IS_ADAPTIVE_LOADER_TO_USE
import com.example.radioplayer.utils.Constants.RECONNECT_PREF
import com.example.radioplayer.utils.Constants.RECORDING_CHANNEL_ID
import com.example.radioplayer.utils.Constants.RECORDING_NOTIFICATION_ID

import com.example.radioplayer.utils.Constants.RECORDING_QUALITY_PREF
import com.example.radioplayer.utils.Constants.SEARCH_FROM_API
import com.example.radioplayer.utils.Constants.SEARCH_FROM_FAVOURITES
import com.example.radioplayer.utils.Constants.SEARCH_FROM_HISTORY
import com.example.radioplayer.utils.Constants.SEARCH_FROM_HISTORY_ONE_DATE
import com.example.radioplayer.utils.Constants.SEARCH_FROM_PLAYLIST
import com.example.radioplayer.utils.Constants.SEARCH_FROM_RECORDINGS
import com.example.radioplayer.utils.Constants.TITLE_UNKNOWN
import com.example.radioplayer.utils.setPreset
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.DefaultLoadControl.*
import com.google.android.exoplayer2.audio.*
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
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
import java.util.*
import javax.inject.Inject



private const val SERVICE_TAG = "service tag"

private const val RECORDING_HANDLER = "keeps info about last started recording"
private const val IS_RECORDING_HANDLED = "is recording handled"
private const val RECORDING_FILE_NAME = "file and path of the recording"
private const val RECORDING_NAME = "name of recording"
private const val RECORDING_TIMESTAMP = "rec. time stamp"
private const val RECORDING_ICON_URL = "rec. url path"
private const val RECORDING_SAMPLE_RATE = "rec. sample rate"
private const val RECORDING_CHANNELS_COUNT = "rec. channels count"



@AndroidEntryPoint
class RadioService : MediaBrowserServiceCompat() {

    @Inject
    lateinit var dataSourceFactory : Factory

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

//    @Inject
//    lateinit var radioServiceConnection: RadioServiceConnection

    private val serviceJob = SupervisorJob()

    val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    lateinit var radioNotificationManager: RadioNotificationManager

    private lateinit var mediaSession : MediaSessionCompat
    private lateinit var mediaSessionConnector : MediaSessionConnector

    private lateinit var radioPlayerEventListener: RadioPlayerEventListener

    var isForegroundService = false


    private var isPlayerInitialized = false



    private val recordingCheck : SharedPreferences by lazy {
        this@RadioService.getSharedPreferences(RECORDING_HANDLER, Context.MODE_PRIVATE)
    }

    private val observerForDatabase = Observer<List<RadioStation>>{
        radioSource.createMediaItemsFromDB(it)
    }



    var stationsFromRecordings = listOf<Recording>()
    var stationsFromRecordingsMediaItems = listOf<MediaItem>()
    var isStationsFromRecordingUpdated = true
    private val observerForRecordings = Observer<List<Recording>>{
        stationsFromRecordings = it

        val fileDir = this@RadioService.filesDir.path

        stationsFromRecordingsMediaItems = it.map { rec ->
            MediaItem.fromUri("$fileDir/${rec.id}")
        }

        isStationsFromRecordingUpdated = true

        radioSource.isRecordingUpdated.postValue(true)
    }

     var currentRadioStation: RadioStation? = null
     var currentRecording : Recording? = null

    companion object{

        var currenlyPlaingSong = TITLE_UNKNOWN
//        var currentStation : MediaMetadataCompat? = null
        var currentPlaylist = -1

        var currentPlayingStation : MutableLiveData<RadioStation> = MutableLiveData()
        var currentPlayingRecording : MutableLiveData<Recording> = MutableLiveData()
        var currentPlayingItemPosition = -1

        var isFromRecording = false

        var canOnDestroyBeCalled = false

        var isToKillServiceOnAppClose = false

        var currentDateLong : Long = 0

        val currentSongTitle = MutableLiveData<String>()
        val recordingPlaybackPosition = MutableLiveData<Long>()
        val recordingDuration = MutableLiveData<Long>()

        var playbackSpeedRec = 100
        var playbackSpeedRadio = 100
        var playbackPitchRadio = 100
        var isSpeedPitchLinked = true

        var isToReconnect = true

        var bufferSizeInMills = 0
//        var bufferSizeInBytes = 0
        var bufferForPlayback = 0
//        var isToSetBufferInBytes = false
        var isAdaptiveLoaderToUse = false

        var reverbMode = 0

        var virtualizerLevel = 0


    }


    private var recSampleRate = 0
    private var recChannelsCount = 2

    private fun searchRadioStations(
            isNewSearch : Boolean
    )
            = serviceScope.launch {

        radioSource.getRadioStations(isNewSearch)

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
            glide
            ) {
             exoPlayer.mediaMetadata.title
        }


        val radioPlaybackPreparer = RadioPlaybackPreparer(
            radioSource, { _, flag, playWhenReady, itemIndex ->

//            currentStation = itemToPlay
            val isSamePlaylist = currentPlaylist == flag
            currentPlaylist = flag

            val isFromRecordings = flag == SEARCH_FROM_RECORDINGS

            preparePlayer(
                playNow = playWhenReady,
                itemIndex =  itemIndex,
                isSamePlaylist = isSamePlaylist,
                isFromRecordings = isFromRecordings
            )

//            when (flag) {
//                SEARCH_FROM_FAVOURITES -> {
//                    preparePlayer(
//                        radioSource.stationsFavouredMetadata,
//                        itemToPlay,
//                        playWhenRady
//                    )
//                }
//                SEARCH_FROM_API -> {
//                    preparePlayer(
//                       playlist = radioSource.stationsFromApiMetadata,
//                       itemToPlay = itemToPlay,
//                       playNow = playWhenRady,
//                       itemIndex = itemIndex,
//                       isSamePlaylist = isSamePlaylist
//                    )
//                }
//                SEARCH_FROM_HISTORY -> {
//                    preparePlayer(
//                        radioSource.stationsFromHistoryMetadata,
//                        itemToPlay,
//                        playWhenRady
//                    )
//                }
//                SEARCH_FROM_RECORDINGS -> {
//                    preparePlayer(
//                        radioSource.recordings,
//                        itemToPlay,
//                        playNow = playWhenRady,
//                        isFromRecordings = true
//                    )
//                }
//
//                else -> {
//                    preparePlayer(
//                        radioSource.stationsFromPlaylistMetadata,
//                        itemToPlay,
//                        playWhenRady
//                    )
//                }
//            }


        }, {
            command, extras ->

            when(command){

                COMMAND_NEW_SEARCH -> {

                   val isNewSearch = extras?.getBoolean("IS_NEW_SEARCH") ?: false

                    searchRadioStations(isNewSearch)

                }

                COMMAND_START_RECORDING -> {

                    if(isPlaybackStatePlaying){
                        if(isExoRecordListenerToSet){
                            setExoRecordListener()
                            createRecordingNotificationChannel()
                        }
                        startRecording()
                    }

                }

                COMMAND_STOP_RECORDING -> {
                    stopRecording()
                }

                COMMAND_REMOVE_CURRENT_PLAYING_ITEM ->{
                    exoPlayer.clearMediaItems()
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

                COMMAND_STOP_SERVICE -> {

//                    radioServiceConnection.disconnectBrowser()

//                    Log.d("CHECKTAGS", "command stop service")
//                    exoPlayer.pause()
//                    exoPlayer.clearMediaItems()
//                    isToShutDown = true
//                    onTaskRemoved(intent)
//
//                    radioNotificationManager.removeNotification()
//                    stopForeground(STOP_FOREGROUND_REMOVE)
//                    stopForeground(true)
//
//                    stopSelf()

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

        radioSource.subscribeToFavouredStations.observeForever(observerForDatabase)
        radioSource.allRecordingsLiveData.observeForever(observerForRecordings)

        checkRecordingAndRecoverIfNeeded()

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

        if(virtualizerLevel == 0){
            effectVirtualizer.enabled = false
        } else {

            effectVirtualizer.setStrength(virtualizerLevel.toShort())

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



    class MyMediaSourceFactory(context: Context, datasourceFactory: DataSource.Factory, checkIntervals: Int) : MediaSource.Factory {
        private val defaultMediaSourceFactory: DefaultMediaSourceFactory
        private val progressiveFactory : MediaSource.Factory

        init {
            defaultMediaSourceFactory = DefaultMediaSourceFactory(context)
            progressiveFactory = ProgressiveMediaSource.Factory(datasourceFactory)
                .setContinueLoadingCheckIntervalBytes(1024 * checkIntervals)
        }


        override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
            defaultMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
            progressiveFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
            return this;
        }

        override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
            defaultMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            progressiveFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            return this
        }

        override fun getSupportedTypes(): IntArray {
            return defaultMediaSourceFactory.supportedTypes
        }

        override fun createMediaSource(mediaItem: MediaItem): MediaSource {
            if (mediaItem.localConfiguration?.uri?.path?.contains("m3u8") == true) {
                return defaultMediaSourceFactory.createMediaSource(mediaItem);

            }
            return progressiveFactory.createMediaSource(mediaItem)
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
            isSamePlaylist = false,
            isFromRecordings = currentPlaylist == SEARCH_FROM_RECORDINGS
            )
    }




    private fun checkRecordingAndRecoverIfNeeded(){

        val check = recordingCheck.getBoolean(IS_RECORDING_HANDLED, true)

        if(!check){



            convertRecording(
                recordingCheck.getString(RECORDING_FILE_NAME, "") ?: "",
                recordingCheck.getInt(RECORDING_SAMPLE_RATE, 44100),
                recordingCheck.getInt(RECORDING_CHANNELS_COUNT, 2),
                recordingCheck.getLong(RECORDING_TIMESTAMP, 0),
                300000
            )
        }
    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        intent?.let {

            if(it.action == COMMAND_STOP_RECORDING) {

                stopRecording()

                NotificationManagerCompat.from(this@RadioService).cancel(RECORDING_NOTIFICATION_ID)
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }



    private fun createRecordingNotificationChannel(){

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                RECORDING_CHANNEL_ID, "Recording",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "Shows ongoing recording"

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }



    private var isRecordingDurationListenerRunning = false
    var isPlaybackStatePlaying = false


    fun listenToRecordDuration ()  {


        if(reverbMode != 0)
            exoPlayer.setAuxEffectInfo(AuxEffectInfo(environmentalReverb.id, 1f))

        if(virtualizerLevel != 0)
            exoPlayer.setAuxEffectInfo(AuxEffectInfo(effectVirtualizer.id, 1f))


        serviceScope.launch {


            while (true){

                val format = exoPlayer.audioFormat
//
//                val sampleRate = format?.sampleRate



//                Log.d("CHECKTAGS", "sampleRate: $sampleRate, channels: $channels")

//
//                Log.d("CHECKTAGS", "buffer? : ${exoPlayer.totalBufferedDuration}")


                delay(2000)

            }

        }



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

                    if(exoPlayer.duration in 0..pos) {

                       exoPlayer.seekTo(currentPlayingItemPosition, 0)
                        exoPlayer.pause()
                       recordingPlaybackPosition.postValue(0)

                        break
                    }

                    recordingPlaybackPosition.postValue(pos)
                    if(exoPlayer.duration > 0){
                        recordingDuration.postValue(exoPlayer.duration)
                    }


                    delay(500)
                }
                isRecordingDurationListenerRunning = false
//                      isToSkip = true
            }
        }
    }



    private val exoRecordListener = object : ExoRecord.ExoRecordListener{

        lateinit var timer : Timer
        var duration = 0L


        override fun onStartRecording(recordFileName: String) {

            val notification = RecordingNotification(this@RadioService) {
                currentRadioStation?.name ?: ""
            }

            notification.showNotification()

            radioSource.exoRecordState.postValue(true)
            radioSource.exoRecordFinishConverting.postValue(false)

            timer = Timer()
            timer.scheduleAtFixedRate(object : TimerTask() {
                val startTime = System.currentTimeMillis()
                override fun run() {
                    duration = (System.currentTimeMillis() - startTime)
                    radioSource.exoRecordTimer.postValue(duration)
                }
            }, 0L, 1000L)

           recordingCheck.edit().apply{
               putBoolean(IS_RECORDING_HANDLED, false)
               putString(RECORDING_FILE_NAME, recordFileName)
               putString(RECORDING_NAME, currentRadioStation?.name ?: "")
               putString(RECORDING_ICON_URL, currentRadioStation?.favicon ?: "")
               putInt(RECORDING_SAMPLE_RATE, recSampleRate)
               putInt(RECORDING_CHANNELS_COUNT, recChannelsCount)
               putLong(RECORDING_TIMESTAMP, System.currentTimeMillis())
           }.apply()
        }

        override fun onStopRecording(record: IExoRecord.Record) {

            NotificationManagerCompat.from(this@RadioService).cancel(RECORDING_NOTIFICATION_ID)

            timer.cancel()
            isConverterWorking = true
            radioSource.exoRecordState.postValue(false)

            convertRecording(record.filePath, recSampleRate, recChannelsCount, System.currentTimeMillis(), duration)

        }
    }



    private fun convertRecording(
        filePath: String, sampleRate : Int, channelsCount : Int,
        timeStamp : Long, duration : Long
        )
            = CoroutineScope(Dispatchers.IO).launch {

        val recQualityPref = this@RadioService.application.getSharedPreferences(RECORDING_QUALITY_PREF, Context.MODE_PRIVATE)
        val setting = recQualityPref.getFloat(RECORDING_QUALITY_PREF, 0.4f)

        ExoRecordOgg.convertFile(
            this@RadioService.application,
            filePath,
            sampleRate,
            channelsCount,
            setting
        ){ progress ->

            if(progress == 100.0f){
                try {
                    insertNewRecording(
                        filePath,
                        timeStamp,
                        duration
                    )
                    deleteFile(filePath)
                    isConverterWorking = false
                    radioSource.exoRecordFinishConverting.postValue(true)
                    recordingCheck.edit().putBoolean(IS_RECORDING_HANDLED, true).apply()
                } catch (e: java.lang.Exception){
                    Log.d("CHECKTAGS", e.stackTraceToString())
                }
                this.cancel()
            }
        }
    }


    private suspend fun insertNewRecording(
        filePath : String, timeStamp : Long, duration : Long
    ) {

        val id = filePath.replace(".wav", ".ogg")
        val iconUri = recordingCheck.getString(RECORDING_ICON_URL, "") ?: ""
        val name = "Rec. ${ recordingCheck.getString(RECORDING_NAME, "") ?: ""}"

        radioSource.insertRecording(
            Recording(
                id, iconUri, timeStamp, name, duration
            )
        )
    }


    private var isExoRecordListenerToSet = true

    private var isConverterWorking = false

    private fun setExoRecordListener(){
        exoRecord.addExoRecordListener("MainListener", exoRecordListener)
        isExoRecordListenerToSet = false
    }


    private fun startRecording () = serviceScope.launch {
        if(!isConverterWorking){

            val format = exoPlayer.audioFormat
            val sampleRate = format?.sampleRate ?: 0
            val channels = format?.channelCount ?: 0

//            Log.d("CHECKTAGS", "$sampleRate, $channels")


            recSampleRate = if(sampleRate == 22050 && format?.sampleMimeType == "audio/mp4a-latm" ||
                sampleRate == 24000 && format?.sampleMimeType == "audio/mp4a-latm"
                    ) {
                recChannelsCount = 2
                sampleRate*2
            } else {
                recChannelsCount = channels
                sampleRate
            }

            Log.d("CHECKTAGS", "rec in : $recChannelsCount, $recSampleRate")

            exoRecord.exoRecordProcessor.configure(AudioProcessor.AudioFormat(sampleRate, channels,  C.ENCODING_PCM_16BIT))

            exoRecord.startRecording()
        }
    }

    private fun stopRecording () = serviceScope.launch {
       if(!isConverterWorking){
           exoRecord.stopRecording()
       }
    }

    fun invalidateNotification(){
        mediaSessionConnector.invalidateMediaSessionQueue()
        mediaSessionConnector.invalidateMediaSessionMetadata()
        radioNotificationManager.updateNotification()
    }




   private inner class RadioQueueNavigator : TimelineQueueNavigator(mediaSession){

       override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat {

           if(currentPlaylist != SEARCH_FROM_RECORDINGS){
               if(windowIndex != currentPlayingItemPosition){
                   return MediaDescriptionCompat.Builder().build()
               } else {
                   val extra = Bundle()
                   extra.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currenlyPlaingSong)
                   return MediaDescriptionCompat.Builder()
                       .setExtras(extra)
                       .setIconUri(currentRadioStation?.favicon?.toUri())
                       .setTitle(currentRadioStation?.name)
                       .setSubtitle(currenlyPlaingSong)
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



    private fun playFromUri (fromUri: String, playWhenReady : Boolean,
                             index : Int = -1, isSamePlaylist: Boolean = true){

        var uri = fromUri

        if(isFromRecording){
            val fileDir = this@RadioService.filesDir.path
            uri = "$fileDir/$uri"
        }

        Log.d("CHECKTAGS", "uri is : $uri")

//        val lastPath = uri.toUri().lastPathSegment

        val mediaItem = MediaItem.fromUri(uri.toUri())

//        val mediaSource = if(lastPath?.contains("m3u8") == true ||
//            lastPath?.contains("m3u") == true) {
//            Log.d("CHECKTAGS", "hls")
//            HlsMediaSource.Factory(dataSourceFactory)
//                 .setAllowChunklessPreparation(true)
//                .createMediaSource(mediaItem)
//        } else {
//
//            val checkInterval = if(isAdaptiveLoaderToUse) 1024*(bufferSizeInMills/1000) * 10
//                                    else 1024*1024
//
//            ProgressiveMediaSource.Factory(dataSourceFactory)
//                .setContinueLoadingCheckIntervalBytes(checkInterval)
//                .createMediaSource(mediaItem)
//        }
//
//        val defaultMediaSource = DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
//

        val playbackSpeed = (if(isFromRecording) playbackSpeedRec
        else playbackSpeedRadio).toFloat()/100

        val playbackPitch = if(isFromRecording) playbackSpeed
        else playbackPitchRadio.toFloat()/100

        val params = PlaybackParameters(playbackSpeed, playbackPitch)
        exoPlayer.playbackParameters = params


        fadeOutPlayer()

        serviceScope.launch {
            delay(200)

            if(currentPlaylist == SEARCH_FROM_API){
                if(!isSamePlaylist || radioSource.isStationsFromApiUpdated){
                    radioSource.isStationsFromApiUpdated = false
                    exoPlayer.setMediaItems(radioSource.stationsFromApiMediaItems)
                }
                exoPlayer.seekTo(index, 0L)
            } else {
                exoPlayer.setMediaItem(mediaItem)
            }

            exoPlayer.prepare()

            exoPlayer.playWhenReady = playWhenReady
        }

    }

    private fun preparePlayer(
//        playlist : List<MediaMetadataCompat>,
//        itemToPlay : MediaMetadataCompat?,
        playNow : Boolean,
        itemIndex : Int = -1,
        isSamePlaylist : Boolean = true,
        isFromRecordings : Boolean = false,

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

            when(currentPlaylist){

                SEARCH_FROM_API -> {
                    if(!isSamePlaylist || radioSource.isStationsFromApiUpdated){
                        radioSource.isStationsFromApiUpdated = false
                        exoPlayer.setMediaItems(radioSource.stationsFromApiMediaItems)
                    }
                }

                SEARCH_FROM_FAVOURITES -> {
                    if(!isSamePlaylist || radioSource.isStationsFavouredUpdated){
                        radioSource.isStationsFavouredUpdated = false
                        exoPlayer.setMediaItems(radioSource.stationsFavouredMediaItems)
                    }
                }

                SEARCH_FROM_PLAYLIST -> {
                    if(!isSamePlaylist || RadioSource.isStationsInPlaylistUpdated){
                        RadioSource.isStationsInPlaylistUpdated = false
                        exoPlayer.setMediaItems(RadioSource.stationsInPlaylistMediaItems)
                    }
                }

                SEARCH_FROM_HISTORY -> {
                    if(!isSamePlaylist || radioSource.isStationsFromHistoryUpdated){
                        radioSource.isStationsFromHistoryUpdated = false
                        exoPlayer.setMediaItems(radioSource.stationsFromHistoryMediaItems)
                    }
                }

                SEARCH_FROM_HISTORY_ONE_DATE -> {
                    if(!isSamePlaylist || radioSource.isStationsFromHistoryOneDateUpdated){
                        radioSource.isStationsFromHistoryOneDateUpdated = false
                        exoPlayer.setMediaItems(radioSource.stationsFromHistoryOneDateMediaItems)
                    }
                }

                SEARCH_FROM_RECORDINGS -> {
                    if(!isSamePlaylist || isStationsFromRecordingUpdated){
                        isStationsFromRecordingUpdated = false
                        exoPlayer.setMediaItems(stationsFromRecordingsMediaItems)

                    }
                }
            }

            if(itemIndex != -1){
                exoPlayer.seekTo(itemIndex, 0L)
            }

            exoPlayer.prepare()

            exoPlayer.playWhenReady = playNow
        }




//        playFromUri(uri, playNow, itemIndex, isSamePlaylist)


      }


    override fun onTaskRemoved(rootIntent: Intent?) {
            super.onTaskRemoved(rootIntent)

        canOnDestroyBeCalled = true

        if(isToKillServiceOnAppClose){
            exoPlayer.stop()
        }

        if(!exoPlayer.isPlaying){

            Log.d("CHECKTAGS", "not playing")

            NotificationManagerCompat.from(this@RadioService).cancel(RECORDING_NOTIFICATION_ID)

            radioNotificationManager.removeNotification()

        }
    }



    override fun onDestroy() {

        mediaSessionConnector.setPlayer(null)
        mediaSessionConnector.setQueueNavigator(null)
        mediaSessionConnector.setPlaybackPreparer(null)

        if(canOnDestroyBeCalled){
            Log.d("CHECKTAGS", "on destroy")
            mediaSession.run {
                isActive = false
                release()
            }


            NotificationManagerCompat.from(this@RadioService).cancel(RECORDING_NOTIFICATION_ID)
            radioSource.subscribeToFavouredStations.removeObserver(observerForDatabase)
            radioSource.allRecordingsLiveData.removeObserver(observerForRecordings)

            exoRecord.removeExoRecordListener("MainListener")



            exoPlayer.removeListener(radioPlayerEventListener)
            exoPlayer.release()


            serviceJob.cancel()

            serviceScope.cancel()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

//    override fun onBind(intent: Intent?): IBinder? {
//        Log.d("CHECKTAGS", "intent:  ${ intent?.action }")
//        return super.onBind(intent)
//    }
//
//    override fun onUnbind(intent: Intent?): Boolean {
//        Log.d("CHECKTAGS", "intent unbind:  ${ intent?.action }")
//        return super.onUnbind(intent)
//    }
//
//    override fun onRebind(intent: Intent?) {
//        Log.d("CHECKTAGS", "intent rebind:  ${ intent?.action }")
//        super.onRebind(intent)
//    }

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


}