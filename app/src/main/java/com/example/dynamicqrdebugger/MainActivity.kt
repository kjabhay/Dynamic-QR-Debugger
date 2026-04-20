package com.example.dynamicqrdebugger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.gson.JsonObject
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Response

// ---------------- NETWORK ----------------
data class ValidationRequest(
    val class_id: String,
    val hash: String,
    val timestamp: Long
)

interface ApiService {
    @POST("/qr/validate")
    suspend fun validateQr(@Body request: ValidationRequest): Response<JsonObject>
}

object NetworkClient {
    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://ble-qr-microservice.onrender.com")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

// ---------------- MAIN ACTIVITY ----------------
class MainActivity : ComponentActivity() {

    private var hasPermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                100
            )
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (hasPermission) {
                        QrScannerApp()
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text("Camera permission is required.")
                        }
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            hasPermission = true
        }
    }
}

// ---------------- UI ----------------
@Composable
fun QrScannerApp() {

    val coroutineScope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(false) }
    var scannedQr by remember { mutableStateOf("") }
    var serverMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // 🔥 NEW: Editable Class ID
    var classId by remember { mutableStateOf("LH-1") }

    Box(modifier = Modifier.fillMaxSize()) {

        CameraPreview(
            isScanning = isScanning,
            onQrDetected = { qrContent ->
                isScanning = false
                isLoading = true
                scannedQr = qrContent
                serverMessage = "Sending data..."

                coroutineScope.launch {
                    try {
                        val requestBody = ValidationRequest(
                            class_id = classId,
                            hash = qrContent,
                            timestamp = System.currentTimeMillis()
                        )

                        val response = withContext(Dispatchers.IO) {
                            NetworkClient.api.validateQr(requestBody)
                        }

                        if (response.isSuccessful) {
                            serverMessage = response.body()?.toString() ?: "Empty response"
                        } else {
                            serverMessage = "HTTP ${response.code()}:\n${response.errorBody()?.string()}"
                        }

                    } catch (e: Exception) {
                        serverMessage = "Error:\n${e.localizedMessage}"
                    } finally {
                        isLoading = false
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 🔥 CLASS ID INPUT
            OutlinedTextField(
                value = classId,
                onValueChange = { classId = it },
                label = { Text("Class ID") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            if (scannedQr.isNotEmpty() || serverMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {

                        if (scannedQr.isNotEmpty()) {
                            Text("QR:", color = Color.LightGray)
                            Text(scannedQr, color = Color.Cyan)
                        }

                        if (serverMessage.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Response:", color = Color.LightGray)
                            Text(
                                serverMessage,
                                color = if (serverMessage.contains("Error") || serverMessage.contains("HTTP"))
                                    Color.Red else Color.Green
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    scannedQr = ""
                    serverMessage = ""
                    isScanning = true
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isScanning) "Scanning..."
                    else if (isLoading) "Validating..."
                    else "Scan QR"
                )
            }
        }
    }
}

// ---------------- CAMERA ----------------
@Composable
fun CameraPreview(
    isScanning: Boolean,
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentIsScanning by rememberUpdatedState(isScanning)
    val currentOnQrDetected by rememberUpdatedState(onQrDetected)

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({

                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(
                    ContextCompat.getMainExecutor(ctx),
                    QrCodeAnalyzer { qrText ->
                        if (currentIsScanning) {
                            currentOnQrDetected(qrText)
                        }
                    }
                )

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )

            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

// ---------------- QR ANALYZER ----------------
class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {

        val mediaImage = imageProxy.image

        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let {
                            onQrCodeScanned(it)
                            break
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}