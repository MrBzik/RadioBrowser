package com.example.radioplayer.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.example.radioplayer.ui.MainActivity
import com.example.radioplayer.ui.viewmodels.DatabaseViewModel
import com.example.radioplayer.ui.viewmodels.MainViewModel

abstract class BaseFragment<VB: ViewBinding>(
        private val bindingInflater : (inflater : LayoutInflater) -> VB
)
    : Fragment() {

    lateinit var bind : VB

    lateinit var databaseViewModel : DatabaseViewModel
    lateinit var mainViewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        bind = bindingInflater(inflater)
        return bind.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        databaseViewModel = (activity as MainActivity).databaseViewModel
        mainViewModel = (activity as MainActivity).mainViewModel
    }

}