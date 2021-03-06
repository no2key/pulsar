package `fun`.platonic.pulsar.dom.select

import `fun`.platonic.pulsar.dom.Documents
import `fun`.platonic.pulsar.dom.HEIGHT
import `fun`.platonic.pulsar.dom.WIDTH
import `fun`.platonic.pulsar.dom.nodes.FeaturedDocument
import `fun`.platonic.pulsar.dom.nodes.FeaturedDocument.Companion.SELECTOR_IN_BOX_DEVIATION
import `fun`.platonic.pulsar.dom.nodes.getFeature
import `fun`.platonic.pulsar.dom.nodes.select2
import `fun`.platonic.pulsar.dom.nodes.uniqueName
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal data class Box(val width: Int, val height: Int) {
    override fun toString(): String {
        return "${width}x${height}"
    }
}

class TestExtendedCss {

    private val resourceName = "/webpages/mia.com/00f3a63c4898d201df95d6015244dd63.html"
    private val baseUri = "jar:/$resourceName"
    private val stream = javaClass.getResourceAsStream(resourceName)
    private val doc: FeaturedDocument = Documents.parse(stream, "UTF-8", baseUri)
    private val box = Box(447, 447)
    private val box2 = Box(463, 439)

    @Before
    fun teardown() {
        stream.close()
    }

    @Test
    fun testByExpression() {
        val expr = "_img == 1 && _width > 400 && _width < 500 && _height > 400 && _height < 500"
        val elements = doc.select2("div:expr($expr)")
        assertEquals(1, elements.size)
        assertTrue { elements.select2("img").isNotEmpty() }
        assertTrue { elements.last().getFeature(WIDTH) > 400 }
        assertTrue { elements.last().getFeature(HEIGHT) < 500 }
    }

    @Test
    fun testByExpression2() {
        val expr = "_width > 400 && _width < 500 && _height > 400 && _height < 500"
        val elements = doc.select2("*:expr($expr)")
        assertEquals(3, elements.size)
        assertTrue { elements.last().getFeature(WIDTH) > 400 }
        assertTrue { elements.last().getFeature(HEIGHT) < 500 }
        elements.forEach { println("\n\n\n${it.uniqueName}\n$it") }
    }

    @Test
    fun testByBoxAccurate() {
        val box = "$box   ,$box2"
        val elements = doc.select2(convertCssQuery(box))
        assertEquals(3, elements.size)
        assertEquals("1942-div.big.rel", elements[0].uniqueName)
        assertEquals("1944-img", elements[1].uniqueName)
        assertEquals("1980-div.pi_attr_box", elements[2].uniqueName)
        // elements.forEach { println("\n\n\n${it.uniqueName}\n$it") }
    }

    @Test
    fun testByBoxInaccurate() {
        val box = " 450x450     , 470x440  "
        val elements = doc.select2(convertCssQuery(box))
        assertEquals(3, elements.size)
        assertEquals("1942-div.big.rel", elements[0].uniqueName)
        assertEquals("1944-img", elements[1].uniqueName)
        assertEquals("1980-div.pi_attr_box", elements[2].uniqueName)
    }

    @Test
    fun testByBoxInaccurate2() {
        for (i in 0..5) {
            assertTrue { i * 5 <= SELECTOR_IN_BOX_DEVIATION }

            val w = box.width + i * 5
            val h = box.height + i * 5
            val elements = doc.select2(convertCssQuery("${w}x${h}"))
            // elements.forEach { println(it) }
            assertEquals(2, elements.size)
            assertEquals("1942-div.big.rel", elements[0].uniqueName)
            assertEquals("1944-img", elements[1].uniqueName)
        }

        val w = box.width + SELECTOR_IN_BOX_DEVIATION + 1
        val h = box.height + SELECTOR_IN_BOX_DEVIATION + 1
        val elements = doc.select2("$w x $h")
        assertTrue { elements.isEmpty() }
    }
}
