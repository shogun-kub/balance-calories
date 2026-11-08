package com.balancecalories.app

import android.app.Application
import android.content.Context
import com.balancecalories.data.db.BalanceCaloriesDatabase
import com.balancecalories.data.db.createDatabase
import com.balancecalories.data.health.HealthConnectState
import com.balancecalories.data.health.HealthConnectSupport
import com.balancecalories.data.off.HttpUrlOffTransport
import com.balancecalories.data.off.OpenFoodFactsClient
import com.balancecalories.data.repo.AppSettingsRepository
import com.balancecalories.data.repo.BarcodeResolver
import com.balancecalories.data.repo.BodyMeasurementRepository
import com.balancecalories.data.repo.DebugRepository
import com.balancecalories.data.repo.FoodEntryRepository
import com.balancecalories.data.repo.ProductRepository
import com.balancecalories.data.repo.ProfileRepository
import com.balancecalories.data.repo.StarterPackImporter
import com.balancecalories.data.repo.TargetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    val products: ProductRepository = ProductRepository(database)
    val food: FoodEntryRepository = FoodEntryRepository(database)

    /** One client for the whole process, so its throttle paces every screen together, not per screen. */
    val offClient: OpenFoodFactsClient = OpenFoodFactsClient(HttpUrlOffTransport())

    val barcodes: BarcodeResolver = BarcodeResolver(products)

    /** Imports the bundled ingredient pack on first launch; idempotent, see StarterPackImporter. */
    val starterPack: StarterPackImporter = StarterPackImporter(
        database = database,
        openAsset = { context.assets.open(StarterPackImporter.ASSET_NAME) },
    )

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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // First launch imports the ingredient pack; the work is idempotent and off the main thread.
        appScope.launch { container.starterPack.ensureImported() }
        // Barcodes scanned while offline are retried once the app starts, throttled by the OFF client.
        appScope.launch {
            runCatching { container.barcodes.drainQueue(fetch = { container.offClient.lookup(it) }) }
        }
    }
}
