package com.turisla.hellopocket.ui.feature.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomFieldDragGeometryTest {

    @Test
    fun targetCentersStayStableForCardsWithDifferentHeights() {
        val centers = calculateCustomFieldDragTargetCenters(
            itemBounds = listOf(
                CustomFieldDragBounds(top = 0f, bottom = 100f),
                CustomFieldDragBounds(top = 108f, bottom = 268f),
                CustomFieldDragBounds(top = 276f, bottom = 396f),
            ),
            draggedIndex = 1,
        )

        assertEquals(listOf(80f, 188f, 316f), centers)
    }

    @Test
    fun hysteresisPreventsIndexOscillationNearTheSameBoundary() {
        val centers = listOf(50f, 218f, 346f)

        assertEquals(
            0,
            resolveCustomFieldDragTargetIndex(
                targetCenters = centers,
                currentTargetIndex = 0,
                dragCenter = 141f,
                hysteresis = 8f,
            ),
        )
        assertEquals(
            1,
            resolveCustomFieldDragTargetIndex(
                targetCenters = centers,
                currentTargetIndex = 0,
                dragCenter = 143f,
                hysteresis = 8f,
            ),
        )
        assertEquals(
            1,
            resolveCustomFieldDragTargetIndex(
                targetCenters = centers,
                currentTargetIndex = 1,
                dragCenter = 127f,
                hysteresis = 8f,
            ),
        )
        assertEquals(
            0,
            resolveCustomFieldDragTargetIndex(
                targetCenters = centers,
                currentTargetIndex = 1,
                dragCenter = 125f,
                hysteresis = 8f,
            ),
        )
    }

    @Test
    fun invalidDraggedBoundsDoNotStartAReorderGesture() {
        assertTrue(
            calculateCustomFieldDragTargetCenters(
                itemBounds = listOf(CustomFieldDragBounds(top = 10f, bottom = 10f)),
                draggedIndex = 0,
            ).isEmpty()
        )
    }
}
