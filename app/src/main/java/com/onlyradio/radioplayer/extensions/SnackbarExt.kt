package com.onlyradio.radioplayer.extensions

import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import com.onlyradio.radioplayer.R

fun FragmentActivity.snackbarSimple(messageId : Int){

    Snackbar.make(this.findViewById(R.id.rootLayout), resources.getString(messageId), Snackbar.LENGTH_SHORT).show()

}

fun FragmentActivity.snackbarSimple(message : String){

    Snackbar.make(this.findViewById(R.id.rootLayout), message, Snackbar.LENGTH_SHORT).show()

}


fun FragmentActivity.snackbarWithAction(messageId : Int, action : () -> Unit){

    Snackbar.make(this.findViewById(R.id.rootLayout), resources.getString(messageId), Snackbar.LENGTH_LONG).apply {
        setAction(resources.getString(R.string.action_undo)){
            action()
        }
    }.show()

}

fun FragmentActivity.snackbarWithAction(message : String, action : () -> Unit){

    Snackbar.make(this.findViewById(R.id.rootLayout), message, Snackbar.LENGTH_LONG).apply {
        setAction(resources.getString(R.string.action_undo)){
            action()
        }
    }.show()

}