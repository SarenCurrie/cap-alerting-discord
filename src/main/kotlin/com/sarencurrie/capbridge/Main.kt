package com.sarencurrie.capbridge

import com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT
import com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import com.sarencurrie.capbridge.alert.Alert
import com.sarencurrie.capbridge.alert.Certainty
import com.sarencurrie.capbridge.alert.Severity
import com.sarencurrie.capbridge.alert.Urgency
import com.sarencurrie.capbridge.persistence.DynamoDbWrapper
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import java.awt.Color
import java.awt.Polygon
import java.awt.Rectangle
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*
import kotlin.system.exitProcess

const val HOURS_TO_IGNORE = 72
const val aklTopY = -36.39
const val aklBottomY = -37.18
const val aklLeftX = 174.43
const val aklRightX = 175.05
const val RED = "#FF181E"
const val ORANGE = "#FF8918"
const val YELLOW = "#FFEB18"

fun main() {
    checkCap()
    exitProcess(0)
}

fun checkCap() {
    val db = DynamoDbWrapper()
    db.createTable()

    val startTime = Date()
    println("starting Discord client")

    val builder = JDABuilder.createDefault(System.getenv("CAP_TOKEN"))

    // Disable parts of the cache

    // Disable parts of the cache
    builder.disableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE)
    builder.disableIntents(GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_MESSAGE_TYPING)
    // Set activity (like "playing Something")
    builder.setActivity(Activity.watching("for alerts"))

    val discordClient = builder.build()

    discordClient.awaitReady()

    println("Starting CAP Bridge")

//    val feedUrl = URL("https://api.geonet.org.nz/cap/1.2/GPA1.0/feed/atom1.0/quake")
    val feedUrl = URL("https://alerts.metservice.com/cap/atom")
//    val feedUrl = URL("https://alerthub.civildefence.govt.nz/rss/pwp")
//    val feedUrl = URL("https://api.preparecenter.org/v1/org/nzl/alerts/rss")

    val input = SyndFeedInput()
    val feed: SyndFeed = input.build(XmlReader(feedUrl))

    val client = HttpClient.newHttpClient()

    for (entry in feed.entries) {
//        println(entry)
        if (entry.publishedDate < Date(Date().time - 1000 * 60 * 60 * HOURS_TO_IGNORE)) {
            print("Ignoring published more than $HOURS_TO_IGNORE hours ago: ")
            println(entry.title)
            continue
        }

        val link = entry.links.find { link -> link.type == "application/cap+xml" }?.href ?: entry.link
        val body = client.send(HttpRequest.newBuilder(URI(link)).GET().build(), HttpResponse.BodyHandlers.ofString()).body()
        val mapper = XmlMapper().registerKotlinModule().enable(ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, ACCEPT_SINGLE_VALUE_AS_ARRAY)
        val alert = mapper.readValue<Alert>(body)
//        println(alert)
        if (db.hasSent(alert.identifier)) {
            continue
        }

        if (alert.info.expires > Date()) {
            if (isInArea(alert)) {
                val embed = buildEmbed(alert)

                println("Sending message")

                discordClient.textChannels
                    .filter { channel -> channel.name == "alerts" }
                    .forEach { channel -> channel.sendMessage(embed).complete() }

                db.store(alert.identifier)
            } else {
                println("Not in area:")
                println(alert.info.area.areaDesc)
            }
        } else {
            println("Ignoring expired")
            println(alert.info.headline)
        }
    }

    val stopTime = Date()
    val interval = (stopTime.time - startTime.time) / 1000
    print(interval)
    println(" seconds")
}

private fun buildEmbed(
    alert: Alert
): MessageEmbed {
    val colorCode = extractColourCode(alert)
    val builder = EmbedBuilder()
    try {
        builder
            .setTitle(alert.info.headline, alert.info.web)
            .setAuthor(alert.info.senderName, alert.sender)//, "https://metservice.com/public/favicons/apple-touch-icon.png")
            .setColor(Color.decode(colorCode))
            .setDescription(alert.info.description ?: "Description missing")
    } catch (e: Exception) {
        println(alert)
        throw e
    }
    if (alert.info.instruction != null) {
        builder.addField("Instruction", alert.info.instruction, false)
    }
    if (alert.info.onset != null) {
        builder.addField("Onset", alert.info.onset.toString(), true)
    }
    if (alert.info.effective != null) {
        builder.addField("Effective", alert.info.effective.toString(), true)
    }
    builder.addField("Expires", alert.info.expires.toString(), true)
    if (alert.info.effective == null) {
        // Keeps the nice 3 wide grid
        builder.addBlankField(true)
    }
    builder.addField("Urgency", alert.info.urgency.toString(), true)
        .addField("Severity", alert.info.severity.toString(), true)
        .addField("Certainty", alert.info.certainty.toString(), true)
        .addField("Area", alert.info.area.areaDesc, false)
    return builder.build()
}

private fun extractColourCode(alert: Alert): String {
    val paramColourCode = alert.info.parameters?.find { p -> p.valueName.equals("ColourCodeHex") }?.value
    if (paramColourCode != null) {
        return paramColourCode
    }

    val urgencyCriteria = alert.info.urgency == Urgency.Immediate || alert.info.urgency == Urgency.Expected
    val severityCriteria = alert.info.severity == Severity.Extreme || alert.info.severity == Severity.Severe
    val certaintyCriteria = alert.info.certainty == Certainty.Observed || alert.info.certainty == Certainty.Likely

    fun Boolean.toInt() = if (this) 1 else 0

    val criteriaMet = urgencyCriteria.toInt() + severityCriteria.toInt() + certaintyCriteria.toInt()

    return when {
        criteriaMet == 3 -> {
            RED
        }
        criteriaMet >= 1 -> {
            ORANGE
        }
        else -> {
            YELLOW
        }
    }
}

fun isInArea(alert: Alert): Boolean {
    if (alert.info.area.areaDesc.contains("Auckland")) {
        return true
    }
    val area = alert.info.area.polygons?.map { getPolygonPoints(it) }
    // Assuming missing area is whole country
    return area == null || area.isEmpty() || area.any { doesIntersectAuckland(it) }
}

private fun getPolygonPoints(polygonString: String) = polygonString
    .split(" ")
    .filter { it.split(",").size == 2 }
    .map { Pair(it.split(",")[0], it.split(",")[1]) }
    .filter { pair: Pair<String, String> ->
        try {
            pair.first.toDouble()
            pair.second.toDouble()
            true
        } catch (e: NumberFormatException) {
            // This shouldn't fail, so we should really say why
            println(e)
            false
        }
    }
    .map { Pair(it.first.toDouble(), it.second.toDouble()) }

private fun doesIntersectAuckland(a: List<Pair<Double, Double>>): Boolean {
    if (a.isEmpty() || a.size < 3) {
        return false
    }
    val polyA = toPolygon(a)
    return polyA.intersects(Rectangle(pInt(aklBottomY), pInt(aklLeftX), pInt(aklTopY - aklBottomY), pInt(aklRightX - aklLeftX)))
}

private fun toPolygon(a: List<Pair<Double, Double>>) = Polygon(a.map { pInt(it.first) }.toIntArray(), a.map { pInt(it.second) }.toIntArray(), a.size)

// Because AWT only uses ints
private fun pInt(a: Double) = (a * 10000).toInt()