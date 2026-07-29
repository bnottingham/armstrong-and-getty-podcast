package com.nomnomsom.armstrongandgetty.testutil

import com.nomnomsom.armstrongandgetty.data.model.RssItem

/**
 * Fixtures copied from the live Omny feed (fetched 2026-07-28). Titles and pubDates are
 * verbatim — these are the real publication patterns the engine has to survive:
 *
 *  - Weekday titles carry no hour markers at all; ordering must come from pubDate.
 *  - On some days (7/23) hours 2-4 + the interview all publish in one burst hours after
 *    hour 1 — the "user is already listening" window.
 *  - Weekend items share IDENTICAL pubDates and sit newest-first in the feed, so
 *    "(Hour Two)" precedes "(Hour One)".
 *  - Interview segments recorded in the evening (5-9pm PT) carry pubDates past UTC
 *    midnight, landing on the NEXT UTC calendar day.
 */

fun rssItem(
    title: String,
    pubDate: String,
    url: String = "https://cdn.example/${title.hashCode()}.mp3",
    durationSeconds: Long = 2100
) = RssItem(
    title = title,
    description = "About: $title",
    pubDate = pubDate,
    audioUrl = url,
    durationSeconds = durationSeconds
)

/** 2026-07-23 — hour 1 in the morning, everything else in a burst 5 hours later. */
object BurstDay {
    val hour1 = rssItem("Reasoning with Psychopaths", "Thu, 23 Jul 2026 14:45:20 +0000")
    val hour2 = rssItem("Do I Look Like A Woman?", "Thu, 23 Jul 2026 19:43:31 +0000")
    val hour3 = rssItem("Stay Out of the Fight", "Thu, 23 Jul 2026 19:49:14 +0000")
    val hour4 = rssItem("The Woke & the Willing", "Thu, 23 Jul 2026 19:59:50 +0000")
    val bonus = rssItem("Make Room for the Deer Meat!", "Thu, 23 Jul 2026 20:04:38 +0000")

    const val DATE = "2026-07-23"

    /** Feed order is newest-first. */
    fun feedItems(vararg published: RssItem): List<RssItem> =
        published.sortedByDescending { it.pubDate }

    val all = listOf(hour1, hour2, hour3, hour4, bonus)
}

/** 2026-07-25 — weekend show, both hours share one pubDate, feed lists Hour Two first. */
object WeekendTieDay {
    const val DATE = "2026-07-25"
    val hourTwo = rssItem("The Best Weekend Talk Show:  Hour Two", "Sat, 25 Jul 2026 07:00:00 +0000")
    val hourOne = rssItem("The Best Weekend Talk Show:  Hour One", "Sat, 25 Jul 2026 07:00:00 +0000")

    /** Exactly as the live feed serves them: newest-first ties keep upload order. */
    val feedOrder = listOf(hourTwo, hourOne)
}

/** Evening interviews (real items) whose pubDates crossed UTC midnight. */
object StrayEveningItems {
    // Published ~8:50pm PT on Tue 2026-07-07; UTC day is Wed 2026-07-08.
    val items = listOf(
        rssItem(
            "The Country Would Have Stopped...for Soccer??? Anthony Martinez Talks to A&G",
            "Wed, 08 Jul 2026 03:57:35 +0000"
        ),
        rssItem("Forgotten Blockbusters.  Anastasia Boden Talks to A&G", "Wed, 08 Jul 2026 03:52:44 +0000"),
        rssItem("Using Us to Beat Us.   Josh Rogin Talks to A&G", "Wed, 08 Jul 2026 03:49:38 +0000")
    )
}

/** Verbatim slice of the live feed XML for parser tests (CDATA, namespaced duration, enclosure). */
const val REAL_FEED_XML_SNIPPET = """<?xml version="1.0" encoding="utf-8"?>
<rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" version="2.0">
  <channel>
    <title>Armstrong &amp; Getty On Demand</title>
    <item>
      <title><![CDATA[Fauci Is Guilty of Everything!!!]]></title>
      <description><![CDATA[<p>Hour 1 of the Tuesday July 28, 2026 edition of The Armstrong &amp; Getty Show &nbsp;features...</p>]]></description>
      <pubDate>Tue, 28 Jul 2026 14:43:09 +0000</pubDate>
      <itunes:duration>2144</itunes:duration>
      <enclosure url="https://traffic.omny.fm/d/clips/aaa/bbb/ccc/audio.mp3?utm_source=Podcast" length="34317000" type="audio/mpeg"/>
      <guid isPermaLink="false">ccc</guid>
    </item>
    <item>
      <title><![CDATA[Old Style Duration]]></title>
      <description><![CDATA[Uses mm:ss duration format.]]></description>
      <pubDate>Mon, 27 Jul 2026 14:23:57 GMT</pubDate>
      <itunes:duration>35:00</itunes:duration>
      <enclosure url="https://traffic.omny.fm/d/clips/aaa/bbb/ddd/audio.mp3" length="1" type="audio/mpeg"/>
    </item>
    <item>
      <title><![CDATA[No Enclosure — must be dropped]]></title>
      <description><![CDATA[x]]></description>
      <pubDate>Mon, 27 Jul 2026 15:00:00 +0000</pubDate>
      <itunes:duration>1:05:30</itunes:duration>
    </item>
  </channel>
</rss>"""
