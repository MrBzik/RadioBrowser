package com.onlyradio.radioplayer.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.RequestManager
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.onlyradio.radioplayer.R
import com.onlyradio.radioplayer.connectivityObserver.ConnectivityObserver
import com.onlyradio.radioplayer.connectivityObserver.NetworkConnectivityObserver
import com.onlyradio.radioplayer.data.local.entities.RadioStation
import com.onlyradio.radioplayer.data.local.entities.Recording
import com.onlyradio.radioplayer.databinding.ActivityCrashBinding
import com.onlyradio.radioplayer.databinding.ActivityMainBinding
import com.onlyradio.radioplayer.databinding.StubPlayerActivityMainBinding
import com.onlyradio.radioplayer.exoPlayer.RadioService
import com.onlyradio.radioplayer.extensions.makeToast
import com.onlyradio.radioplayer.ui.animations.LoadingAnim
import com.onlyradio.radioplayer.ui.delegates.Navigation
import com.onlyradio.radioplayer.ui.delegates.NavigationImpl
import com.onlyradio.radioplayer.ui.fragments.*
import com.onlyradio.radioplayer.ui.stubs.MainPlayerView
import com.onlyradio.radioplayer.ui.viewmodels.DatabaseViewModel
import com.onlyradio.radioplayer.ui.viewmodels.HistoryViewModel
import com.onlyradio.radioplayer.ui.viewmodels.MainViewModel
import com.onlyradio.radioplayer.ui.viewmodels.RecordingsViewModel
import com.onlyradio.radioplayer.ui.viewmodels.SearchDialogsViewModel
import com.onlyradio.radioplayer.ui.viewmodels.SettingsViewModel
import com.onlyradio.radioplayer.utils.Constants.TEXT_SIZE_STATION_TITLE_PREF
import com.onlyradio.radioplayer.utils.CustomException
import com.onlyradio.radioplayer.utils.UpdatesStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    val mainViewModel: MainViewModel by viewModels()
    val settingsViewModel : SettingsViewModel by viewModels()
    val recordingsViewModel : RecordingsViewModel by viewModels()
    val historyViewModel : HistoryViewModel by viewModels()
    val favViewModel : DatabaseViewModel by viewModels()
    val searchDialogsViewModel : SearchDialogsViewModel by viewModels()

    lateinit var bind : ActivityMainBinding
    var bindPlayer : StubPlayerActivityMainBinding? = null

    private val separatorAnimation : LoadingAnim by lazy { LoadingAnim(this,
        bind.viewSeparatorStart!!, bind.viewSeparatorEnd!!,
        bind.separatorLowest!!, bind.separatorSecond!!) }


    private val animationIn : Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.fall_down) }
    val layoutAnimationController : LayoutAnimationController by lazy {
        LayoutAnimationController(animationIn).apply {
            delay = 0.1f
            order = LayoutAnimationController.ORDER_NORMAL
        }
    }

    private val navigation : Navigation by lazy {
        NavigationImpl(supportFragmentManager, mainViewModel)
    }

    private var playerUtils : MainPlayerView? = null


    @Inject
    lateinit var glide : RequestManager

    private var currentPlayingStation : RadioStation? = null
    private var currentPlayingRecording : Recording? = null


    private var appUpdateManager : AppUpdateManager? = null
    private val updateType = AppUpdateType.FLEXIBLE

    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->

        settingsViewModel.onInstallUpdateStatus(state)

    }

    private lateinit var connectivityObserver: NetworkConnectivityObserver

    companion object{
        var uiMode = 0
        var flHeight = 0
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var crash = false

        setTheme(R.style.Theme_RadioPlayer)

        try {
            bind = ActivityMainBinding.inflate(layoutInflater)
            setContentView(bind.root)

        } catch (e : Exception){
            crash = true
            val crashBinding : ActivityCrashBinding = ActivityCrashBinding.inflate(layoutInflater)
            setContentView(crashBinding.root)

            crashBinding.btnYes.setOnClickListener {
                throw CustomException("Downloaded from Google Play", e)
            }

            crashBinding.btnNo.setOnClickListener {
                throw CustomException("Not from Google Play", e)
            }

            crashBinding.btnSend.setOnClickListener {
                val text = crashBinding.textInputLayout.editText?.text.toString()
                if(text.isNotBlank())
                    throw CustomException(text, e)
            }

        }

        if(crash) return

        setOnBackPressed()

        setConnectivityObserver()

        initialCheckForUpdates()


        bind.stubPlayer.setOnInflateListener{ _, view ->
            bindPlayer = StubPlayerActivityMainBinding.bind(view)
            playerUtils = MainPlayerView(bindPlayer!!, glide, resources.getString(R.string.time_left))
            callPlayerStubRelatedMethods()
        }

        uiMode = this.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        observeIsToPlayLoadingAnim()

        refreshSeparators()

        navigation.initialNavigation()

        observeNewStation()

        observeRecordingDuration()

        setOnBottomNavClickListener()

        setOnBottomNavItemReselect()

        observeUpdatesState()

            bind.root.doOnLayout {
                flHeight = bind.viewHeight.height
            }
    }

    private fun setOnBackPressed(){

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                if(mainViewModel.isInDetailsFragment.value == false) {
                    this@MainActivity.moveTaskToBack(true)
                }  else {
                    navigation.handleNavigationToFragments(bind.bottomNavigationView.selectedItemId)
                }
            }
        })
    }

    private fun setConnectivityObserver() {

        connectivityObserver = NetworkConnectivityObserver(this)

        mainViewModel.updateInternetConnectionStatus(connectivityObserver.isNetworkAvailable())

        connectivityObserver.observe().onEach {

            val status = when (it) {
                ConnectivityObserver.Status.Available -> true
                else -> false
            }

            mainViewModel.updateInternetConnectionStatus(status)

        }.launchIn(lifecycleScope)
    }


    private fun initialCheckForUpdates(){

        appUpdateManager = AppUpdateManagerFactory.create(applicationContext)
        appUpdateManager?.registerListener(installStateUpdatedListener)

        val isToUpdate = settingsViewModel.checkUpdatesPref()

        checkForUpdates(isToUpdate)

    }


    private fun checkForUpdates(isToUpdate : Boolean){
        appUpdateManager?.appUpdateInfo?.addOnSuccessListener { info->

            val isAvailable = settingsViewModel.onUpdatesSuccessListener(info)

            if(isAvailable && isToUpdate)
                initializeUpdate(info)
        }
    }


    private fun initializeUpdate(info: AppUpdateInfo){
        try {
            appUpdateManager?.startUpdateFlowForResult(
                info, this, AppUpdateOptions.defaultOptions(updateType), 123
            )
        } catch (e: Exception){
            this.makeToast(R.string.updates_status_failed)
        }
    }

    private fun observeUpdatesState(){

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED){
                settingsViewModel.updatesStatus.collectLatest { status ->

                    when(status){

                        UpdatesStatus.UPDATES_DOWNLOADING -> {
                            this@MainActivity.makeToast(R.string.update_downloading)
                        }

                        UpdatesStatus.UPDATES_DOWNLOADED -> {
                            try {
                                this@MainActivity.makeToast(R.string.update_downloaded)
                                appUpdateManager?.completeUpdate()
                            } catch (e: Exception){
                                this@MainActivity.makeToast(R.string.updates_status_failed)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }


        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED){
                settingsViewModel.updatesInitialize.collectLatest { isToUpdate ->
                    checkForUpdates(isToUpdate)
                }
            }
        }
    }


    private fun observeRecordingDuration(){

        lifecycleScope.launch {

            repeatOnLifecycle(Lifecycle.State.STARTED){

                recordingsViewModel.durationWithPosition.collectLatest {
                    playerUtils?.updateRecordingDuration(it)
                }
            }
        }
    }



    private fun observeIsInDetails(){

        mainViewModel.isInDetailsFragment.observe(this){
            if(it){
                bindPlayer?.tvExpandHideText?.setText(R.string.Hide)
            } else {
                bindPlayer?.tvExpandHideText?.setText(R.string.Expand)
            }
        }
    }

    private fun observeIsToPlayLoadingAnim(){

        mainViewModel.isToPlayLoadAnim.observe(this){

            if(uiMode == Configuration.UI_MODE_NIGHT_YES){
                if(it) separatorAnimation.startLoadingAnim()
                else separatorAnimation.endLoadingAnim()
            } else {
                if(!it || mainViewModel.isNewSearch)
                    bind.progressBarBottom?.hide()
                else bind.progressBarBottom?.show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mainViewModel.connectMediaBrowser()
    }


    private fun refreshSeparators(){
        if(!mainViewModel.isInitialLaunchOfTheApp && uiMode == Configuration.UI_MODE_NIGHT_YES){
            separatorAnimation.refresh()
        }
    }


    private fun handleStubPlayer(){

        bindPlayer?.let {
            if(it.root.visibility == View.GONE)
                playerUtils?.slideInPlayer()
        } ?: kotlin.run {
            bind.stubPlayer.inflate()
        }

        playerUtils?.updateImage()

    }


    private fun observeNewStation(){



        RadioService.currentPlayingStation.observe(this){ station ->

            currentPlayingStation = station

            handleStubPlayer()

        }


        RadioService.currentPlayingRecording.observe(this){ recording ->

            currentPlayingRecording = recording

            handleStubPlayer()

        }
    }

    private fun callPlayerStubRelatedMethods (){

        playerUtils?.slideInPlayer()

        clickListenerToHandleNavigationWithDetailsFragment()

        onClickListenerForTogglePlay()

        observePlaybackStateToChangeIcons()

        observeCurrentSongTitle()

        observePlayerBufferingState()

        observeIsInDetails()

    }

    private fun observePlayerBufferingState(){
        mainViewModel.isPlayerBuffering.observe(this){
            bindPlayer?.progressBuffer?.isVisible = it
        }
    }

    private fun observeCurrentSongTitle (){

        mainViewModel.currentSongTitle.observe(this){ title ->

            playerUtils?.handleTitleText(title)
        }
    }


    private fun setOnBottomNavClickListener(){

        bind.bottomNavigationView.setOnItemSelectedListener {
            navigation.handleNavigationToFragments(it.itemId)
            true
        }
    }

    private fun setOnBottomNavItemReselect(){
        bind.bottomNavigationView.setOnItemReselectedListener {
            if(mainViewModel.isInDetailsFragment.value == true){
                navigation.handleNavigationToFragments(it.itemId)
            }
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun clickListenerToHandleNavigationWithDetailsFragment(){

        bindPlayer?.tvStationTitle?.setOnTouchListener { _, event ->

            if (event.action == MotionEvent.ACTION_DOWN){
                navigation.handleNavigationWithDetailsFragment(bind.bottomNavigationView.selectedItemId)
            }
            true
        }
    }



    private fun onClickListenerForTogglePlay(){

        bindPlayer?.ivTogglePlayCurrentStation?.setOnClickListener { _ ->

            if(RadioService.isFromRecording){

                currentPlayingRecording?.let { recordingsViewModel.playOrToggleRecording(rec = it) }

            } else {

                currentPlayingStation?.let { mainViewModel.playOrToggleStation(
                    it.stationuuid,
                    isToChangeMediaItems = false,
                    searchFlag = RadioService.currentMediaItems
                ) }
            }
        }
    }


    private fun observePlaybackStateToChangeIcons (){

        mainViewModel.isPlaying.observe(this){

            playerUtils?.handleIcons(it)

        }
    }



    override fun onStop() {
        super.onStop()
        this.cacheDir.deleteRecursively()

        mainViewModel.saveSearchPrefs()

        settingsViewModel.textSizePref.edit()
            .putFloat(TEXT_SIZE_STATION_TITLE_PREF, settingsViewModel.stationsTitleSize).apply()

    }

    override fun onDestroy() {
        super.onDestroy()
        appUpdateManager?.unregisterListener(installStateUpdatedListener)

    }
}

