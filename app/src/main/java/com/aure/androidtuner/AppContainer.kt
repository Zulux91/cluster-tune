package com.aure.androidtuner

import android.content.Context
import com.aure.androidtuner.data.BundledPresetProvider
import com.aure.androidtuner.data.BootIdReader
import com.aure.androidtuner.data.CpuPolicyDetector
import com.aure.androidtuner.data.PerformanceRepository
import com.aure.androidtuner.data.ProfileStorage
import com.aure.androidtuner.data.SettingsStorage
import com.aure.androidtuner.root.PerformanceCommandBuilder
import com.aure.androidtuner.root.PServerSysfsReader
import com.aure.androidtuner.root.RootCommandRunner

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsStorage: SettingsStorage by lazy {
        SettingsStorage(appContext)
    }

    val repository: PerformanceRepository by lazy {
        PerformanceRepository(
            detector = CpuPolicyDetector(
                privilegedReader = PServerSysfsReader(appContext),
            ),
            bootIdReader = BootIdReader(),
            bundledPresetProvider = BundledPresetProvider(appContext),
            profileStorage = ProfileStorage(appContext),
            commandBuilder = PerformanceCommandBuilder(),
            rootCommandRunner = RootCommandRunner(appContext),
        )
    }
}
