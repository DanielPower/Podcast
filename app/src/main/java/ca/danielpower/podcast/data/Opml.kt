package ca.danielpower.podcast.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream

/** OPML, the file format podcast apps use to move a list of subscriptions between them. */
object Opml {
    /** The feed URL of every `<outline>`, however the file nests them, in order and without repeats. */
    fun parseFeedUrls(input: InputStream): List<String> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val urls = LinkedHashSet<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || !parser.name.equals("outline", ignoreCase = true)) continue
            val url = (0 until parser.attributeCount)
                .firstOrNull { parser.getAttributeName(it).equals("xmlUrl", ignoreCase = true) }
                ?.let { parser.getAttributeValue(it) }
                ?.let(::feedUrlOrNull)
            if (url != null) urls += url
        }
        return urls.toList()
    }

    fun write(podcasts: List<Podcast>, output: OutputStream) {
        val xml = Xml.newSerializer()
        xml.setOutput(output, "UTF-8")
        xml.startDocument("UTF-8", null)
        xml.startTag(null, "opml").attribute(null, "version", "2.0")
        xml.startTag(null, "head")
        xml.startTag(null, "title").text("Podcast subscriptions").endTag(null, "title")
        xml.endTag(null, "head")
        xml.startTag(null, "body")
        for (podcast in podcasts) {
            xml.startTag(null, "outline")
                .attribute(null, "type", "rss")
                .attribute(null, "text", podcast.title)
                .attribute(null, "title", podcast.title)
                .attribute(null, "xmlUrl", podcast.feedUrl)
                .endTag(null, "outline")
        }
        xml.endTag(null, "body")
        xml.endTag(null, "opml")
        xml.endDocument()
        xml.flush()
    }
}
