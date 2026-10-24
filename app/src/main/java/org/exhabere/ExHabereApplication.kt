package org.exhabere

import android.app.Application
import org.exhabere.di.AppContainer
import org.exhabere.di.DefaultAppContainer

/**
 * Base Application class exposing the manual Dependency Injection container [AppContainer].
 */
class ExHabereApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        instance = this
    }

    companion object {
        lateinit var instance: ExHabereApplication
            private set
    }
}
