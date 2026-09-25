package com.expensetracker.ui.screens.ocr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.expensetracker.services.receipt.ReceiptParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

@Composable
fun OcrScanScreen(
    navController: NavController,
    onScanComplete: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var permissionDeniedPermanently by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        val activity = context as? android.app.Activity
        if (isGranted) {
            permissionGranted = true
            permissionDeniedPermanently = false
        } else {
            permissionGranted = false
            permissionDeniedPermanently = activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        }
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                permissionGranted -> {
                    CameraPreviewWithAnalysis(
                        lifecycleOwner = lifecycleOwner,
                        context = context,
                        onTextDetected = { text ->
                            val parsed = ReceiptParser.parse(text, "INR")
                            onScanComplete(text)
                        }
                    )
                }
                permissionDeniedPermanently -> {
                    PermissionDeniedPermanentUI(
                        onOpenSettings = {
                            val intent = android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        onRequestAgain = {
                            permissionDeniedPermanently = false
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    )
                }
                else -> {
                    PermissionNotGrantedUI(
                        onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionNotGrantedUI(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Camera permission is required to scan receipts.",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Please grant camera access to continue.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text("Grant Camera Permission")
        }
    }
}

@Composable
private fun PermissionDeniedPermanentUI(onOpenSettings: () -> Unit, onRequestAgain: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Camera permission is required to scan receipts.",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Permission was permanently denied. Please enable it from App Settings.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onOpenSettings) {
            Text("Open App Settings")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestAgain) {
            Text("Request Permission Again")
        }
    }
}

@Composable
private fun CameraPreviewWithAnalysis(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    context: Context,
    onTextDetected: (String) -> Unit
) {
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor, MLKitImageAnalyzer { text ->
                            onTextDetected(text)
                        })
                    }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
            } catch (_: Exception) {
                // Camera initialization failed; do not bypass permission
            }
        }, { it.run() })
        onDispose {
            try {
                val provider = cameraProviderFuture.get()
                provider.unbindAll()
            } catch (_: Exception) {
                // Ignore cleanup errors
            }
            executor.shutdown()
        }
    }
}

private class MLKitImageAnalyzer(private val onResult: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                onResult(visionText.text)
                imageProxy.close()
            }
            .addOnFailureListener {
                imageProxy.close()
            }
    }
}
