package com.balancecalories.app

import android.app.Application
import android.content.Context
import com.balancecalories.data.db.BalanceCaloriesDatabase
import com.balancecalories.data.db.createDatabase
import com.balancecalories.data.health.HealthConnectActivitySource
import com.balancecalories.data.health.HealthConnectState
import com.balancecalories.data.health.HealthConnectSupport
import com.balancecalories.data.off.HttpUrlOffTransport
import com.balancecalories.data.off.OpenFoodFactsClient
import com.balancecalories.app.sync.ActivitySyncWorker
import com.balancecalories.data.repo.AppSettingsRepository
import com.balancecalories.data.repo.BarcodeResolver
import com.balancecalories.data.repo.BodyMeasurementRepository
import com.balancecalories.data.repo.DailyActivityRepository
import com.balancecalories.data.repo.DebugRepository
import com.balancecalories.data.repo.FoodEntryRepository
import com.balancecalories.data.repo.RecipeRepository
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
    val recipes: RecipeRepository = RecipeRepository(database)

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

    /**
     * The platform Health Connect state, for screens that must explain a missing store instead of showing an
     * empty card (Android 11 needs the standalone app; Android 14 has it in the platform).
     */
    val healthConnectState: HealthConnectState =
        HealthConnectSupport.state(context.applicationContext)

    /**
     * Null when there is no usable Health Connect. Kept nullable rather than throwing so no-band mode is a
     * supported state (research decision D9) and not an error path.
     */
    private val activitySource: HealthConnectActivitySource? =
        HealthConnectActivitySource.createIfAvailable(context.applicationContext)

    /**
     * Band activity: the only source of burned calories. Present even when [activitySource] is null, so the
     * UI can ask for a burn figure and receive an honest estimate; a sync simply reports `Unavailable`.
     */
    val activity: DailyActivityRepository = DailyActivityRepository(
        database = database,
        source = activitySource ?: ActivitySourceUnavailable,
    )

    /** The permissions the app must hold before any activity can be read. */
    val activityPermissions: Set<String> =
        HealthConnectActivitySource.REQUIRED_PERMISSIONS

    /** Suspends until Health Connect answers whether the read permissions are granted. */
    suspend fun hasActivityPermission(): Boolean =
        activitySource?.hasPermissions() ?: false
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
        // Band activity: schedule the background sync and try once now, so the first screen is not empty.
        if (container.healthConnectAvailable) {
            ActivitySyncWorker.schedulePeriodic(this)
            ActivitySyncWorker.syncNow(this)
        }
    }
}

/**
 * Stands in when this device has no Health Connect. It answers `Unavailable` to every read, which the
 * repository turns into a sync result the UI can explain — better than a null check in every caller.
 */
private object ActivitySourceUnavailable : com.balancecalories.data.health.ActivitySource {
    override suspend fun read(
        start: java.time.Instant,
        end: java.time.Instant,
    ): com.balancecalories.data.health.ActivityRead =
        com.balancecalories.data.health.ActivityRead.Unavailable
}