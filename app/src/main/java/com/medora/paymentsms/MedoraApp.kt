package com.medora.paymentsms

import android.app.Application
import com.medora.paymentsms.data.api.ApiFactory
import com.medora.paymentsms.data.db.AppDatabase
import com.medora.paymentsms.data.prefs.AppStateStore
import com.medora.paymentsms.data.prefs.TokenStore
import com.medora.paymentsms.data.repository.PaymentRepository
import com.medora.paymentsms.sync.EventSyncWorker
import com.medora.paymentsms.sync.RetentionWorker

class MedoraApp : Application() {
    lateinit var tokenStore: TokenStore
        private set
    lateinit var stateStore: AppStateStore
        private set
    lateinit var repository: PaymentRepository
        private set

    override fun onCreate() {
        super.onCreate()
        tokenStore = TokenStore(this)
        stateStore = AppStateStore(this)
        repository = PaymentRepository.create(
            AppDatabase.get(this),
            ApiFactory.create(),
            tokenStore,
            stateStore,
        )

        RetentionWorker.scheduleDaily(this)
        if (stateStore.listeningEnabled && tokenStore.isPaired() && !stateStore.authRevoked) {
            EventSyncWorker.schedulePeriodic(this)
        }
    }
}
