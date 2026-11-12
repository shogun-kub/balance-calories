package com.balancecalories.app.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.balancecalories.app.ui.ChartPalette
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.app.AppContainer
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.app.R
import com.balancecalories.core.food.Barcode
import com.balancecalories.core.food.Product
import com.balancecalories.data.off.UnusableReason
import com.balancecalories.data.repo.Lookup
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the screen is doing, so the user sees a reason rather than a silent camera. */
/** How long the green frame and the vibration are on screen before the product opens. */
private const val SCAN_CONFIRM_MS = 400L

/** Bright green for the "code read" state: the overlay is drawn on camera pixels, not on a themed surface. */
private val ScanFoundGreen = Color(0xFF81C784)

/** How long a tap-to-focus reticle stays visible. */
private const val FOCUS_RETICLE_MS = 600L

private sealed interface ScanState {
    data object Scanning : ScanState
    data class Looking(val barcode: String) : ScanState
    data class NotFound(val barcode: String) : ScanState
    data class Offline(val barcode: String) : ScanState
    data class Unusable(val barcode: String, val reason: UnusableReason) : ScanState
    data class Invalid(val barcode: String) : ScanState
}

/**
 * The scanner: camera frames in, a product out.
 *
 * Deliberate choices: the analyzer runs on a single background thread and keeps only the latest frame, so
 * a slow decode can never queue up; the same code is ignored for a couple of seconds so one barcode does
 * not fire five lookups; and a decode result is reported to the user *before* the network call finishes,
 * because waiting on a spinner to learn that a barcode was read is the most annoying part of every scanner.
 *
 * The camera is optional: typing the barcode by hand reaches the same resolver.
 */
@Composable
fun ScannerScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onProduct: (Product) -> Unit,
    onAddManually: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var deniedForGood by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<ScanState>(ScanState.Scanning) }
    var manualCode by remember { mutableStateOf("") }
    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    // The code the camera just read. Non-null means "we saw it", which is a different moment from "we found
    // the product": the frame turns green and says the number immediately, before the lookup answers.
    var detected by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current
    // Where the last tap-to-focus landed, in the preview's own pixels; cleared after a moment.
    var focusTap by remember { mutableStateOf<Offset?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed ->
        granted = allowed
        deniedForGood = !allowed
    }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(focusTap) {
        if (focusTap != null) {
            delay(FOCUS_RETICLE_MS)
            focusTap = null
        }
    }

    fun lookup(raw: String) {
        val barcode = Barcode.normalize(raw)
        if (barcode == null) {
            state = ScanState.Invalid(raw)
            return
        }
        if (state is ScanState.Looking) return
        // Confirmation first, resolution second: a scanner that waits for the network to say "I read it"
        // makes the person move the phone away at the exact moment the code was captured.
        detected = barcode
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        state = ScanState.Looking(barcode)
        scope.launch {
            val result = container.barcodes.resolve(barcode) { container.offClient.lookup(it) }
            state = when (result) {
                is Lookup.Cached -> {
                    // The green frame and the vibration have already said "read"; this pause is only long
                    // enough for the eye to land on the confirmation before the screen changes.
                    delay(SCAN_CONFIRM_MS)
                    onProduct(result.product)
                    ScanState.Scanning
                }

                is Lookup.Fetched -> {
                    delay(SCAN_CONFIRM_MS)
                    onProduct(result.product)
                    ScanState.Scanning
                }

                Lookup.NotFound, Lookup.KnownMiss -> {
                    detected = null
                    ScanState.NotFound(barcode)
                }
                is Lookup.Offline -> {
                    detected = null
                    ScanState.Offline(barcode)
                }
                is Lookup.Unusable -> {
                    detected = null
                    ScanState.Unusable(barcode, result.reason)
                }
                is Lookup.Invalid -> {
                    detected = null
                    ScanState.Invalid(result.barcode)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            Text(text = stringResource(R.string.scan_title), style = MaterialTheme.typography.titleLarge)
        }

        if (granted) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(Color.Black, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                CameraPreview(
                    lifecycleOwner = lifecycleOwner,
                    onBarcode = ::lookup,
                    onCamera = { camera = it },
                    frozen = detected != null,
                    onFocus = { focusTap = it },
                    modifier = Modifier.fillMaxSize(),
                )
                // A frame, not a scanning laser: the user aims the whole code at it. It turns green the
                // moment a code is read, which is the confirmation the screen was missing.
                val found = detected
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(90.dp)
                        .border(
                            width = if (found != null) 3.dp else 2.dp,
                            color = if (found != null) ScanFoundGreen else Color.White,
                            shape = RoundedCornerShape(8.dp),
                        ),
                )
                Text(
                    text = if (found != null) {
                        stringResource(R.string.scan_found, found)
                    } else {
                        stringResource(R.string.scan_hint)
                    },
                    // The overlay sits on camera pixels, not on the app surface, so it uses a fixed bright
                    // green and a dark pill rather than the theme's tones: the light theme's protein green
                    // is a dark olive and disappeared into the video (measured on the Redmi, light theme).
                    color = if (found != null) ScanFoundGreen else Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                // A reticle where the tap landed, so a touch-focus is visible rather than an invisible
                // guess about whether the tap registered.
                val tap = focusTap
                if (tap != null) {
                    val density = LocalDensity.current
                    val side = 56.dp
                    val sidePx = with(density) { side.toPx() }
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (tap.x - sidePx / 2f).toInt(),
                                    y = (tap.y - sidePx / 2f).toInt(),
                                )
                            }
                            .size(side)
                            .border(2.dp, Color.White, CircleShape),
                    )
                }
            }
            WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        torch = !torch
                        camera?.cameraControl?.enableTorch(torch)
                    },
                ) {
                    Text(stringResource(if (torch) R.string.scan_torch_on else R.string.scan_torch_off))
                }
            }
        } else {
            Text(text = stringResource(R.string.scan_permission_rationale))
            Text(text = stringResource(R.string.scan_permission_denied))
            if (deniedForGood) {
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            },
                        )
                    },
                ) {
                    Text(stringResource(R.string.action_open_settings))
                }
            }
        }

        stateMessage(state)?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

        if (state is ScanState.NotFound) {
            Text(text = stringResource(R.string.scan_not_found_note))
            val barcode = (state as ScanState.NotFound).barcode
            TextButton(onClick = { onAddManually(barcode) }) {
                Text(stringResource(R.string.scan_add_manually))
            }
        }

        OutlinedTextField(
            value = manualCode,
            onValueChange = { manualCode = it },
            label = { Text(stringResource(R.string.scan_manual_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { lookup(manualCode) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_scan))
        }
    }
}

