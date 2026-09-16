package com.blake.editor

import android.app.Application
import com.blake.editor.app.AppContainer

class BlakeEditorApp : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
