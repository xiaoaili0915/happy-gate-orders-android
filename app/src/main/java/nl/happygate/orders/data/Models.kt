package nl.happygate.orders.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object OrderStatus {
    const val PENDING = "pending_restaurant_confirmation"
    const val APPROVED = "approved"
    const val READY = "ready"
    const val COMPLETED = "completed"
    const val REJECTED_MANUAL = "rejected_manual"
    const val REJECTED_TIMEOUT = "rejected_timeout"
}

@Serializable
data class OrderModifier(
    val name: String,
    @SerialName("price_cents") val priceCents: Int = 0,
)

@Serializable
data class OrderLine(
    val name: String,
    val quantity: Int,
    @SerialName("unit_price_cents") val unitPriceCents: Int,
    @SerialName("special_notes") val specialNotes: String = "",
    val modifiers: List<OrderModifier> = emptyList(),
) {
    val lineTotalCents: Int
        get() = (unitPriceCents + modifiers.sumOf { it.priceCents }) * quantity
}

@Serializable
data class PrintEvent(
    val status: String,
    val message: String = "",
    val device: String = "",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Order(
    val id: Int,
    val status: String,
    @SerialName("customer_name") val customerName: String,
    @SerialName("customer_email") val customerEmail: String = "",
    @SerialName("customer_phone") val customerPhone: String = "",
    @SerialName("pickup_notes") val pickupNotes: String = "",
    @SerialName("subtotal_cents") val subtotalCents: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("approved_at") val approvedAt: String? = null,
    @SerialName("rejected_at") val rejectedAt: String? = null,
    @SerialName("ready_at") val readyAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("preparation_minutes") val preparationMinutes: Int? = null,
    @SerialName("estimated_ready_at") val estimatedReadyAt: String? = null,
    @SerialName("expected_pickup_time") val expectedPickupTime: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String = "",
    val items: List<OrderLine> = emptyList(),
    @SerialName("print_events") val printEvents: List<PrintEvent> = emptyList(),
) {
    val isPending get() = status == OrderStatus.PENDING
    val isApproved get() = status == OrderStatus.APPROVED
    val isReady get() = status == OrderStatus.READY
    val isActive get() = isApproved || isReady
    val isRejected get() = status == OrderStatus.REJECTED_MANUAL || status == OrderStatus.REJECTED_TIMEOUT

    /** Latest print outcome reported to the backend by any device, if any. */
    val lastPrintStatus: String? get() = printEvents.lastOrNull()?.status
}

@Serializable data class AcceptBody(@SerialName("preparation_minutes") val preparationMinutes: Int)
@Serializable data class RejectBody(val reason: String)
@Serializable data class PrintEventBody(val status: String, val message: String = "", val device: String = "")
@Serializable data class DeviceBody(val token: String, val platform: String = "android")
@Serializable data class PingResponse(
    val ok: Boolean,
    @SerialName("restaurant_name") val restaurantName: String = "",
    val timezone: String = "",
    @SerialName("server_time") val serverTime: String = "",
)
@Serializable data class ApiError(val detail: String = "")

// ---- formatting helpers shared by screens and the ticket ----

fun String.toInstantOrNull(): Instant? = try {
    OffsetDateTime.parse(this).toInstant()
} catch (e: Exception) {
    null
}

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM HH:mm")

fun String?.asLocalTime(): String =
    this?.toInstantOrNull()?.atZone(ZoneId.systemDefault())?.format(timeFmt) ?: "--:--"

fun String?.asLocalDateTime(): String =
    this?.toInstantOrNull()?.atZone(ZoneId.systemDefault())?.format(dateTimeFmt) ?: "--"

fun Int.euro(): String = "€%.2f".format(this / 100.0).replace('.', ',')

fun Order.statusLabel(): String = when (status) {
    OrderStatus.PENDING -> "New"
    OrderStatus.APPROVED -> "Preparing"
    OrderStatus.READY -> "Ready"
    OrderStatus.COMPLETED -> "Picked up"
    OrderStatus.REJECTED_MANUAL -> "Rejected"
    OrderStatus.REJECTED_TIMEOUT -> "Expired"
    else -> status
}
