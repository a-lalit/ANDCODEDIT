package com.andcodedit

import android.app.Application

/**
 * Application entry point. Holds process-wide singletons (settings, persistence)
 * and performs one-time initialisation.
 */
class AndCodEditApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: AndCodEditApp
            private set
    }
}
