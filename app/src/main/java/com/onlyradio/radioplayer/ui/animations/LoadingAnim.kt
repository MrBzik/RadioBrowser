package com.onlyradio.radioplayer.ui.animations

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat



class LoadingAnim(
    context: Context,
    private val separator : View, private val separator2: View, private val separator3: View, private val separator4: View
) {

   private val activeColor = ContextCompat.getColor(context, com.onlyradio.radioplayer.R.color.separatorLoad)
   private val defaultColor = ContextCompat.getColor(context, com.onlyradio.radioplayer.R.color.Separator)

   private val anim = ValueAnimator.ofObject(ArgbEvaluator(), defaultColor, activeColor)


    fun startLoadingAnim() {

        anim.addUpdateListener {

            val colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                it.animatedValue as Int, BlendModeCompat.XOR
            )
            separator.background.colorFilter = colorFilter
            separator2.background.colorFilter = colorFilter
            separator3.background.colorFilter = colorFilter
            separator4.background.colorFilter = colorFilter
        }


        anim.duration = 1500
        anim.repeatMode = ValueAnimator.REVERSE
        anim.repeatCount = -1
        anim.start()
    }

    fun refresh(){
            val colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                defaultColor, BlendModeCompat.XOR
            )
            separator.background.colorFilter = colorFilter
            separator2.background.colorFilter = colorFilter
            separator3.background.colorFilter = colorFilter
            separator4.background.colorFilter = colorFilter
    }


    fun endLoadingAnim(){

        if(anim.isRunning){
            val color = anim.animatedValue as Int
            anim.cancel()

            val endAnim = ValueAnimator.ofObject(ArgbEvaluator(), color, defaultColor)

            endAnim.addUpdateListener {

                val colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                    it.animatedValue as Int, BlendModeCompat.XOR
                )
                separator.background.colorFilter = colorFilter
                separator2.background.colorFilter = colorFilter
                separator3.background.colorFilter = colorFilter
                separator4.background.colorFilter = colorFilter

            }

            endAnim.duration = 800
            endAnim.start()
        }
    }
}







