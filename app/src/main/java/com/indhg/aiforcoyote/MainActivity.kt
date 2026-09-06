package com.indhg.aiforcoyote

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.indhg.aiforcoyote.ui.ChatScreen
import com.indhg.aiforcoyote.ui.SettingsScreen
import com.indhg.aiforcoyote.ui.onboarding.Guide1Steps
import com.indhg.aiforcoyote.ui.onboarding.LocalTourTargets
import com.indhg.aiforcoyote.ui.onboarding.OnboardingOverlay
import com.indhg.aiforcoyote.ui.onboarding.TourPrefs
import com.indhg.aiforcoyote.ui.onboarding.TourScreen
import com.indhg.aiforcoyote.ui.onboarding.TourTargets
import com.indhg.aiforcoyote.ui.theme.CoyoteTheme
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* 结果由观察器按权限自检降级 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 观察期间屏幕常亮（任务书要求：本 App 前台常驻）
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 摄像头 + 麦克风权限（拒绝则对应观察自动禁用，仅聊天不崩溃）
        permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        // 分享/打开入口：别的应用把 zip/md 甩过来直接导入
        handleImportIntent(intent)
        // 前台开观察、退后台暂停
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.startObservation(this)
                Lifecycle.Event.ON_STOP -> vm.stopObservation()
                else -> {}
            }
        })
        setContent {
            CoyoteTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current
                    var showSettings by remember { mutableStateOf(false) }
                    var tourActive by remember { mutableStateOf(false) }
                    var tourStepIndex by remember { mutableIntStateOf(0) }
                    val tourTargets = remember { TourTargets() }
                    val steps = Guide1Steps

                    fun finishTour() {
                        TourPrefs.markDone(context)
                        tourActive = false
                        tourStepIndex = 0
                    }

                    fun startTour() {
                        TourPrefs.clear(context)
                        tourStepIndex = 0
                        tourActive = true
                    }

                    // 首次启动：prefs 无 tour_done_guide1 → ~800ms 后开引导
                    LaunchedEffect(Unit) {
                        delay(800)
                        if (!TourPrefs.isDone(context)) {
                            tourStepIndex = 0
                            tourActive = true
                        }
                    }

                    // 按步骤切换 Chat / Settings；目标缺失则跳过该步
                    LaunchedEffect(tourActive, tourStepIndex) {
                        if (!tourActive) return@LaunchedEffect
                        val step = steps.getOrNull(tourStepIndex) ?: run {
                            finishTour()
                            return@LaunchedEffect
                        }
                        showSettings = step.screen == TourScreen.Settings
                        delay(120)
                        val tid = step.targetId
                        if (tid != null) {
                            tourTargets.bringIntoView(tid)
                            var found = false
                            repeat(16) {
                                val r = tourTargets.get(tid)
                                if (r != null && r.width > 1f && r.height > 1f) {
                                    found = true
                                    return@repeat
                                }
                                delay(50)
                            }
                            if (!found) {
                                // 目标仍缺失：跳过本步
                                if (tourStepIndex >= steps.lastIndex) {
                                    finishTour()
                                } else {
                                    tourStepIndex += 1
                                }
                            }
                        }
                    }

                    CompositionLocalProvider(LocalTourTargets provides tourTargets) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (showSettings) {
                                SettingsScreen(
                                    vm = vm,
                                    onBack = {
                                        if (!tourActive) showSettings = false
                                    },
                                    onReplayTour = { startTour() },
                                )
                            } else {
                                ChatScreen(
                                    vm = vm,
                                    onOpenSettings = {
                                        if (!tourActive) showSettings = true
                                    },
                                )
                            }
                            if (tourActive) {
                                OnboardingOverlay(
                                    stepIndex = tourStepIndex,
                                    steps = steps,
                                    targets = tourTargets,
                                    onSkip = { finishTour() },
                                    onBack = {
                                        if (tourStepIndex > 0) tourStepIndex -= 1
                                    },
                                    onNext = {
                                        if (tourStepIndex >= steps.lastIndex) {
                                            finishTour()
                                        } else {
                                            tourStepIndex += 1
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleImportIntent(intent)
    }

    /** 处理 SEND/VIEW 分享意图：取出流导入 DLC，随后重置意图防止重复导入。 */
    private fun handleImportIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_VIEW) return
        val uri: Uri? = when (action) {
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> intent.data
        }
        if (uri != null) vm.importDlc(uri)
        setIntent(Intent(this, MainActivity::class.java)) // 清掉分享意图，避免切后台回来重复导入
    }
}
