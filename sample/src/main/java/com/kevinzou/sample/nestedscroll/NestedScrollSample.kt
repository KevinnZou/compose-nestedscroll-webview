package com.kevinzou.sample.nestedscroll

import android.util.Log
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints.Companion.Infinity
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp

/**
 * Created By Kevin Zou On 2023/7/31
 */
@Stable
class NestedScrollSampleState {
    var scrollY by mutableStateOf(0)
    var maxScrollY by mutableStateOf(300)
    private var accumulator: Float = 0f

    internal val scrollableState = ScrollableState { delta ->
        val absolute = scrollY + accumulator + delta
        val newValue = absolute.coerceIn(0f, maxScrollY.toFloat())
        val changed = newValue != absolute
        val consumed = newValue - scrollY
        val consumedInt = consumed.toInt()
        Log.d(
            "KZ",
            "Scroll onDelta: $delta, $accumulator, $scrollY, $maxScrollY, $absolute, $consumed, $consumedInt"
        )
        scrollY += consumedInt
        accumulator = consumed.minus(consumedInt)

        if (changed) consumed else delta
    }

    internal val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            Log.d("KZ", "onPreScroll: $available")
            return if (available.y < 0 && scrollY < maxScrollY) {
                consume(available)
            } else {
                super.onPreScroll(available, source)
            }
        }
    }

    private fun consume(available: Offset): Offset {
        val consumedY = -scrollableState.dispatchRawDelta(-available.y)
        return available.copy(y = consumedY)
    }
}

@Composable
fun NestedScrollSample() {
    val nestedScrollSampleState = remember {
        NestedScrollSampleState()
    }

    with(LocalDensity.current) {
        nestedScrollSampleState.maxScrollY = 300.dp.toPx().toInt()
    }

    Layout(
        content = {
            Text("Header",
                Modifier
                    .height(300.dp)
                    .wrapContentWidth())
            Column(
                Modifier
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Items()
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .scrollable(
                nestedScrollSampleState.scrollableState,
                orientation = Orientation.Vertical,
                enabled = true,
                reverseDirection = true
            )
            .nestedScroll(nestedScrollSampleState.nestedScrollConnection),
    ) { measurables, constraints ->
        val height = constraints.maxHeight
        Log.d("KZ", "NestedScrollSample: $constraints")
        val topPlaceable =
            measurables[0].measure(constraints.copy(minHeight = 0, maxHeight = Infinity))
        val bottomPlaceable =
            measurables[1].measure(constraints.copy(minHeight = height, maxHeight = height))
        val width = constraints.constrainWidth(maxOf(topPlaceable.width, bottomPlaceable.width))
        nestedScrollSampleState.maxScrollY = topPlaceable.height
        val topX = (constraints.maxWidth - topPlaceable.width) / 2
        layout(width, height) {
            topPlaceable.placeWithLayer(topX, -nestedScrollSampleState.scrollY)
            bottomPlaceable.placeWithLayer(0, topPlaceable.height - nestedScrollSampleState.scrollY)
        }
    }
}

@Composable
fun Items() {
    (1..25).forEach {
        Card(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth(0.8f)
                .height(40.dp)

        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Item $it")
            }

        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewScrollTest() {
    NestedScrollSample()
}