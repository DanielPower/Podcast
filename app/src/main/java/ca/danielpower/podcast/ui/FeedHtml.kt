package ca.danielpower.podcast.ui

/**
 * Feed text, often HTML, read into blocks of plain text with inline styles. It's only read, never
 * run: scripts, styles, frames and images are dropped, and links keep only web, mail and phone
 * addresses. [FeedText] styles and lays them out.
 */
fun parseFeedHtml(text: String): List<FeedBlock> =
    if (looksLikeHtml(text)) HtmlReader(text).read() else plainBlocks(text)

sealed interface FeedBlock {
    /** How many blockquotes it's inside. */
    val quote: Int

    data class Paragraph(val text: Inline, override val quote: Int = 0) : FeedBlock

    /** `h1` to `h6`. */
    data class Heading(val level: Int, val text: Inline, override val quote: Int = 0) : FeedBlock

    /**
     * A list item, [depth] lists deep (0 for a top-level list). [marker] is "•", "◦" or "3."; it's
     * null for text that carries on in the item after a nested list.
     */
    data class ListItem(val marker: String?, val depth: Int, val text: Inline, override val quote: Int = 0) : FeedBlock

    /** Preformatted text, with its line breaks and spacing kept. */
    data class Code(val text: String, override val quote: Int = 0) : FeedBlock

    /** `hr` */
    data class Rule(override val quote: Int = 0) : FeedBlock
}

/** Text with styles over character ranges. */
data class Inline(val text: String, val spans: List<InlineSpan> = emptyList())

data class InlineSpan(val start: Int, val end: Int, val style: InlineStyle)

sealed interface InlineStyle {
    data object Bold : InlineStyle
    data object Italic : InlineStyle
    data object Underline : InlineStyle
    data object Strike : InlineStyle
    data object Code : InlineStyle
    data class Link(val url: String) : InlineStyle
}

private val htmlTag = Regex("""<(?:[a-zA-Z][a-zA-Z0-9]*[\s/>]|/[a-zA-Z]|!--)""")

private fun looksLikeHtml(text: String) = htmlTag.containsMatchIn(text)

/** Plain text: paragraphs split at blank lines, single line breaks kept. */
private fun plainBlocks(text: String): List<FeedBlock> =
    decodeEntities(text).replace("\r\n", "\n").split(Regex("""\n[ \t]*\n\s*"""))
        .map { paragraph -> paragraph.lines().joinToString("\n") { it.trim() }.trim() }
        .filter { it.isNotEmpty() }
        .map { FeedBlock.Paragraph(Inline(it)) }

/** Tags whose content is never shown. */
private val skippedTags = setOf("script", "style", "head", "title", "noscript", "template", "iframe", "object", "svg", "math", "select", "textarea")

/** Tags that start and end a paragraph. */
private val blockTags = setOf(
    "p", "div", "section", "article", "header", "footer", "main", "aside", "nav", "figure", "figcaption",
    "table", "thead", "tbody", "tfoot", "tr", "dl", "dt", "dd", "center", "address", "details", "summary", "body", "html",
)

private val safeSchemes = listOf("http://", "https://", "mailto:", "tel:")

private class HtmlReader(private val html: String) {
    private val blocks = mutableListOf<FeedBlock>()
    private val text = StringBuilder()
    private val spans = mutableListOf<InlineSpan>()
    /** Inline styles open now, with where each started. */
    private val open = mutableListOf<Pair<String, Pair<Int, InlineStyle?>>>()

    private var quote = 0
    private var heading = 0
    private var pre = 0
    /** One entry per open list: whether it's numbered, and the number the next item takes. */
    private val lists = mutableListOf<IntArray>()
    /** Open list items' depths; the marker is used by the item's first block only. */
    private var itemDepth = -1
    private var pendingMarker: String? = null

    private var i = 0

    fun read(): List<FeedBlock> {
        while (i < html.length) {
            val c = html[i]
            if (c == '<' && readMarkup()) continue
            val next = html.indexOf('<', i + 1).let { if (it < 0) html.length else it }
            appendText(decodeEntities(html.substring(i, next)))
            i = next
        }
        flush()
        return blocks
    }

