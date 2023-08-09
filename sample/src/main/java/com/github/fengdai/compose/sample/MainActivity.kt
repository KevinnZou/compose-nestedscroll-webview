package com.github.fengdai.compose.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollSource.Companion.Fling
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.node.Ref
import androidx.compose.ui.unit.*
import androidx.compose.ui.unit.Constraints.Companion.Infinity
import androidx.core.view.ViewCompat
import com.google.accompanist.web.WebView
import com.google.accompanist.web.rememberWebViewState
import com.kevinzou.sample.nestedscroll.NestedScrollSample
import com.kevinzou.sample.nestedscroll.NestedScrollSample2
import com.telefonica.nestedscrollwebview.NestedScrollWebView
import java.lang.Integer.max
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NestedScrollSample2()
//            Commodity(
//                topContent = {
//                    Box(
//                        modifier = Modifier
//                            .height(1000.dp)
//                            .fillMaxWidth()
//                            .background(Brush.verticalGradient(listOf(Color.Black, Color.White)))
//                    )
//                }
//            )
        }
    }
}

@Composable
fun rememberCommodityState(): CommodityState {
    return remember { CommodityState() }
}

@Stable
class CommodityState {
    var scrollY by mutableStateOf(0)
        private set
    var maxScrollY: Int
        get() = _maxScrollYState.value
        internal set(newMax) {
            _maxScrollYState.value = newMax
            if (scrollY > newMax) {
                scrollY = newMax
            }
        }
    private var _maxScrollYState = mutableStateOf(Int.MAX_VALUE)
    private var accumulator: Float = 0f

    /**
     * 在InnerConnection中，它会拦截WebView的划动事件并通过dispatchRawDelta最终调用到这里到onDelta方法
     * 在手指上划时，如果顶部内容已经完全不可见，则不管划动距离是多少，absolute都会大于maxScrollY，从而导致consumed为0
     * 这就意味着这种情况下我们并不去拦截WebView的滚动，让其自行处理
     */
    internal val scrollableState = ScrollableState {
        val absolute = (scrollY + it + accumulator)
        val newValue = absolute.coerceIn(0f, maxScrollY.toFloat())
        val changed = absolute != newValue
        val consumed = newValue - scrollY
        val consumedInt = consumed.roundToInt()
        scrollY += consumedInt
        accumulator = consumed - consumedInt

        // Avoid floating-point rounding error
        if (changed) consumed else it
    }

    internal lateinit var flingBehavior: FlingBehavior

