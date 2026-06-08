package com.umavpn

import android.app.Application

class UmaVpnApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Eagerly initialise the manager so that binding to OpenVPN for Android
        // starts as soon as possible, making tile taps feel snappier.
        UmaVpnManager.getInstance(this)
    }
}
