package com.koala.cartes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ScanResult(val value: String, val format: String, val storeId: String?)

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
}

/** Analyse chaque image de la caméra : code-barres + nom du magasin écrit sur la carte. */
private class CardAnalyzer(private val onFound: (String, String, String?) -> Unit) : ImageAnalysis.Analyzer {
    private val barcodes = BarcodeScanning.getClient()
    private val texts = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val votes = mutableMapOf<String, Int>()
    @Volatile private var done = false
    private var frame = 0

    private fun vote(text: String) {
        Stores.detect(text)?.let { votes[it] = (votes[it] ?: 0) + 1 }
    }

    private fun best(): String? = votes.maxByOrNull { it.value }?.key

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null || done) { proxy.close(); return }
        val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        frame++
        val readText = frame % 3 == 1
        barcodes.process(img).addOnCompleteListener { t ->
            val code = if (t.isSuccessful) t.result.firstOrNull { !it.rawValue.isNullOrBlank() } else null
            when {
                code != null && !done -> {
                    done = true
                    texts.process(img).addOnCompleteListener { tt ->
                        if (tt.isSuccessful) vote(tt.result.text)
                        proxy.close()
                        onFound(code.rawValue!!.trim(), Codes.fromMlKit(code.format), best())
                    }
                }
                readText && !done -> texts.process(img).addOnCompleteListener { tt ->
                    if (tt.isSuccessful) vote(tt.result.text)
                    proxy.close()
                }
                else -> proxy.close()
            }
        }
    }

    fun close() {
        barcodes.close(); texts.close()
    }
}

suspend fun scanImage(ctx: Context, uri: Uri): ScanResult? {
    val img = withContext(Dispatchers.IO) { InputImage.fromFilePath(ctx, uri) }
    val bs = BarcodeScanning.getClient()
    val ts = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        val code = bs.process(img).awaitTask().firstOrNull { !it.rawValue.isNullOrBlank() } ?: return null
        val text = runCatching { ts.process(img).awaitTask().text }.getOrDefault("")
        return ScanResult(code.rawValue!!.trim(), Codes.fromMlKit(code.format), Stores.detect(text))
    } finally {
        bs.close(); ts.close()
    }
}

@Composable
fun ScannerScreen(onResult: (ScanResult) -> Unit, onManual: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(Manifest.permission.CAMERA) }

    val currentOnResult by rememberUpdatedState(onResult)
    val delivered = remember { AtomicBoolean(false) }
    fun deliver(r: ScanResult) {
        if (delivered.compareAndSet(false, true)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnResult(r)
        }
    }

    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val r = runCatching { scanImage(ctx, uri) }.getOrNull()
                busy = false
                if (r != null) deliver(r)
                else Toast.makeText(ctx, "Aucun code-barres trouvé sur cette image", Toast.LENGTH_LONG).show()
            }
        }
    }

    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose { }
        val executor = Executors.newSingleThreadExecutor()
        val analyzer = CardAnalyzer { v, f, s -> deliver(ScanResult(v, f, s)) }
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(
                        ResolutionSelector.Builder().setResolutionStrategy(
                            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                        ).build()
                    )
                    .build().also { it.setAnalyzer(executor, analyzer) }
                p.unbindAll()
                camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                Toast.makeText(ctx, "Caméra indisponible", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            disposed = true
            provider?.unbindAll()
            camera = null
            analyzer.close()
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.Center).fillMaxWidth(0.84f).aspectRatio(1.586f)
                    .border(3.dp, Yellow, RoundedCornerShape(18.dp))
            )
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "L'appareil photo sert à lire le code-barres. Tu peux aussi importer une photo ou saisir le numéro.",
                    color = Color.White, textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Navy),
                ) { Text("Autoriser l'appareil photo") }
            }
        }

        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().background(Color(0x99000000)).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Fermer", tint = Color.White) }
                Text("Scanner une carte", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                if (granted && camera?.cameraInfo?.hasFlashUnit() == true) {
                    TextButton(onClick = {
                        torch = !torch
                        camera?.cameraControl?.enableTorch(torch)
                    }) { Text(if (torch) "Lampe ON" else "Lampe", color = if (torch) Yellow else Color.White) }
                }
            }
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.fillMaxWidth().background(Color(0x99000000)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (granted) Text("Place le code-barres dans le cadre", color = Color.White, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Depuis une photo", color = Color.White) }
                    OutlinedButton(onClick = onManual) { Text("Saisir à la main", color = Color.White) }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Yellow)
    }
}