    internal val nestedScrollConnection = object : NestedScrollConnection {
        /**
         * 主要用于在顶部内容完全不显示时，在WebView下拉，漏出顶部内容，不松手接着上划
         * 这个时候顶部内容并不会划动回去，而是WebView自己消耗了，这样显然时不行的
         * 所以我们在这里拦截WebView滚动，在向上滚动时去判断顶部内容是否可以滚动，若可以则先滚动顶部内容
         */
        override fun onPreScroll(
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            return if (source != Fling && (available.y < 0 || scrollY < maxScrollY)) {
                consume(available)
            } else super.onPreScroll(available, source)
        }

        /**
         * WebView消耗完后，如果还有内容，则交给scrollable滚动，即整体上划
         */
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            return if (source != Fling && available.y > 0) consume(available)
            else super.onPostScroll(consumed, available, source)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (available.y > 0) {
                var remain = available.y
                scrollableState.scroll {
                    with(this) {
                        with(flingBehavior) {
                            remain = -performFling(-available.y)
                        }
                    }
                }
                return Velocity.Zero.copy(y = available.y - remain)
            }
            return Velocity.Zero
        }
    }

    private fun consume(available: Offset): Offset {
        val consumedY = -scrollableState.dispatchRawDelta(-available.y)
        return available.copy(y = consumedY)
    }

    val canScrollForward by derivedStateOf { scrollY < maxScrollY }

    suspend fun animateScrollTo(
        scrollY: Float,
        animationSpec: AnimationSpec<Float> = SpringSpec()
    ) {
        scrollableState.animateScrollBy(scrollY - this.scrollY, animationSpec)
    }

    suspend fun scrollTo(scrollY: Float): Float = scrollableState.scrollBy(scrollY - this.scrollY)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Commodity(
    state: CommodityState = rememberCommodityState(),
    topContent: @Composable () -> Unit
) {
    val webViewRef = remember { Ref<NestedScrollWebView>() }
    val flingBehavior = ScrollableDefaults.flingBehavior()
    SideEffect { state.flingBehavior = flingBehavior }

    val scrollableInteractionSource = remember { MutableInteractionSource() }
    val isDragged by scrollableInteractionSource.collectIsDraggedAsState()
    fun isScrollableEnabled() = state.canScrollForward || isDragged
    val scope = rememberCoroutineScope()
    val outerNestedScrollConnection = remember {
        /**
         * 这个方法感觉没啥大用，主要应对的情况是顶部内容仍然可见，且用户向下划动想要显示更多顶部内容
         * 这时会去看WebView的内容是否已经内部滚动了一段距离，若有则先将WebView归位
         * 剩下的滚动内容会交给它的child，也就是scrollable处理
         * 但是在实际操作中，顶部内容可见的情况下，WebView肯定是没有滚动的，所以它的scrollY一直为0
         * 这就导致即使走到了划动逻辑，也不会有任何滚动，所以这个方法实际上没啥用
         * 测试后发现一种应用场景，也正是这个connection存在的意义：
         * 用户在顶部内容可见时，一次性划动到顶部内容消失，这时如果没有下面onPostScroll的处理，会卡在WebView顶部
         * 处理后能将剩余滚动传递到WebView内部，但是这时如果再往回划动，如果这个方法不做处理，会直接划动出现顶部内容
         * 但是之前部分划动的WebView并不会复原，所以这里就需要先拦截向下滚动事件，将WebView归位，然后再将剩余滚动传递给子容器
         */
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (isScrollableEnabled()) {
                    if (available.y > 0) {
                        webViewRef.value?.run {
                            val consumedY = available.y
                                .coerceIn(0f, scrollY.toFloat())
                            scrollBy(0, -consumedY.roundToInt())
                            return available.copy(y = consumedY)
                        }
                    }
                }
                return super.onPreScroll(available, source)
            }

            /**
             * 这个方法的作用是在它的child，也就是scrollable滚动后，再将剩下的滚动量交给WebView处理
             * 它的触发时机是顶部内容仍然可见，且用户向上划动想要显示更多WebView内容
             * 因为是PostScroll,所以会在child滚动后才触发，等于是当整体列表划动后还有剩余距离时才会去向下划动WebView
             * 测试后发现其使用场景为：在顶部内容仍然存在，然后一次划动距离超过顶部内容时，做到无缝衔接。
             * 如果没有它，表现会是先将顶部内容滚完，然后就卡住，必须要再次划动才能触发WebView内部滚动
             */
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (isScrollableEnabled()) {
                    if (available.y < 0) {
                        webViewRef.value?.run {
                            val consumedY = available.y
                                .coerceIn(-(getMaxScrollY() - scrollY).toFloat(), 0f)
                            scrollBy(0, -consumedY.roundToInt())
                            return available.copy(y = consumedY)
                        }
                    }
                }
                return super.onPostScroll(consumed, available, source)
            }
        }
    }
    Layout(
        content = {
            topContent()
            WebView(
                state = rememberWebViewState(url = "https://www.google.com.hk/search?q=nestedscroll+compose"),
                onCreated = {
                    webViewRef.value = it as NestedScrollWebView
                    ViewCompat.setNestedScrollingEnabled(it, true)
                },
                onDispose = {
                    webViewRef.value = null
                },
                factory = ::NestedScrollWebView,
                modifier = Modifier.alpha(0.99f)
            )
        },
        modifier = Modifier
            .fillMaxHeight()
            .nestedScroll(outerNestedScrollConnection)
            .scrollable(
                state.scrollableState,
                Orientation.Vertical,
                enabled = isScrollableEnabled(),
                reverseDirection = true,
                flingBehavior = remember {
                    object : FlingBehavior {
                        override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                            val remain = with(this) {
                                with(flingBehavior) {
                                    performFling(initialVelocity)
                                }
                            }
                            if (remain > 0) {
                                webViewRef.value?.fling(remain.roundToInt())
                                return 0f
                            }
                            Log.d("Dai", "performFling: $initialVelocity -> $remain")
                            return remain
                        }
                    }
                },
                interactionSource = scrollableInteractionSource,
            )
            .nestedScroll(state.nestedScrollConnection)
    ) { measurables, constraints ->
        check(constraints.hasBoundedHeight)
        val height = constraints.maxHeight
        val topContentPlaceable =
            measurables[0].measure(constraints.copy(minHeight = 0, maxHeight = Infinity))
        val bottomContentPlaceable =
            measurables[1].measure(constraints.copy(minHeight = height, maxHeight = height))
        val width =
            constraints.constrainWidth(max(topContentPlaceable.width, bottomContentPlaceable.width))
        state.maxScrollY = topContentPlaceable.height
        layout(
            width = width,
            height = height
        ) {
            topContentPlaceable.placeRelativeWithLayer(0, -state.scrollY)
            bottomContentPlaceable.placeRelativeWithLayer(
                0,
                topContentPlaceable.height - state.scrollY
            )
        }
    }
}
