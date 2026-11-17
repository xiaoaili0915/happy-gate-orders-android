package nl.happygate.orders.printing

import nl.happygate.orders.data.Order
import nl.happygate.orders.data.asLocalTime
import nl.happygate.orders.data.euro
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Builds a kitchen ticket as raw ESC/POS bytes for 80mm (48 col) or 58mm (32 col)
 * Epson-compatible thermal printers. This is the lowest common denominator that
 * every Epson TM-series LAN model understands on TCP port 9100; swapping in the
 * Epson ePOS SDK later only requires another [PrinterTransport].
 */
class EscPosTicket(private val columns: Int = 48) {

    private val out = ByteArrayOutputStream()
    private val charset: Charset = runCatching { Charset.forName("windows-1252") }.getOrDefault(Charsets.ISO_8859_1)

    private fun raw(vararg b: Int) = out.write(b.map { it.toByte() }.toByteArray())
    private fun text(s: String) = out.write(s.toByteArray(charset))
    private fun line(s: String = "") { text(s); raw(0x0A) }

    private fun init() { raw(0x1B, 0x40); raw(0x1B, 0x74, 16) } // ESC @ , ESC t 16 (WPC1252)
    private fun align(a: Int) = raw(0x1B, 0x61, a) // 0 left, 1 centre, 2 right
    private fun bold(on: Boolean) = raw(0x1B, 0x45, if (on) 1 else 0)
    private fun size(wide: Boolean, tall: Boolean) = raw(0x1D, 0x21, (if (wide) 0x10 else 0) or (if (tall) 0x01 else 0))
    private fun feed(n: Int) = raw(0x1B, 0x64, n)
    private fun cut() = raw(0x1D, 0x56, 66, 0) // partial cut with feed
    private fun rule(ch: Char = '-') = line(ch.toString().repeat(columns))

    private fun twoCols(left: String, right: String): String {
        val space = columns - right.length
        val l = if (left.length > space - 1) left.take(space - 1) else left
        return l.padEnd(space) + right
    }

    private fun wrap(s: String, indent: Int = 0): List<String> {
        val width = columns - indent
        val words = s.split(' ')
        val lines = mutableListOf<String>()
        var cur = StringBuilder()
        for (w in words) {
            if (cur.isNotEmpty() && cur.length + 1 + w.length > width) { lines += cur.toString(); cur = StringBuilder() }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(w)
        }
        if (cur.isNotEmpty()) lines += cur.toString()
        return lines.map { " ".repeat(indent) + it }
    }

    fun build(order: Order, restaurantName: String, reprint: Boolean = false): ByteArray {
        init()
        align(1)
        bold(true); size(wide = true, tall = true)
        line(restaurantName.ifBlank { "HAPPY GATE" })
        line("#${order.id}")
        size(wide = false, tall = false)
        if (reprint) line("*** REPRINT ***")
        bold(false)
        line("Pickup order")
        line()

        align(0)
        bold(true); size(wide = false, tall = true)
        line("READY AT  ${order.estimatedReadyAt.asLocalTime()}")
        size(wide = false, tall = false); bold(false)
        line("Ordered   ${order.createdAt.asLocalTime()}   Accepted ${order.approvedAt.asLocalTime()}")
        rule()
        line("Customer: ${order.customerName}")
        line("Phone:    ${order.customerPhone}")
        rule()

        for (item in order.items) {
            bold(true); size(wide = false, tall = true)
            line(twoCols("${item.quantity}x ${item.name}", item.lineTotalCents.euro()))
            size(wide = false, tall = false); bold(false)
            for (m in item.modifiers) line("   + ${m.name}")
            if (item.specialNotes.isNotBlank()) {
                bold(true); wrap("NOTE: ${item.specialNotes}", 3).forEach { line(it) }; bold(false)
            }
        }
        rule()
        bold(true)
        line(twoCols("TOTAL", order.subtotalCents.euro()))
        bold(false)
        if (order.pickupNotes.isNotBlank()) {
            line()
            bold(true); line("ORDER NOTES:"); bold(false)
            wrap(order.pickupNotes).forEach { line(it) }
        }
        feed(3)
        cut()
        return out.toByteArray()
    }

    fun buildTest(restaurantName: String, printerHost: String): ByteArray {
        init(); align(1); bold(true); size(wide = true, tall = true)
        line(restaurantName.ifBlank { "HAPPY GATE" })
        size(wide = false, tall = false); bold(false)
        line("Printer test OK")
        line(printerHost)
        feed(3); cut()
        return out.toByteArray()
    }
}
