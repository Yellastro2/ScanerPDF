package com.nla.AIscanerPDF

import android.app.Application
import org.opencv.android.OpenCVLoader
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import com.nla.AIscanerPDF.core.di.appModules

class ScannerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        check(OpenCVLoader.initLocal()) { "Не удалось инициализировать OpenCV" }
        startKoin {
            androidLogger()
            androidContext(this@ScannerApp)
            modules(appModules)
        }
    }
}
