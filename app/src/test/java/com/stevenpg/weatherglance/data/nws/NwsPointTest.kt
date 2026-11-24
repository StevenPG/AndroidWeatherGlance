package com.stevenpg.weatherglance.data.nws

import org.junit.Test
import kotlin.test.assertEquals

class NwsPointTest {
    @Test
    fun `coordinates are rounded to four decimals without trailing zeros`() {
        assertEquals("39.7817,-89.6502", NwsPoint.format(39.781749, -89.650151))
        assertEquals("40,-105.1", NwsPoint.format(40.0, -105.1))
    }
}
