package com.aistudio.kineticmedia

import android.app.Application
import com.aistudio.kineticmedia.feature.browser.BrowserViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

class KineticApp : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@KineticApp)
            modules(
                module {
                    viewModel { BrowserViewModel(get()) }
                }
            )
        }
    }
}
