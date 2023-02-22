package com.example.radioplayer.ui.fragments

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.view.isVisible
import androidx.core.view.marginLeft
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.radioplayer.R
import com.example.radioplayer.adapters.PagingRadioAdapter
import com.example.radioplayer.databinding.FragmentRadioSearchBinding
import com.example.radioplayer.ui.MainActivity
import com.example.radioplayer.ui.animations.animateSeparator
import com.example.radioplayer.ui.dialogs.TagPickerDialog
import com.example.radioplayer.ui.dialogs.CountryPickerDialog
import com.example.radioplayer.ui.dialogs.NameDialog
import com.example.radioplayer.utils.Constants
import com.example.radioplayer.utils.Constants.FAB_POSITION_X
import com.example.radioplayer.utils.Constants.FAB_POSITION_Y
import com.example.radioplayer.utils.Constants.SEARCH_FROM_API
import com.example.radioplayer.utils.listOfTags
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@FlowPreview
@ExperimentalCoroutinesApi
@AndroidEntryPoint
class RadioSearchFragment : BaseFragment<FragmentRadioSearchBinding>(
    FragmentRadioSearchBinding::inflate
) {


    private val allTags = listOfTags

    @Inject
    lateinit var pagingRadioAdapter : PagingRadioAdapter

    private var checkInitialLaunch = true


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        setSearchParamsObservers()

        setSearchToolbar()

        setRecycleView()

        setAdapterLoadStateListener()

        setAdapterOnClickListener()

        setOnRefreshSearch()

        listenSearchButton()

        subscribeToStationsFlow()

        setDragListenerForLayout()
        setDragListenerForButton()
        getFabSearchPositionIfNeeded()

    }


    private fun getFabSearchPositionIfNeeded(){
        bind.fabInitiateSearch.post{
            if (mainViewModel.isFabMoved) {
                bind.fabInitiateSearch.x = mainViewModel.fabX
                bind.fabInitiateSearch.y = mainViewModel.fabY
            }
            bind.fabInitiateSearch.isVisible = true
        }
    }

    private fun setDragListenerForLayout(){
            var tempX = 0f
            var tempY = 0f

        bind.root.setOnDragListener { v, event ->
            when(event.action){
                DragEvent.ACTION_DRAG_LOCATION -> {
                   tempX = event.x
                   tempY = event.y
                }
                DragEvent.ACTION_DRAG_ENDED ->{

                    bind.fabInitiateSearch.x = tempX - bind.fabInitiateSearch.width/2
                    bind.fabInitiateSearch.y = tempY - bind.fabInitiateSearch.height/2

                    mainViewModel.fabX = bind.fabInitiateSearch.x
                    mainViewModel.fabY = bind.fabInitiateSearch.y
                    mainViewModel.isFabUpdated = true
                }
            }
            true
        }
    }

    private fun setDragListenerForButton(){
        bind.fabInitiateSearch.setOnLongClickListener { view ->
            val shadow = View.DragShadowBuilder(bind.fabInitiateSearch)
            view.startDragAndDrop(null, shadow, view, 0)
            true
        }
    }


    private fun setAdapterOnClickListener(){

        pagingRadioAdapter.setOnClickListener {

            mainViewModel.playOrToggleStation(it, SEARCH_FROM_API)
            databaseViewModel.insertRadioStation(it)
            databaseViewModel.checkDateAndUpdateHistory(it.stationuuid)

        }

    }


    private fun setRecycleView(){

        bind.rvSearchStations.apply {

            adapter = pagingRadioAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)

        }
    }

    private fun setAdapterLoadStateListener(){

        pagingRadioAdapter.addLoadStateListener {


            if (it.refresh is LoadState.Loading ||
                it.append is LoadState.Loading)
//                bind.loadStationsProgressBar.isVisible = true
            {
//
                (activity as MainActivity).sideSeparatorStart?.
                setColorFilter(ContextCompat.getColor(
                    requireContext(), R.color.color_changed_on_interaction
                ), PorterDuff.Mode.OVERLAY)

                (activity as MainActivity).sideSeparatorEnd?.
                setColorFilter(ContextCompat.getColor(
                    requireContext(), R.color.color_changed_on_interaction
                ), PorterDuff.Mode.OVERLAY)

            }



            else {
//                bind.loadStationsProgressBar.visibility = View.GONE

                (activity as MainActivity).sideSeparatorStart?.colorFilter =
                    BlendModeColorFilterCompat
                        .createBlendModeColorFilterCompat(ContextCompat.getColor(
                            requireContext(), R.color.Separator
                        ), BlendModeCompat.OVERLAY)

                (activity as MainActivity).sideSeparatorEnd?.colorFilter =
                    BlendModeColorFilterCompat
                        .createBlendModeColorFilterCompat(ContextCompat.getColor(
                            requireContext(), R.color.Separator
                        ), BlendModeCompat.OVERLAY)



                if(isNewSearch){
                 handleScrollUpOnNewSearch()
                 isNewSearch = false
                }
            }
        }
    }

    private fun handleScrollUpOnNewSearch() = viewLifecycleOwner.lifecycleScope.launch {


        delay(100)
        bind.rvSearchStations.smoothScrollToPosition(0)
    }


    private var isNewSearch = false


    private fun subscribeToStationsFlow(){

        viewLifecycleOwner.lifecycleScope.launch {

            mainViewModel.stationsFlow.collectLatest {

                    if(checkInitialLaunch){
                        checkInitialLaunch = false
                    } else {
                        isNewSearch = true
                    }

                pagingRadioAdapter.submitData(it)

            }
        }
    }


    private fun setSearchToolbar() {


        bind.tvTag.setOnClickListener {

          TagPickerDialog(requireContext(), allTags, mainViewModel).apply {
              show()

          }
        }


        bind.tvName.setOnClickListener {

            NameDialog(requireContext(), it as TextView, mainViewModel).show()

        }

        bind.tvSelectedCountry.setOnClickListener {

            CountryPickerDialog(requireContext(), mainViewModel).show()

        }

    }


    private fun setOnRefreshSearch(){

        bind.swipeRefresh.setOnRefreshListener {

            initiateNewSearch()

            bind.swipeRefresh.isRefreshing = false

        }
    }

    private fun listenSearchButton(){

        bind.fabInitiateSearch.setOnClickListener {
            initiateNewSearch()
        }

    }


    private fun initiateNewSearch(){

        val name = bind.tvName.text.toString()

        val tag = bind.tvTag.text.toString()

        val country = bind.tvSelectedCountry.text.toString()

        val bundle = Bundle().apply {


            if(tag == "Tag") {
                putString("TAG", "")
            } else {
                putString("TAG", tag)
            }

           if(country == "Country"){
               putString("COUNTRY", "")
           } else {
               putString("COUNTRY", country)
           }

            if(name == "Name"){
                putString("NAME", "")
            } else {
                putString("NAME", name)
            }

        }
        mainViewModel.isNewSearch = true
        mainViewModel.setSearchBy(bundle)



    }


    private fun setSearchParamsObservers(){

        mainViewModel.searchParamTag.observe(viewLifecycleOwner){

             bind.tvTag.text = if (it == "") "Tag" else it

        }

        mainViewModel.searchParamName.observe(viewLifecycleOwner){
            bind.tvName.text = if (it == "") "Name" else it
        }

        mainViewModel.searchParamCountry.observe(viewLifecycleOwner){

           bind.tvSelectedCountry.text = if (it == "") "Country" else it
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        checkInitialLaunch = true
        bind.rvSearchStations.adapter = null
        _bind = null
    }

}





