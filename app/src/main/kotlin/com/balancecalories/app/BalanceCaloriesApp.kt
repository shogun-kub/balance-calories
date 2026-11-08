package com.balancecalories.app

import android.app.Application
import android.content.Context
import com.balancecalories.data.db.BalanceCaloriesDatabase
import com.balancecalories.data.db.createDatabase
import com.balancecalories.data.health.HealthConnectState
import com.balancecalories.data.health.HealthConnectSupport
import com.balancecalories.data.repo.AppSettingsRepository
import com.balancecalories.data.repo.BodyMeasurementRepository
import com.balancecalories.data.repo.DebugRepository
import com.balancecalories.data.repo.ProfileRepository
import com.balancecalories.data.repo.TargetRepository

/**
 * Manual dependency container. Hilt is still deferred (docs/03-architecture.md §1 lists it), because
 * three repositories and one database do not yet justify an annotation processor in the build.
 */
class AppContainer(context: Context) {

    private val database: BalanceCaloriesDatabase = createDatabase(context)

    val profiles: ProfileRepository = ProfileRepository(database)
    val body: BodyMeasurementRepository = BodyMeasurementRepository(database)
    val targets: TargetRepository = TargetRepository(database)
    val settings: AppSettingsRepository = AppSettingsRepository(database)
    val debug: DebugRepository = DebugRepository(database)

    /**
     * Whether this phone can supply band data at all. The first profile claims the band when it can;
     * a phone without Health Connect keeps working for food and formulas.
     */
    val healthConnectAvailable: Boolean =
        HealthConnectSupport.state(context.applicationContext) == HealthConnectState.AVAILABLE
}

class BalanceCaloriesApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
