package com.onlyradio.radioplayer.extensions

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.onlyradio.radioplayer.R

fun FragmentManager.navigate(fragment : Fragment){
    beginTransaction().apply {
        val enterAnim =
//            if(fragment is SettingsFragment)
//            android.R.anim.fade_in
//        else
            R.anim.blank_anim
        setCustomAnimations(enterAnim, android.R.anim.fade_out)
        replace(R.id.flFragment, fragment)
//        addToBackStack(null)
        commit()
    }
}