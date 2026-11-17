package nl.happygate.orders.printing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/** Outcome of handing bytes to the printer. A successful write is NOT proof paper came out. */
sealed class PrintOutcome {
    /** All bytes were written and the socket closed cleanly. */
    data object Sent : PrintOutcome()
    /** Nothing reached the printer (connect/refused/timeout before the first byte). Safe to retry. */
    data class Failed(val reason: String) : PrintOutcome()
    /** The connection broke after some bytes were written. The ticket may or may not have printed. */
    data class Uncertain(val reason: String) : PrintOutcome()
}

interface PrinterTransport {
    suspend fun send(bytes: ByteArray): PrintOutcome
}

/** Raw TCP ("JetDirect", port 9100) transport used by Epson TM LAN/Wi-Fi printers. */
class NetworkPrinterTransport(
    private val host: String,
    private val port: Int = 9100,
    private val connectTimeoutMs: Int = 5000,
) : PrinterTransport {

    override suspend fun send(bytes: ByteArray): PrintOutcome = withContext(Dispatchers.IO) {
        var wroteAny = false
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                socket.soTimeout = connectTimeoutMs
                val os = socket.getOutputStream()
                // Write in chunks so a mid-way failure is distinguishable from a failure to connect.
                var offset = 0
                while (offset < bytes.size) {
                    val n = minOf(1024, bytes.size - offset)
                    os.write(bytes, offset, n)
                    wroteAny = true
                    offset += n
                }
                os.flush()
                socket.shutdownOutput()
            }
            PrintOutcome.Sent
        } catch (e: IOException) {
            val reason = e.message ?: e.javaClass.simpleName
            if (wroteAny) PrintOutcome.Uncertain(reason) else PrintOutcome.Failed(reason)
        }
    }
}
