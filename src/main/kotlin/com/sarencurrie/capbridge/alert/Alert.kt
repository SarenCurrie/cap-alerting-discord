package com.sarencurrie.capbridge.alert

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import java.util.*

@JsonIgnoreProperties("Signature")
data class Alert(
    val identifier: String,
    val sender: String,
    val sent: Date,
    val status: String,
    val msgType: MessageType,
    val scope: String,
    val references: String?,
    val info: Info,
    )

enum class MessageType {
    Alert,
    Update,
    Cancel
}

data class Info(
    val category: CapCategory,
    val event: String,
    val eventCode: String?,
    val responseType: ResponseType?,
    val urgency: Urgency,
    val severity: Severity,
    val certainty: Certainty,
    val onset: Date?,
    val effective: Date?,
    val expires: Date,
    val senderName: String,
    val headline: String,
    val description: String?,
    val instruction: String?,
    @JsonProperty("parameter")
    @JacksonXmlElementWrapper(useWrapping = false)
    val parameters: List<CapParameter>?,
    val area: Area,
    val web: String?,
    val language: String?,
    val contact: String?
)

data class Area(
    val areaDesc: String,
    @JsonProperty("polygon")
    @JacksonXmlElementWrapper(useWrapping = false)
    val polygons: List<String>?,
    @JsonProperty("circle")
    @JacksonXmlElementWrapper(useWrapping = false)
    val circles: List<String>?
)

data class CapParameter(
    val valueName: String?,
    val value: String?,
)

enum class CapCategory {
    Met,
    Geo,
    Safety,
    Security,
    Rescue,
    Fire,
    Health,
    Env,
    Transport,
    Infra,
    CBRNE,
    Other
}

enum class ResponseType {
    Monitor,
    Prepare
}

enum class Urgency {
    Future,
    Expected,
    Immediate,
    Past,
    Unknown
}

enum class Severity {
    Minor,
    Moderate,
    Severe,
    Extreme,
    Unknown
}

enum class Certainty {
    Possible,
    Likely,
    Observed,
    Unlikely,
    Unknown
}
