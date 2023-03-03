package com.example.radioplayer.ui

import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.transition.Fade
import androidx.transition.Slide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.radioplayer.R
import com.example.radioplayer.data.models.PlayingItem
import com.example.radioplayer.databinding.ActivityMainBinding
import com.example.radioplayer.databinding.StubPlayerActivityMainBinding
import com.example.radioplayer.exoPlayer.isPlayEnabled
import com.example.radioplayer.exoPlayer.isPlaying
import com.example.radioplayer.ui.animations.LoadingAnim
import com.example.radioplayer.ui.animations.slideAnim
import com.example.radioplayer.ui.fragments.*
import com.example.radioplayer.ui.viewmodels.DatabaseViewModel
import com.example.radioplayer.ui.viewmodels.MainViewModel
import com.example.radioplayer.utils.Constants.FAB_POSITION_X
import com.example.radioplayer.utils.Constants.FAB_POSITION_Y
import com.example.radioplayer.utils.Constants.IS_FAB_UPDATED
import com.example.radioplayer.utils.Constants.SEARCH_FULL_COUNTRY_NAME
import com.example.radioplayer.utils.Constants.SEARCH_PREF_COUNTRY
import com.example.radioplayer.utils.Constants.SEARCH_PREF_NAME
import com.example.radioplayer.utils.Constants.SEARCH_PREF_TAG

