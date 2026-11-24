package com.dreamboard.app

import android.app.Application
import com.dreamboard.app.data.work.SnapshotWorker
import com.dreamboard.app.di.ApplicationScope
import com.dreamboard.app.domain.repository.FinanceRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DreamboardApplication : Application() {
    @Inject lateinit var repository: FinanceRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        SnapshotWorker.schedule(this)
        appScope.launch { runCatching { repository.ensureRecentSnapshot() } }
    }
}
