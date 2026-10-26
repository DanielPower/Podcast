package ca.danielpower.podcast.ui

import ca.danielpower.podcast.ui.FeedBlock.Code
import ca.danielpower.podcast.ui.FeedBlock.Heading
import ca.danielpower.podcast.ui.FeedBlock.ListItem
import ca.danielpower.podcast.ui.FeedBlock.Paragraph
import ca.danielpower.podcast.ui.FeedBlock.Rule
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedHtmlTest {
    private fun texts(html: String) = parseFeedHtml(html).map {
        when (it) {
            is Paragraph -> it.text.text
            is Heading -> it.text.text
            is ListItem -> it.text.text
            is Code -> it.text
            is Rule -> "---"
        }
    }

    @Test
    fun splitsParagraphsAndCollapsesSpaces() {
        assertEquals(listOf("One two", "Three"), texts("<p>One\n   two</p>\n<p> Three </p>"))
    }

    @Test
    fun keepsLineBreaks() {
        assertEquals(listOf("First\nSecond"), texts("First<br>Second<br/>"))
    }

    @Test
    fun readsHeadings() {
        val blocks = parseFeedHtml("<h2>In this episode</h2><p>Text</p>")
        assertEquals(Heading(2, Inline("In this episode")), blocks[0])
        assertEquals(Paragraph(Inline("Text")), blocks[1])
    }

    @Test
    fun marksListItems() {
        val blocks = parseFeedHtml("<ul><li>a<ul><li>b</li></ul>c</li><li>d</li></ul><ol start=\"3\"><li>e</li><li>f</li></ol>")
        assertEquals(
            listOf(
                ListItem("•", 0, Inline("a")),
                ListItem("◦", 1, Inline("b")),
                ListItem(null, 0, Inline("c")),
                ListItem("•", 0, Inline("d")),
                ListItem("3.", 0, Inline("e")),
                ListItem("4.", 0, Inline("f")),
            ),
            blocks,
        )
    }

    @Test
    fun readsInlineStyles() {
        val block = parseFeedHtml("<p>Plain <b>bold <i>both</i></b> <code>x</code></p>").single() as Paragraph
        assertEquals("Plain bold both x", block.text.text)
        assertEquals(
            setOf(
                InlineSpan(6, 15, InlineStyle.Bold),
                InlineSpan(11, 15, InlineStyle.Italic),
                InlineSpan(16, 17, InlineStyle.Code),
            ),
            block.text.spans.toSet(),
        )
    }

    @Test
    fun keepsOnlySafeLinks() {
        val block = parseFeedHtml("""<a href="https://example.com/?a=1&amp;b=2">site</a> <a href="javascript:alert(1)">bad</a>""").single() as Paragraph
        assertEquals("site bad", block.text.text)
        assertEquals(listOf(InlineSpan(0, 4, InlineStyle.Link("https://example.com/?a=1&b=2"))), block.text.spans)
    }

    @Test
    fun dropsScriptsStylesAndImages() {
        assertEquals(
            listOf("Hello there"),
            texts("<style>p{color:red}</style><script>alert('<p>')</script><p>Hello <img src=\"x.png\"> there</p><!-- note -->"),
        )
    }

    @Test
    fun readsQuotesRulesAndCode() {
        val blocks = parseFeedHtml("<blockquote><p>Said</p></blockquote><hr><pre>a\n  b</pre>")
        assertEquals(listOf(Paragraph(Inline("Said"), quote = 1), Rule(), Code("a\n  b")), blocks)
    }

    @Test
    fun decodesEntities() {
        assertEquals(listOf("Q&A — “quotes” it’s 5 > 4 é AT&T"), texts("<p>Q&amp;A &mdash; &#8220;quotes&#x201D; it&rsquo;s 5 &gt; 4 &eacute; AT&T</p>"))
    }

    @Test
    fun readsPlainText() {
        assertEquals(listOf("One\nline two", "Three"), texts("One\n line two \n\n\nThree"))
        assertEquals(listOf("5 < 6 & fine"), texts("5 < 6 &amp; fine"))
    }

    @Test
    fun keepsStylesAcrossBlocks() {
        val blocks = parseFeedHtml("<b>one<p>two</p></b>")
        assertEquals(listOf(InlineSpan(0, 3, InlineStyle.Bold)), (blocks[0] as Paragraph).text.spans)
        assertEquals(listOf(InlineSpan(0, 3, InlineStyle.Bold)), (blocks[1] as Paragraph).text.spans)
    }

    @Test
    fun copesWithSloppyHtml() {
        assertEquals(listOf("a", "b < c <3", "d"), texts("<p>a<p>b < c <3<div>d"))
    }
}
