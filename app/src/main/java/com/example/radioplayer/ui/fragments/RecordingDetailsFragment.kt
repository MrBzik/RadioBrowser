package com.example.radioplayer.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SeekBar
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.radioplayer.data.local.entities.Recording
import com.example.radioplayer.data.models.PlayingItem
import com.example.radioplayer.databinding.FragmentRecordingDetailsBinding
import com.example.radioplayer.exoPlayer.isPlaying
import com.example.radioplayer.ui.MainActivity
import com.example.radioplayer.utils.Utils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RecordingDetailsFragment : BaseFragment<FragmentRecordingDetailsBinding>(
    FragmentRecordingDetailsBinding::inflate
)
{
    @Inject
    lateinit var glide : RequestManager

    private var currentRecording : Recording? = null

    private var isSeekBarToUpdate = true


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        subscribeToObservers()

        setSeekbarChangeListener()

        endLoadingBarIfNeeded()

    }

    private fun subscribeToObservers(){

        observeCurrentRecording()
        observePlaybackState()
        observePlayerPosition()


        mainViewModel.currentPlayerPosition.value?.let {
            Log.d("CHECKTAGS", "pos : $it")
            bind.seekBar.progress = it.toInt()
        }


    }


    private fun observeCurrentRecording(){

        mainViewModel.newRadioStation.observe(viewLifecycleOwner) {

        if(it is PlayingItem.FromRecordings){

            currentRecording = it.recording

            updateUiForRecording(it.recording)

            observeRecordingDuration()

            }
        }
    }


    private fun setSeekbarChangeListener(){
        bind.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if(fromUser){
                    setTvRecordingPlayingTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeekBarToUpdate = false
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    mainViewModel.seekTo(it.progress.toLong())
                    isSeekBarToUpdate = true
                }
            }
        })
    }


    private fun observePlayerPosition(){
        mainViewModel.currentPlayerPosition.observe(viewLifecycleOwner){
            if(isSeekBarToUpdate){
                bind.seekBar.progress = it.toInt()
                Log.d("CHECKTAGS", "pos : $it")
                setTvRecordingPlayingTime(it)
            }
        }
    }
    private fun setTvRecordingPlayingTime(time : Long){
        bind.tvRecordingPlayingTime.text = Utils.timerFormat(time)
    }

    private fun observeRecordingDuration(){
        mainViewModel.currentPlayerDuration.observe(viewLifecycleOwner){
            bind.seekBar.max = it.toInt()
        }
    }


    private fun observePlaybackState(){
        mainViewModel.playbackState.observe(viewLifecycleOwner){
            if(it?.isPlaying == true){
                bind.seekBar.progress = it.position.toInt()

            }
        }
    }

    private fun updateUiForRecording(recording: Recording){

        bind.tvName.text = recording.name
        glide
            .load(recording.iconUri)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(bind.ivIcon)

    }


    private fun endLoadingBarIfNeeded(){
        (activity as MainActivity).separatorLeftAnim.endLoadingAnim()
        (activity as MainActivity).separatorRightAnim.endLoadingAnim()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _bind = null
    }


}