package com.turisla.hellopocket.ui.feature.passwordGenerator

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.turisla.hellopocket.model.GeneratorSegment
import com.turisla.hellopocket.model.GeneratorStep
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class GeneratorDragStateTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun movingFirstVisibleRuleDoesNotScrollOrKeepMovingWithoutFingerMotion() {
        var steps by mutableStateOf(List(40) {
            GeneratorStep("step-$it", "Rule $it", GeneratorSegment.Digits(1))
        })
        val list = LazyListState(firstVisibleItemIndex = 10)
        val drag = GeneratorDragState(list)
        val move = { id: String, index: Int ->
            steps = steps.toMutableList().apply { add(index, removeAt(indexOfFirst { it.id == id })) }
        }
        compose.setContent {
            LazyColumn(state = list, modifier = Modifier.size(320.dp, 300.dp)) {
                items(steps, key = { it.id }) { Box(Modifier.height(80.dp)) }
            }
        }
        compose.runOnIdle {
            drag.start("step-10")
            val source = list.layoutInfo.visibleItemsInfo.first { it.key == "step-10" }
            val target = list.layoutInfo.visibleItemsInfo.first { it.key == "step-11" }
            drag.drag((target.offset - source.offset).toFloat())
            drag.reorder(steps, move)
        }
        // 让真正的 LazyColumn 完成重排；随后没有手指位移，顺序与视口都应保持稳定。
        repeat(3) { compose.runOnIdle { drag.reorder(steps, move) } }
        compose.runOnIdle {
            assertEquals(10, list.firstVisibleItemIndex)
            assertEquals("step-11", steps[10].id)
            assertEquals("step-10", steps[11].id)
            assertEquals("step-12", steps[12].id)
            drag.stop()
        }
    }
}
