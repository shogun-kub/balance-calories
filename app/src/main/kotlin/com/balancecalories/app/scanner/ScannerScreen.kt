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
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.balancecalories.app.AppContainer
import com.balancecalories.app.R
import com.balancecalories.core.food.Barcode
import com.balancecalories.core.food.Product
import com.balancecalories.data.off.UnusableReason
import com.balancecalories.data.repo.Lookup
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

/** What the screen is doing, so the user sees a reason rather than a silent camera. */
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed ->
        granted = allowed
        deniedForGood = !allowed
    }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun lookup(raw: String) {
        val barcode = Barcode.normalize(raw)
        if (barcode == null) {
            state = ScanState.Invalid(raw)
            return
        }
        if (state is ScanState.Looking) return
        state = ScanState.Looking(barcode)
        scope.launch {
            val result = container.barcodes.resolve(barcode) { container.offClient.lookup(it) }
            state = when (result) {
                is Lookup.Cached -> {
                    onProduct(result.product)
                    ScanState.Scanning
                }

                is Lookup.Fetched -> {
                    onProduct(result.product)
                    ScanState.Scanning
                }

                Lookup.NotFound, Lookup.KnownMiss -> ScanState.NotFound(barcode)
                is Lookup.Offline -> ScanState.Offline(barcode)
                is Lookup.Unusable -> ScanState.Unusable(barcode, result.reason)
                is Lookup.Invalid -> ScanState.Invalid(result.barcode)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    modifier = Modifier.fillMaxSize(),
                )
                // A frame, not a scanning laser: the user aims the whole code at it.
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(90.dp)
                        .border(2.dp, Color.White, RoundedCornerShape(6.dp)),
                )
                Text(
                    text = stringResource(R.string.scan_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { ZxingBarcodeScanner() }
    var lastCode by remember { mutableStateOf<String?>(null) }
    var lastAt by remember { mutableStateOf(0L) }

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
                    onCamera(camera)
                }
            }, ContextCompat.getMainExecutor(ctx))
            textureView
        },
    )
}
