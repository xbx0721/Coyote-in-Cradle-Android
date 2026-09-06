@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.indhg.aiforcoyote.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indhg.aiforcoyote.R
import com.indhg.aiforcoyote.ui.theme.Gold
import com.indhg.aiforcoyote.ui.theme.Ink
import com.indhg.aiforcoyote.ui.theme.Muted
import com.indhg.aiforcoyote.ui.theme.TextMain
import kotlinx.coroutines.delay

enum class TourScreen { Chat, Settings }

data class TourStep(
    val id: String,
    val titleRes: Int,
    val bodyRes: Int,
    val targetId: String?,
    val screen: TourScreen,
)

/** guide1 步骤表：Chat ↔ Settings 由 MainActivity 按 screen 切换。 */
val Guide1Steps: List<TourStep> = listOf(
    TourStep("settings_btn", R.string.tour_step_settings_title, R.string.tour_step_settings_body, "settings_btn", TourScreen.Chat),
    TourStep("api_key", R.string.tour_step_api_key_title, R.string.tour_step_api_key_body, "api_key", TourScreen.Settings),
    TourStep("base_url", R.string.tour_step_base_url_title, R.string.tour_step_base_url_body, "base_url", TourScreen.Settings),
    TourStep("model", R.string.tour_step_model_title, R.string.tour_step_model_body, "model", TourScreen.Settings),
    TourStep("test_btn", R.string.tour_step_test_title, R.string.tour_step_test_body, "test_btn", TourScreen.Settings),
    TourStep("save_btn", R.string.tour_step_save_title, R.string.tour_step_save_body, "save_btn", TourScreen.Settings),
    TourStep("connect_coyote", R.string.tour_step_connect_title, R.string.tour_step_connect_body, "connect_coyote", TourScreen.Settings),
    TourStep("autopilot", R.string.tour_step_autopilot_title, R.string.tour_step_autopilot_body, "autopilot", TourScreen.Chat),
    TourStep("safety", R.string.tour_step_safety_title, R.string.tour_step_safety_body, "autopilot", TourScreen.Chat),
    TourStep("done", R.string.tour_step_done_title, R.string.tour_step_done_body, "tour_replay", TourScreen.Settings),
)

/** 目标边界注册表：各 composable 用 tourTarget 上报 boundsInRoot。 */
class TourTargets {
    private val bounds = mutableStateMapOf<String, Rect>()
    private val bringers = mutableMapOf<String, BringIntoViewRequester>()

    fun update(id: String, rect: Rect) {
        bounds[id] = rect
    }

    fun get(id: String): Rect? = bounds[id]

    fun registerBringer(id: String, requester: BringIntoViewRequester) {
        bringers[id] = requester
    }

    suspend fun bringIntoView(id: String) {
        bringers[id]?.bringIntoView()
    }
}

val LocalTourTargets = staticCompositionLocalOf { TourTargets() }

/** 把 composable 注册为引导高亮目标。 */
fun Modifier.tourTarget(id: String): Modifier = composed {
    val targets = LocalTourTargets.current
    val bringer = remember { BringIntoViewRequester() }
    LaunchedEffect(id) {
        targets.registerBringer(id, bringer)
    }
    this
        .bringIntoViewRequester(bringer)
        .onGloballyPositioned { coords ->
            targets.update(id, coords.boundsInRoot())
        }
}

@Composable
fun OnboardingOverlay(
    stepIndex: Int,
    steps: List<TourStep> = Guide1Steps,
    targets: TourTargets,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val step = steps.getOrNull(stepIndex) ?: return
    val total = steps.size
    val pad = with(LocalDensity.current) { 8.dp.toPx() }

    // Live bounds every composition: TourTargets.mutableStateMapOf notifies on scroll updates.
    val rect = step.targetId?.let { targets.get(it) }
    val hole = rect?.takeIf { it.width > 1f && it.height > 1f }?.inflate(pad)

    LaunchedEffect(stepIndex, step.targetId) {
        val tid = step.targetId ?: return@LaunchedEffect
        delay(50)
        targets.bringIntoView(tid)
        // Brief wait for layout after bringIntoView; hole tracks live via composition.
        delay(100)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dim = Color(0xCC0A0A0A)
            val h = hole
            if (h == null) {
                drawRect(dim)
            } else {
                val path = Path().apply {
                    addRect(Rect(Offset.Zero, size))
                    addRoundRect(
                        RoundRect(
                            left = h.left,
                            top = h.top,
                            right = h.right,
                            bottom = h.bottom,
                            cornerRadius = CornerRadius(16f, 16f),
                        ),
                    )
                }
                clipPath(path, clipOp = ClipOp.Difference) {
                    drawRect(dim)
                }
                drawRoundRect(
                    color = Gold.copy(alpha = 0.95f),
                    topLeft = Offset(h.left, h.top),
                    size = Size(h.width, h.height),
                    cornerRadius = CornerRadius(16f, 16f),
                    style = Stroke(width = 3.dp.toPx()),
                )
                drawRoundRect(
                    color = Gold.copy(alpha = 0.18f),
                    topLeft = Offset(h.left, h.top),
                    size = Size(h.width, h.height),
                    cornerRadius = CornerRadius(16f, 16f),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.tour_step_index, stepIndex + 1, total),
                fontSize = 11.sp,
                color = Muted,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(step.titleRes),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Gold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(step.bodyRes),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = TextMain,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.tour_skip), color = Muted, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                if (stepIndex > 0) {
                    OutlinedButton(onClick = onBack) {
                        Text(stringResource(R.string.tour_back), color = Muted, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                val isLast = stepIndex >= total - 1
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
                ) {
                    Text(
                        stringResource(if (isLast) R.string.tour_done else R.string.tour_next),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

private fun Rect.inflate(pad: Float): Rect =
    Rect(left - pad, top - pad, right + pad, bottom + pad)
