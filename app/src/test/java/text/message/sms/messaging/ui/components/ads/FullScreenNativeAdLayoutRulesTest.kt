package text.message.sms.messaging.ui.components.ads

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class FullScreenNativeAdLayoutRulesTest {

    @Test
    fun tallArea_capsMediaAtSquare() =
        assertEquals(DpSize(320.dp, 320.dp), fullScreenNativeMediaSize(320.dp, 600.dp))

    @Test
    fun areaBetweenSquareAndWide_fillsIt() =
        assertEquals(DpSize(320.dp, 250.dp), fullScreenNativeMediaSize(320.dp, 250.dp))

    @Test
    fun shortArea_keepsSixteenByNineAndNarrows() {
        val size = fullScreenNativeMediaSize(560.dp, 90.dp)
        assertEquals(160f, size.width.value, 0.01f)
        assertEquals(90f, size.height.value, 0.01f)
    }

    @Test
    fun body_shrinksThenHidesOnShortScreens() {
        assertEquals(3, fullScreenNativeBodyMaxLines(700.dp))
        assertEquals(2, fullScreenNativeBodyMaxLines(500.dp))
        assertEquals(0, fullScreenNativeBodyMaxLines(300.dp))
    }
}
