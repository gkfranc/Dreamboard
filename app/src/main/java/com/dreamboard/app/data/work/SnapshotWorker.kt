package com.dreamboard.app.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dreamboard.app.domain.repository.FinanceRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/** Once a day, records a net-worth snapshot if the last one is a week old. Runs fully offline. */
class SnapshotWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun repository(): FinanceRepository
    }

    override suspend fun doWork(): Result {
        val repo = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).repository()
        return runCatching { repo.ensureRecentSnapshot() }.fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        private const val NAME = "net-worth-snapshot"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SnapshotWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
