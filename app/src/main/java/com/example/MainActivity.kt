package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.AppSettings
import com.example.data.PreferencesManager
import com.example.model.ColorPreset
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentIndigoLight
import com.example.ui.theme.AccentYellow
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesManager = PreferencesManager.getInstance(this)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                ColorScrollApp(preferencesManager = preferencesManager)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check service and permissions when user returns from Settings
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorScrollApp(preferencesManager: PreferencesManager) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val settings by preferencesManager.settingsFlow.collectAsState()
    val isAccessibilityEnabled by AutoScrollAccessibilityService.serviceStateFlow.collectAsState()
    val lastGestureEvent by AutoScrollAccessibilityService.lastGestureEventFlow.collectAsState()
    val isOverlayRunning by FloatingCameraOverlayService.isOverlayRunningFlow.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var liveAnalysisResult by remember {
        mutableStateOf(ColorAnalysisResult())
    }

    var showInstructions by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Color Scroll",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ServiceStatusBadge(
                            isAccessibilityActive = isAccessibilityEnabled || AutoScrollAccessibilityService.isAccessibilitySettingsEnabled(context),
                            hasCamera = hasCameraPermission
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showInstructions = !showInstructions },
                        modifier = Modifier.testTag("help_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
                            contentDescription = "Help Guide"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Instructions Banner (Expandable)
            item {
                AnimatedVisibility(
                    visible = showInstructions,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    InstructionsCard(onClose = { showInstructions = false })
                }
            }

            // 2. Permissions / Setup Health Card
            item {
                SetupStatusCard(
                    context = context,
                    hasCameraPermission = hasCameraPermission,
                    isAccessibilityEnabled = isAccessibilityEnabled || AutoScrollAccessibilityService.isAccessibilitySettingsEnabled(context),
                    hasOverlayPermission = hasOverlayPermission,
                    onRequestCamera = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }

            // 3. Live Front Camera View & Calibration HUD
            item {
                LiveCameraCard(
                    settings = settings,
                    hasCameraPermission = hasCameraPermission,
                    analysisResult = liveAnalysisResult,
                    onAnalysisUpdated = { result ->
                        liveAnalysisResult = result
                    },
                    onToggleDetection = { active ->
                        preferencesManager.updateDetectionActive(active)
                    }
                )
            }

            // 4. Quick Gesture Test Controls
            item {
                GestureTestCard(
                    onTestUp = {
                        val success = AutoScrollAccessibilityService.scrollUp(
                            swipeDistanceRatio = settings.swipeDistanceRatio,
                            durationMs = settings.swipeDurationMs
                        )
                        Toast.makeText(
                            context,
                            if (success) "Dispatched Scroll Up gesture!" else "Accessibility Service not enabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onTestDown = {
                        val success = AutoScrollAccessibilityService.scrollDown(
                            swipeDistanceRatio = settings.swipeDistanceRatio,
                            durationMs = settings.swipeDurationMs
                        )
                        Toast.makeText(
                            context,
                            if (success) "Dispatched Scroll Down gesture!" else "Accessibility Service not enabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    lastGestureEvent = lastGestureEvent
                )
            }

            // 5. Floating Overlay Mode for Reels & TikTok
            item {
                FloatingOverlayCard(
                    context = context,
                    isOverlayRunning = isOverlayRunning,
                    hasOverlayPermission = hasOverlayPermission,
                    hasCameraPermission = hasCameraPermission,
                    onToggle = { enabled ->
                        if (enabled) {
                            if (!hasOverlayPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                                Toast.makeText(
                                    context,
                                    "Please grant 'Display over other apps' to use floating mode",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else if (!hasCameraPermission) {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                FloatingCameraOverlayService.start(context)
                                preferencesManager.updateFloatingOverlay(true)
                            }
                        } else {
                            FloatingCameraOverlayService.stop(context)
                            preferencesManager.updateFloatingOverlay(false)
                        }
                    }
                )
            }

            // 6. Color Trigger Configurations
            item {
                ColorSelectionCard(
                    title = "Up-Scroll Trigger Color",
                    subtitle = "Waves/shows this color to scroll to previous video",
                    selectedPreset = settings.upColorPreset,
                    onSelectPreset = { preset ->
                        preferencesManager.updateUpColor(preset)
                    }
                )
            }

            item {
                ColorSelectionCard(
                    title = "Down-Scroll Trigger Color",
                    subtitle = "Waves/shows this color to scroll to next video (Reels)",
                    selectedPreset = settings.downColorPreset,
                    onSelectPreset = { preset ->
                        preferencesManager.updateDownColor(preset)
                    }
                )
            }

            // 7. Sensitivity, Speed & Cooldown Sliders
            item {
                SettingsSlidersCard(
                    settings = settings,
                    onThresholdChange = { preferencesManager.updateThreshold(it) },
                    onDistanceChange = { preferencesManager.updateDistance(it) },
                    onDurationChange = { preferencesManager.updateDuration(it) },
                    onCooldownChange = { preferencesManager.updateCooldown(it) }
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun ServiceStatusBadge(isAccessibilityActive: Boolean, hasCamera: Boolean) {
    val isReady = isAccessibilityActive && hasCamera
    val text = if (isReady) "READY" else "SETUP NEEDED"
    val bgColor = if (isReady) AccentGreen.copy(alpha = 0.2f) else AccentYellow.copy(alpha = 0.2f)
    val textColor = if (isReady) AccentGreen else AccentYellow

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun InstructionsCard(onClose: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hands-Free Auto Scroll Guide",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.ExpandLess, contentDescription = "Collapse")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "1. Enable Accessibility Service so gestures can be dispatched.\n" +
                        "2. Grant Camera permission for real-time front color detection.\n" +
                        "3. Hold a Yellow item (e.g. sticky note, pen, cup) to scroll UP.\n" +
                        "4. Hold a Green item (e.g. card, phone case) to scroll DOWN (next Reel).\n" +
                        "5. Enable 'Floating Overlay' to scroll while browsing Instagram Reels, TikTok, or YouTube Shorts hands-free!",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun SetupStatusCard(
    context: android.content.Context,
    hasCameraPermission: Boolean,
    isAccessibilityEnabled: Boolean,
    hasOverlayPermission: Boolean,
    onRequestCamera: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Service & Permission Checklist",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 1. Accessibility Service Status
            PermissionRow(
                title = "Accessibility Auto-Scroller",
                description = if (isAccessibilityEnabled) "Service active & ready to dispatch scroll gestures" else "Required to inject swipe gestures in Reels & other apps",
                isGranted = isAccessibilityEnabled,
                actionLabel = "Enable Service",
                testTag = "accessibility_settings_button",
                onAction = {
                    AutoScrollAccessibilityService.openAccessibilitySettings(context)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Camera Permission
            PermissionRow(
                title = "Front Camera Access",
                description = if (hasCameraPermission) "Camera active for live color recognition" else "Required for hands-free color analysis",
                isGranted = hasCameraPermission,
                actionLabel = "Grant Camera",
                testTag = "camera_permission_button",
                onAction = onRequestCamera
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Overlay Permission (for floating widget)
            PermissionRow(
                title = "Display Over Other Apps",
                description = if (hasOverlayPermission) "Overlay active for background use" else "Needed to show floating camera over Instagram Reels/TikTok",
                isGranted = hasOverlayPermission,
                actionLabel = "Allow Overlay",
                testTag = "overlay_permission_button",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                }
            )
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    testTag: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isGranted) AccentGreen else AccentYellow,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!isGranted) {
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag(testTag)
            ) {
                Text(text = actionLabel, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun LiveCameraCard(
    settings: AppSettings,
    hasCameraPermission: Boolean,
    analysisResult: ColorAnalysisResult,
    onAnalysisUpdated: (ColorAnalysisResult) -> Unit,
    onToggleDetection: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    DisposableEffect(hasCameraPermission, settings.isDetectionActive) {
        if (!hasCameraPermission || !settings.isDetectionActive) {
            return@DisposableEffect onDispose {}
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    previewView?.let { pv -> it.surfaceProvider = pv.surfaceProvider }
                }

                val analyzer = ColorAnalyzer(
                    settingsProvider = { settings },
                    onResult = { result ->
                        onAnalysisUpdated(result)
                    }
                )

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, analyzer)
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                // Ignore if unbinding or switching views
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                cameraProvider?.unbindAll()
            } catch (e: Exception) {
                // Safe ignore
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Live Front Camera Analysis",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (settings.isDetectionActive) "Active" else "Paused",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = settings.isDetectionActive,
                        onCheckedChange = onToggleDetection,
                        modifier = Modifier.testTag("toggle_detection_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Camera preview container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also {
                                previewView = it
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Trigger banner overlay
                    if (analysisResult.triggeredAction != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xDD000000))
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            val actionName = when (analysisResult.triggeredAction) {
                                ScrollAction.SCROLL_UP -> "⬆️ SCROLL UP TRIGGERED"
                                ScrollAction.SCROLL_DOWN -> "⬇️ SCROLL DOWN TRIGGERED"
                                null -> ""
                            }
                            Text(
                                text = actionName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    // Cooldown notification in top corner
                    if (analysisResult.isCooldownActive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xCC334155))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Cooldown: ${(analysisResult.remainingCooldownMs / 1000f).let { String.format("%.1fs", it) }}",
                                color = AccentYellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Camera permission required for live analysis",
                            color = Color.White,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Realtime Color Match Telemetry
            Text(
                text = "Real-Time Target Color Percentage",
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 1. Up-Trigger Color Meter
            ColorMeterRow(
                label = "Up (${settings.upColorPreset.displayName})",
                currentPercent = analysisResult.upPercentage,
                thresholdPercent = settings.thresholdPercent,
                color = Color(settings.upColorPreset.colorHex)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Down-Trigger Color Meter
            ColorMeterRow(
                label = "Down (${settings.downColorPreset.displayName})",
                currentPercent = analysisResult.downPercentage,
                thresholdPercent = settings.thresholdPercent,
                color = Color(settings.downColorPreset.colorHex)
            )
        }
    }
}

@Composable
fun ColorMeterRow(
    label: String,
    currentPercent: Float,
    thresholdPercent: Float,
    color: Color
) {
    val progress = (currentPercent / 20.0f).coerceIn(0f, 1f)
    val isTriggering = currentPercent >= thresholdPercent

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "${String.format("%.1f%%", currentPercent)} / ${String.format("%.0f%%", thresholdPercent)} target",
                fontSize = 12.sp,
                fontWeight = if (isTriggering) FontWeight.Bold else FontWeight.Normal,
                color = if (isTriggering) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (isTriggering) AccentGreen else color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun GestureTestCard(
    onTestUp: () -> Unit,
    onTestDown: () -> Unit,
    lastGestureEvent: AutoScrollAccessibilityService.GestureEvent?
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Manual Gesture Testing",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                text = "Test accessibility swipe injection directly without waiting for camera",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onTestUp,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_scroll_up_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = "⬆️ Test Scroll Up",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = onTestDown,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_scroll_down_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = "⬇️ Test Scroll Down",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (lastGestureEvent != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Last injected: ${lastGestureEvent.action}",
                        fontSize = 12.sp,
                        color = AccentGreen
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingOverlayCard(
    context: android.content.Context,
    isOverlayRunning: Boolean,
    hasOverlayPermission: Boolean,
    hasCameraPermission: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = AccentCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Floating Camera Overlay",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Enables scrolling inside Instagram Reels & TikTok",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isOverlayRunning,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("toggle_floating_overlay_switch")
                )
            }
        }
    }
}

@Composable
fun ColorSelectionCard(
    title: String,
    subtitle: String,
    selectedPreset: ColorPreset,
    onSelectPreset: (ColorPreset) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(ColorPreset.entries.toTypedArray()) { preset ->
                    val isSelected = preset == selectedPreset
                    val chipBorder = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(2.dp, chipBorder, RoundedCornerShape(10.dp))
                            .clickable { onSelectPreset(preset) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(preset.colorHex))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = preset.displayName,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSlidersCard(
    settings: AppSettings,
    onThresholdChange: (Float) -> Unit,
    onDistanceChange: (Float) -> Unit,
    onDurationChange: (Long) -> Unit,
    onCooldownChange: (Float) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Threshold & Gesture Sensitivity",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            // 1. Threshold Percentage Slider (1% to 20%, default 5%)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Threshold Percentage",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${String.format("%.1f", settings.thresholdPercent)}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = settings.thresholdPercent,
                onValueChange = onThresholdChange,
                valueRange = 1.0f..20.0f,
                steps = 19,
                modifier = Modifier.testTag("threshold_slider")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Gesture Swipe Distance (20% to 80% screen height)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Scroll Swipe Distance",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${(settings.swipeDistanceRatio * 100).toInt()}% screen",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = settings.swipeDistanceRatio,
                onValueChange = onDistanceChange,
                valueRange = 0.20f..0.80f,
                modifier = Modifier.testTag("distance_slider")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Gesture Duration / Speed (150ms to 600ms)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Swipe Duration / Speed",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${settings.swipeDurationMs} ms",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = settings.swipeDurationMs.toFloat(),
                onValueChange = { onDurationChange(it.toLong()) },
                valueRange = 150f..600f,
                modifier = Modifier.testTag("duration_slider")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Cooldown / Debounce (1.0s to 3.0s)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Trigger Cooldown / Debounce",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${String.format("%.1f", settings.cooldownSeconds)} s",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = settings.cooldownSeconds,
                onValueChange = onCooldownChange,
                valueRange = 1.0f..3.0f,
                steps = 20,
                modifier = Modifier.testTag("cooldown_slider")
            )
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