    /** Reads a tag or comment at [i]; false when the `<` is just text. */
    private fun readMarkup(): Boolean {
        if (html.startsWith("<!--", i)) {
            val end = html.indexOf("-->", i + 4)
            i = if (end < 0) html.length else end + 3
            return true
        }
        if (html.startsWith("<!", i) || html.startsWith("<?", i)) {
            val end = html.indexOf('>', i)
            i = if (end < 0) html.length else end + 1
            return true
        }
        var j = i + 1
        val closing = j < html.length && html[j] == '/'
        if (closing) j++
        val nameStart = j
        while (j < html.length && html[j].isLetterOrDigit()) j++
        if (j == nameStart || !html[nameStart].isLetter()) return false
        val name = html.substring(nameStart, j).lowercase()
        // Attributes run to the next > outside quotes.
        val attrStart = j
        var quoteChar = 0.toChar()
        while (j < html.length) {
            val ch = html[j]
            if (quoteChar != 0.toChar()) {
                if (ch == quoteChar) quoteChar = 0.toChar()
            } else if (ch == '"' || ch == '\'') {
                quoteChar = ch
            } else if (ch == '>') {
                break
            }
            j++
        }
        val attrs = html.substring(attrStart, j.coerceAtMost(html.length))
        i = (j + 1).coerceAtMost(html.length)
        if (closing) closeTag(name) else openTag(name, attrs)
        return true
    }

    private fun openTag(name: String, attrs: String) {
        if (name in skippedTags && !attrs.trimEnd().endsWith("/")) {
            val end = Regex("</$name\\s*>", RegexOption.IGNORE_CASE).find(html, i)
            i = end?.range?.last?.plus(1) ?: html.length
            return
        }
        when (name) {
            in blockTags -> flush()
            "br" -> if (pre > 0) text.append('\n') else newLine()
            "hr" -> {
                flush()
                blocks += FeedBlock.Rule(quote)
            }
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                flush()
                heading = name[1] - '0'
            }
            "blockquote" -> {
                flush()
                quote++
            }
            "pre" -> {
                flush()
                pre++
            }
            "ul", "ol" -> {
                flush()
                val start = if (name == "ol") attribute(attrs, "start")?.toIntOrNull() ?: 1 else 0
                lists += intArrayOf(if (name == "ol") 1 else 0, start)
            }
            "li" -> {
                flush()
                val list = lists.lastOrNull()
                itemDepth = (lists.size - 1).coerceAtLeast(0)
                pendingMarker = when {
                    list != null && list[0] == 1 -> "${list[1]++}."
                    itemDepth == 0 -> "•"
                    else -> "◦"
                }
            }
            "td", "th" -> appendText(" ")
            "b", "strong" -> startStyle(name, InlineStyle.Bold)
            "i", "em", "cite", "dfn", "var" -> startStyle(name, InlineStyle.Italic)
            "u", "ins" -> startStyle(name, InlineStyle.Underline)
            "s", "strike", "del" -> startStyle(name, InlineStyle.Strike)
            "code", "tt", "kbd", "samp" -> startStyle(name, if (pre > 0) null else InlineStyle.Code)
            "a" -> {
                val href = attribute(attrs, "href")?.let(::decodeEntities)?.trim()
                startStyle(name, href?.takeIf { url -> safeSchemes.any { url.startsWith(it, ignoreCase = true) } }?.let { InlineStyle.Link(it) })
            }
        }
    }

    private fun closeTag(name: String) {
        when (name) {
            in blockTags -> flush()
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                flush()
                heading = 0
            }
            "blockquote" -> {
                flush()
                quote = (quote - 1).coerceAtLeast(0)
            }
            "pre" -> {
                flush()
                pre = (pre - 1).coerceAtLeast(0)
            }
            "ul", "ol" -> {
                flush()
                if (lists.isNotEmpty()) lists.removeAt(lists.lastIndex)
                // Text after a nested list carries on in the outer item.
                itemDepth = if (lists.isEmpty()) -1 else lists.size - 1
                pendingMarker = null
            }
            "li" -> {
                flush()
                itemDepth = if (lists.isEmpty()) -1 else lists.size - 2
                pendingMarker = null
            }
            else -> endStyle(name)
        }
    }

    private fun startStyle(tag: String, style: InlineStyle?) {
        open += tag to (text.length to style)
    }

    private fun endStyle(tag: String) {
        val index = open.indexOfLast { it.first == tag }
        if (index < 0) return
        // Close it and anything left open inside it.
        while (open.size > index) {
            val (_, started) = open.removeAt(open.lastIndex)
            val style = started.second ?: continue
            if (started.first < text.length) spans += InlineSpan(started.first, text.length, style)
        }
    }

    private fun appendText(s: String) {
        if (pre > 0) {
            text.append(s)
            return
        }
        for (ch in s) {
            if (ch.isWhitespace() && ch != ' ') {
                // Collapse runs of spaces, and drop them at the start of a line.
                if (text.isNotEmpty() && text.last() != ' ' && text.last() != '\n') text.append(' ')
            } else {
                text.append(if (ch == ' ') ' ' else ch)
            }
        }
    }

    private fun newLine() {
        while (text.isNotEmpty() && text.last() == ' ') text.setLength(text.length - 1)
        if (text.isNotEmpty()) text.append('\n')
    }

    /** Ends the block being read, if it has any text. */
    private fun flush() {
        // Styles still open carry on into the next block.
        val stillOpen = open.map { it.first to it.second.second }
        endAll()
        val block = if (pre > 0) codeBlock() else textBlock()
        if (block != null) blocks += block
        text.setLength(0)
        spans.clear()
        for ((tag, style) in stillOpen) startStyle(tag, style)
    }

    private fun endAll() {
        for ((_, started) in open) {
            val style = started.second ?: continue
            if (started.first < text.length) spans += InlineSpan(started.first, text.length, style)
        }
        open.clear()
    }

    private fun codeBlock(): FeedBlock? {
        val code = text.toString().trim('\n', '\r').trimEnd()
        return if (code.isBlank()) null else FeedBlock.Code(code, quote)
    }

    private fun textBlock(): FeedBlock? {
        var start = 0
        var end = text.length
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        if (start == end) return null
        val inline = Inline(
            text.substring(start, end),
            spans.mapNotNull { span ->
                val s = span.start.coerceIn(start, end) - start
                val e = span.end.coerceIn(start, end) - start
                if (s < e) InlineSpan(s, e, span.style) else null
            },
        )
        return when {
            heading > 0 -> FeedBlock.Heading(heading, inline, quote)
            itemDepth >= 0 -> FeedBlock.ListItem(pendingMarker, itemDepth, inline, quote).also { pendingMarker = null }
            else -> FeedBlock.Paragraph(inline, quote)
        }
    }
}

