package ca.danielpower.podcast.data

/*
 * An artwork URL column holds every image a feed offers, best first, one per line, so the UI can
 * fall back to the next one when an image fails to load. A single URL is a valid value too.
 */

/** Joins artwork values (each a URL or an already joined list) into one, dropping blanks and repeats. */
fun joinArtwork(vararg urls: String?): String? =
    urls.flatMap(::artworkCandidates).distinct().joinToString("\n").ifEmpty { null }

/** The URLs in an artwork value, best first. */
fun artworkCandidates(urls: String?): List<String> =
    urls?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