@Composable
private fun stateMessage(state: ScanState): String? = when (state) {
    ScanState.Scanning -> null
    is ScanState.Looking -> stringResource(R.string.scan_looking, state.barcode)
    is ScanState.NotFound -> stringResource(R.string.scan_not_found, state.barcode)
    is ScanState.Offline -> stringResource(R.string.scan_offline)
    is ScanState.Unusable -> stringResource(R.string.scan_unusable)
    is ScanState.Invalid -> stringResource(R.string.scan_invalid)
}

/** CameraX preview plus analysis, bound to the screen's lifecycle and released with it. */
@Composable
private fun CameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onBarcode: (String) -> Unit,
    onCamera: (Camera?) -> Unit,
    frozen: Boolean,
    onFocus: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { ZxingBarcodeScanner() }
    var lastCode by remember { mutableStateOf<String?>(null) }
    var lastAt by remember { mutableLongStateOf(0L) }
    // The touch listener is created inside the AndroidView factory, which cannot read composable state, so
    // the camera handle lives in a holder both sides can reach, and `frozen` is read through a state object.
    val cameraHolder = remember { mutableStateOf<Camera?>(null) }
    val frozenNow = rememberUpdatedState(frozen)

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // A plain TextureView, not PreviewView: PreviewView's internal SurfaceView laid itself out at
            // 720x1280 inside a 656x240 parent and painted over the title and the buttons (measured with
            // `dumpsys activity top`). A TextureView we own is clipped by its parent like any other view.
            val textureView = TextureView(ctx)
            textureView.setOnTouchListener { view, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                    val width = view.width.toFloat()
                    val height = view.height.toFloat()
                    val camera = cameraHolder.value
                    if (camera != null && width > 0f && height > 0f) {
                        // A PreviewView would hand us a metering factory; a TextureView we own does not, so
                        // the factory is built from the view's own pixel size, which is all it needs to map
                        // a tap to a sensor region.
                        val factory = ViewMeteringPointFactory(width, height)
                        // CameraX takes the metering point in the factory's own units: [0..1] across the
                        // preview, which the factory then scales by the surface's aspect ratio.
                        val point = factory.createPoint(
                            (event.x / width).coerceIn(0f, 1f),
                            (event.y / height).coerceIn(0f, 1f),
                        )
                        val action = FocusMeteringAction.Builder(
                            point,
                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                        camera.cameraControl.startFocusAndMetering(action)
                        onFocus(Offset(event.x, event.y))
                    }
                    view.performClick()
                }
                true
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { preview ->
                    preview.setSurfaceProvider(ContextCompat.getMainExecutor(ctx)) { request ->
                        val texture = textureView.surfaceTexture
                        if (texture == null) {
                            request.willNotProvideSurface()
                        } else {
                            val surface = Surface(texture)
                            request.provideSurface(surface, ContextCompat.getMainExecutor(ctx)) {
                                surface.release()
                            }
                        }
                    }
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { image ->
                    // Frozen while a code is being resolved: decoding the same barcode again would only
                    // re-trigger the confirmation and the vibration.
                    if (frozenNow.value) {
                        image.close()
                        return@setAnalyzer
                    }
                    val code = runCatching { scanner.scan(image) }.getOrNull()
                    image.close()
                    if (code != null) {
                        val now = System.currentTimeMillis()
                        // One barcode, one lookup: ignore the same code for two seconds.
                        if (code != lastCode || now - lastAt > 2_000) {
                            lastCode = code
                            lastAt = now
                            textureView.post { onBarcode(code) }
                        }
                    }
                }
                runCatching {
                    provider.unbindAll()
                    val camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                    cameraHolder.value = camera
                    onCamera(camera)
                }
            }, ContextCompat.getMainExecutor(ctx))
            textureView
        },
    )
}

/**
 * Metering points for a `TextureView` we own.
 *
 * `PreviewView.getMeteringPointFactory()` is the usual way, but `PreviewView` laid its surface outside its
 * bounds on the Redmi and painted over the title (measured 2026-11-12), so the preview stays a plain
 * `TextureView` and this factory supplies the mapping: coordinates are given in the view's pixels and passed
 * through unchanged, because the preview fills the view.
 */
private class ViewMeteringPointFactory(width: Float, height: Float) :
    androidx.camera.core.MeteringPointFactory(
        android.util.Rational(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1)),
    ) {
    override fun convertPoint(x: Float, y: Float): android.graphics.PointF =
        android.graphics.PointF(x, y)
}