private fun attribute(attrs: String, name: String): String? {
    val match = Regex("""(?:^|\s)$name\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""", RegexOption.IGNORE_CASE).find(attrs) ?: return null
    return match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: ""
}

private val entity = Regex("""&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});?""")

private val namedEntities = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    "ndash" to "–", "mdash" to "—", "hellip" to "…", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“",
    "rdquo" to "”", "laquo" to "«", "raquo" to "»", "bull" to "•", "middot" to "·", "copy" to "©",
    "reg" to "®", "trade" to "™", "deg" to "°", "times" to "×", "divide" to "÷", "plusmn" to "±",
    "frac12" to "½", "frac14" to "¼", "frac34" to "¾", "euro" to "€", "pound" to "£", "yen" to "¥",
    "cent" to "¢", "sect" to "§", "para" to "¶", "dagger" to "†", "larr" to "←", "rarr" to "→",
    "uarr" to "↑", "darr" to "↓", "hearts" to "♥", "star" to "☆", "check" to "✓", "shy" to "",
    "zwj" to "‍", "zwnj" to "‌", "thinsp" to " ", "ensp" to " ", "emsp" to " ",
    "iexcl" to "¡", "iquest" to "¿", "szlig" to "ß",
    "agrave" to "à", "aacute" to "á", "acirc" to "â", "atilde" to "ã", "auml" to "ä", "aring" to "å", "aelig" to "æ",
    "ccedil" to "ç", "egrave" to "è", "eacute" to "é", "ecirc" to "ê", "euml" to "ë", "igrave" to "ì", "iacute" to "í",
    "icirc" to "î", "iuml" to "ï", "ntilde" to "ñ", "ograve" to "ò", "oacute" to "ó", "ocirc" to "ô", "otilde" to "õ",
    "ouml" to "ö", "oslash" to "ø", "ugrave" to "ù", "uacute" to "ú", "ucirc" to "û", "uuml" to "ü", "yacute" to "ý",
    "yuml" to "ÿ", "Agrave" to "À", "Aacute" to "Á", "Acirc" to "Â", "Auml" to "Ä", "Aring" to "Å", "Ccedil" to "Ç",
    "Egrave" to "È", "Eacute" to "É", "Ecirc" to "Ê", "Iacute" to "Í", "Ntilde" to "Ñ", "Oacute" to "Ó", "Ouml" to "Ö",
    "Oslash" to "Ø", "Uacute" to "Ú", "Uuml" to "Ü",
)

fun decodeEntities(s: String): String {
    if ('&' !in s) return s
    return entity.replace(s) { match ->
        val name = match.groupValues[1]
        val decoded = when {
            name.startsWith("#x") || name.startsWith("#X") -> name.substring(2).toIntOrNull(16)?.let(::codePoint)
            name.startsWith("#") -> name.substring(1).toIntOrNull()?.let(::codePoint)
            else -> namedEntities[name]
        }
        decoded ?: match.value
    }
}

private fun codePoint(cp: Int): String? =
    if (cp in 1..0x10FFFF && cp !in 0xD800..0xDFFF) String(Character.toChars(cp)) else null
