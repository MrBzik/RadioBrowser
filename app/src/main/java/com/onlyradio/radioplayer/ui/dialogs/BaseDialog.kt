package com.onlyradio.radioplayer.ui.dialogs

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.viewbinding.ViewBinding
import com.onlyradio.radioplayer.R
import com.onlyradio.radioplayer.ui.MainActivity
import com.onlyradio.radioplayer.ui.animations.slideAnim
import com.onlyradio.radioplayer.utils.dpToP

abstract class BaseDialog<VB : ViewBinding> (
    private val requireContext : Context,
    private val bindingInflater : (inflater : LayoutInflater) -> VB
    ) : AppCompatDialog(requireContext){

    var _bind : VB? = null

    val bind : VB
        get() = _bind!!

    override fun onCreate(savedInstanceState: Bundle?) {

        _bind = bindingInflater(layoutInflater)
        super.onCreate(savedInstanceState)

//        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)

        setContentView(bind.root)

        setupMainWindow()



        bind.root.slideAnim(400, 0, R.anim.scale_up)

    }

    val dp8 = 8f.dpToP(requireContext)

    fun adjustDialogHeight(view : ConstraintLayout){

        val bottomNanViewShift = 20f.dpToP(requireContext)

        view.minHeight = MainActivity.flHeight + bottomNanViewShift

    }


    fun removeDim(){
        window?.setDimAmount(0f)
    }

    fun removeTopPadding(){

        bind.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            setMargins(dp8,0,dp8, 0)
        }


    }

     fun setupMainWindow(){


             window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
             window?.setGravity(Gravity.TOP)


//        window?.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.navigationBarColor = ContextCompat.getColor(requireContext, R.color.main_background)

         window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)

//         window?.setDimAmount(0.5f)



         if(MainActivity.uiMode == Configuration.UI_MODE_NIGHT_NO){
             window?.setDimAmount(0.15f)
         }



         bind.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
//            setMargins(dp8,topMargin,dp8, bottomMargin)
            setMargins(dp8,dp8*2,dp8, 0)
        }
    }

}