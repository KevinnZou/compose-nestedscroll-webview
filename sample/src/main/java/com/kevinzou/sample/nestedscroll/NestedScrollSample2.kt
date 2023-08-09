package com.kevinzou.sample.nestedscroll

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Created By Kevin Zou On 2023/8/9
 */
@Stable
class NestedScrollSample2State(coroutineScope: CoroutineScope) {
    val topOffset = Animatable(0f)
    var maxOffset = 0

    internal val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            return if (source == NestedScrollSource.Drag && available.y < 0 && topOffset.value > 0) {
                coroutineScope.launch {
                    val target = (topOffset.value + available.y).coerceAtLeast(0f)
                    topOffset.snapTo(target)
                }
                available
            } else {
                Offset.Zero
            }
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            return if (source == NestedScrollSource.Drag && available.y > 0) {
                val target = (topOffset.value + available.y).coerceAtMost(maxOffset.toFloat())
                coroutineScope.launch {
                    topOffset.snapTo(target)
                }
                available
            } else {
                Offset.Zero
            }
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            return if (topOffset.value > 0) {
                coroutineScope.launch {
                    topOffset.animateTo(0f)
                }
                available
            } else {
                super.onPreFling(available)
            }
        }
    }
}

@Composable
fun NestedScrollSample2() {
    val scope = rememberCoroutineScope()
    val state = remember { NestedScrollSample2State(coroutineScope = scope) }
    val indicatorHeight = 100.dp
    state.maxOffset = with(LocalDensity.current) {
        indicatorHeight.toPx()
    }.toInt()

    Box(
        Modifier
            .fillMaxSize()
            .nestedScroll(state.nestedScrollConnection)
    ) {
        Box(
            Modifier
                .offset {
                    IntOffset(0, state.topOffset.value.toInt())
                }) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Items()
            }
        }
        Box(
            Modifier
                .height(indicatorHeight)
                .fillMaxWidth()
                .offset {
                    IntOffset(0, -indicatorHeight.roundToPx() + state.topOffset.value.toInt())
                }) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 25.dp)
                    .size(50.dp)
            ) {
                CircularProgressIndicator()
            }
        }

    }
}