import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    val mainViewModel : MainViewModel by viewModels()
    val databaseViewModel : DatabaseViewModel by viewModels()


    lateinit var bind : ActivityMainBinding


    lateinit var bindPlayer : StubPlayerActivityMainBinding

    private var isStubPlayerBindInflated = false

    var sideSeparatorStart : Drawable? = null
    var sideSeparatorEnd : Drawable? = null


    val separatorLeftAnim : LoadingAnim by lazy { LoadingAnim(sideSeparatorStart, this)  }
    val separatorRightAnim : LoadingAnim by lazy { LoadingAnim(sideSeparatorEnd, this)  }

    val animationIn : Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.fall_down) }
    val layoutAnimationController : LayoutAnimationController by lazy {
        LayoutAnimationController(animationIn).apply {
            delay = 0.1f
            order = LayoutAnimationController.ORDER_NORMAL
        }
    }
    val animationOut : Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.fade_out_anim) }
    val layoutAnimationControllerOut : LayoutAnimationController by lazy {
        LayoutAnimationController(animationOut).apply {
            delay = 0f
            order = LayoutAnimationController.ORDER_REVERSE
        }
    }



    @Inject
    lateinit var glide : RequestManager

    private var currentPlayingItem : PlayingItem? = null


    private  val radioSearchFragment : RadioSearchFragment by lazy { RadioSearchFragment() }
    private  val favStationsFragment : FavStationsFragment by lazy { FavStationsFragment() }
    private  val historyFragment : HistoryFragment by lazy { HistoryFragment() }
    private  val recordingsFragment : RecordingsFragment by lazy { RecordingsFragment() }
    private  val stationDetailsFragment : StationDetailsFragment by lazy { StationDetailsFragment().apply {
        enterTransition = Slide(Gravity.BOTTOM)
        exitTransition = Slide(Gravity.BOTTOM)
        }
    }

    private  val recordingDetailsFragment : RecordingDetailsFragment by lazy { RecordingDetailsFragment().apply {
        enterTransition = Slide(Gravity.BOTTOM)
        exitTransition = Slide(Gravity.BOTTOM)
         }
    }


    private var previousImageUri : Uri? = null


    override fun onBackPressed() {

        if(!isStubPlayerBindInflated) {
            this.moveTaskToBack(true)
        } else if (bindPlayer.tvExpandHideText.text == resources.getString(R.string.Expand)) {
            this.moveTaskToBack(true)
        } else {
            handleNavigationToFragments(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(R.style.Theme_RadioPlayer)

        bind = ActivityMainBinding.inflate(layoutInflater)
        setContentView(bind.root)

        bind.stubPlayer.setOnInflateListener{ _, inflated ->
                bindPlayer = StubPlayerActivityMainBinding.bind(inflated)
        }

        window.navigationBarColor = ContextCompat.getColor(this, R.color.toolbar)

        setupSidesSeparators()

        setupInitialNavigation()

        observeInternetConnection()

        observeNewStation()

        setOnBottomNavClickListener()

        setOnBottomNavItemReselect()


    }

    private fun observeInternetConnection() {

        mainViewModel.hasInternetConnection.observe(this){
            bind.ivNoInternet.isVisible = !it
        }
    }

    private fun setupSidesSeparators(){

        sideSeparatorStart = ContextCompat.getDrawable(this, R.drawable.gradient_for_separators)
        sideSeparatorEnd =ContextCompat.getDrawable(this, R.drawable.gradient_for_separators)
        bind.viewSeparatorStart.background = sideSeparatorStart
        bind.viewSeparatorEnd.background = sideSeparatorEnd

    }


    private fun setupInitialNavigation(){


        supportFragmentManager.beginTransaction().apply {
            replace(R.id.flFragment, radioSearchFragment)
            addToBackStack(null)
            commit()
        }

    }

    private fun observeNewStation(){

        mainViewModel.newRadioStation.observe(this){ playingItem ->

            currentPlayingItem = playingItem

            if(!isStubPlayerBindInflated) {
                inflatePlayerStubAndCallRelatedMethods()
            }


            updateImageAndTitle(playingItem)

        }
    }

    private fun inflatePlayerStubAndCallRelatedMethods (){


        isStubPlayerBindInflated = true
        bind.stubPlayer.visibility = View.VISIBLE

        bindPlayer.root.slideAnim(500, 0, R.anim.fade_in_anim)

        clickListenerToHandleNavigationWithDetailsFragment()

        onClickListenerForTogglePlay()
        observePlaybackStateToChangeIcons()
    }



    private fun setOnBottomNavClickListener(){

        bind.bottomNavigationView.setOnItemSelectedListener {
           handleNavigationToFragments(it)

        }
    }

    private fun setOnBottomNavItemReselect(){
        bind.bottomNavigationView.setOnItemReselectedListener {

            if(isStubPlayerBindInflated){
                if(bindPlayer.tvExpandHideText.text == resources.getString(R.string.Hide)){
                    handleNavigationToFragments(it)
                }
            }
        }
    }







    private fun clickListenerToHandleNavigationWithDetailsFragment(){

        bindPlayer.tvStationTitle.setOnClickListener{

            if(bindPlayer.tvExpandHideText.text == resources.getString(R.string.Expand)) {

                putFadeOutForDetailsFragment()

                supportFragmentManager.beginTransaction().apply {

                    if(mainViewModel.isRadioTrueRecordingFalse){
                        replace(R.id.flFragment, stationDetailsFragment)
                    } else {
                        replace(R.id.flFragment, recordingDetailsFragment)
                    }
                    addToBackStack(null)
                    commit()
                }

                bindPlayer.tvExpandHideText.setText(R.string.Hide)
          
            }

            else {
                handleNavigationToFragments(null)
            }
        }

    }

    private fun handleNavigationToFragments(item : MenuItem?) : Boolean {

        val menuItem = bind.bottomNavigationView.selectedItemId

        when(item?.itemId ?: menuItem) {
            R.id.mi_radioSearchFragment -> {

                radioSearchFragment.exitTransition = null

                supportFragmentManager.beginTransaction().apply {
                    setCustomAnimations(R.anim.blank_anim, android.R.anim.fade_out)
                    replace(R.id.flFragment, radioSearchFragment)
                    addToBackStack(null)
                    commit()
                }
            }
            R.id.mi_favStationsFragment -> {

                favStationsFragment.exitTransition = null

                supportFragmentManager.beginTransaction().apply {
                    setCustomAnimations(R.anim.blank_anim, android.R.anim.fade_out)
                    replace(R.id.flFragment, favStationsFragment)
                    addToBackStack(null)
                    commit()
                }
            }
            R.id.mi_historyFragment -> {

                historyFragment.exitTransition = null
                supportFragmentManager.beginTransaction().apply {
                    setCustomAnimations(R.anim.blank_anim, android.R.anim.fade_out)
                    replace(R.id.flFragment, historyFragment)
                    addToBackStack(null)
                    commit()
                }
            }

            R.id.mi_recordingsFragment -> {

                recordingsFragment.exitTransition = null
                supportFragmentManager.beginTransaction().apply {
                    setCustomAnimations(R.anim.blank_anim, android.R.anim.fade_out)
                    replace(R.id.flFragment, recordingsFragment)
                    addToBackStack(null)
                    commit()
                }
            }


        }

        if(isStubPlayerBindInflated){
            bindPlayer.tvExpandHideText.setText(R.string.Expand)

        }

        return true
    }


    private fun putFadeOutForDetailsFragment(){

        when(bind.bottomNavigationView.selectedItemId) {
            R.id.mi_radioSearchFragment -> {

                radioSearchFragment.exitTransition = Fade()

            }
            R.id.mi_favStationsFragment -> {

                favStationsFragment.exitTransition = Fade()

            }
            R.id.mi_historyFragment -> {

                historyFragment.exitTransition = Fade()

            }
            R.id.mi_recordingsFragment -> {

                recordingsFragment.exitTransition = Fade()
            }
        }
    }



    private fun updateImageAndTitle(playingItem : PlayingItem){

        val newImage : Uri?
        var name = ""

        when (playingItem) {
            is PlayingItem.FromRadio -> {
                name = playingItem.radioStation.name ?: ""
                newImage = playingItem.radioStation.favicon?.toUri()
            }
            is PlayingItem.FromRecordings -> {
                name = playingItem.recording.name
                newImage = playingItem.recording.iconUri.toUri()
            }
        }


        newImage?.let { uri ->

            if(previousImageUri == uri){/*DO NOTHING*/} else{

                glide
                    .load(uri)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(bindPlayer.ivCurrentStationImage)
                previousImageUri = uri
            }
        }

        bindPlayer.tvStationTitle.text = name

    }

    private fun onClickListenerForTogglePlay(){

        bindPlayer.ivTogglePlayCurrentStation.setOnClickListener {

            currentPlayingItem?.let {

                when(it){
                    is PlayingItem.FromRadio -> {
                        mainViewModel.playOrToggleStation(it.radioStation)
                    }
                    is PlayingItem.FromRecordings -> {
                        mainViewModel.playOrToggleStation(rec = it.recording)
                    }
                }
            }
        }
    }


    private fun observePlaybackStateToChangeIcons (){

        mainViewModel.playbackState.observe(this){

            it?.let {
                when{
                    it.isPlaying -> {
                        bindPlayer.ivTogglePlayCurrentStation
                            .setImageResource(R.drawable.ic_pause_play)

                    }
                    it.isPlayEnabled -> {
                        bindPlayer.ivTogglePlayCurrentStation
                            .setImageResource(R.drawable.ic_play_pause)

                    }
                }
            }
        }
    }



    override fun onStop() {
        super.onStop()
        this.cacheDir.deleteRecursively()
        databaseViewModel.removeUnusedStations()

        mainViewModel.searchPreferences.edit().apply {
            putString(SEARCH_PREF_TAG, mainViewModel.searchParamTag.value)
            putString(SEARCH_PREF_NAME, mainViewModel.searchParamName.value)
            putString(SEARCH_PREF_COUNTRY, mainViewModel.searchParamCountry.value)
            putString(SEARCH_FULL_COUNTRY_NAME, mainViewModel.searchFullCountryName)


        }.apply()

        if(mainViewModel.isFabUpdated){
            mainViewModel.fabPref.edit().apply {
                putFloat(FAB_POSITION_X, mainViewModel.fabX)
                putFloat(FAB_POSITION_Y, mainViewModel.fabY)
                putBoolean(IS_FAB_UPDATED, true)
            }.apply()
        }

    }

}

