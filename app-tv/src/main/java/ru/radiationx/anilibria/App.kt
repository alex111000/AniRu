package ru.radiationx.anilibria

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import ru.mintrocket.lib.mintpermissions.ext.initMintPermissions
import ru.mintrocket.lib.mintpermissions.flows.ext.initMintPermissionsFlow
import ru.radiationx.anilibria.di.AppModule
import ru.radiationx.data.di.DataModule
import ru.radiationx.quill.Quill
import timber.log.Timber

class App : Application() {

    companion object {
        /*
         * ContentProvider may be created before Application.onCreate. Consumers wait for
         * this flag before requesting dependencies from Quill.
         */
        val appCreateAction = MutableStateFlow(false)
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        // AniRu intentionally does not initialize the upstream AniLibria AppMetrica key.
        if (isMainProcess()) initInMainProcess()
        appCreateAction.value = true
    }

    private fun initInMainProcess() {
        initDependencies()
        initMintPermissions()
        initMintPermissionsFlow()
    }

    private fun initDependencies() {
        Quill.getRootScope().installModules(AppModule(this), DataModule(this))
    }

    private fun isMainProcess() = packageName == getCurrentProcessName()

    private fun getCurrentProcessName(): String? {
        val mypid = android.os.Process.myPid()
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.runningAppProcesses?.firstOrNull { it.pid == mypid }?.processName
    }
}
