package com.dec.monitor.server

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import com.dec.monitor.TelegramBot
import com.dec.monitor.system.DeviceInfo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList

object PhoneServer {

    private var server: NettyApplicationEngine? = null
    var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())
    val wsClients = CopyOnWriteArrayList<DefaultWebSocketSession>()

    fun start(context: Context) {
        if (server != null) return

        server = embeddedServer(Netty, port = 7474) {
            install(WebSockets)

            routing {

                get("/") {
                    call.respondText("Dec Monitor Online ✅")
                }

                get("/screenshot") {
                    val bytes = captureScreenshot()
                    if (bytes != null) {
                        call.respondBytes(bytes, ContentType.Image.JPEG)
                    } else {
                        call.respondText("Screenshot unavailable")
                    }
                }

                get("/battery") {
                    val info = DeviceInfo.getBatteryInfo(context)
                    call.respondText(info)
                }

                // MJPEG live stream
                get("/stream") {
                    call.respond(object : OutgoingContent.WriteChannelContent() {
                        override val contentType = ContentType.parse("multipart/x-mixed-replace; boundary=frame")
                        override suspend fun writeTo(channel: io.ktor.utils.io.ByteWriteChannel) {
                            while (true) {
                                val bytes = captureScreenshot() ?: break
                                val header = "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${bytes.size}\r\n\r\n"
                                channel.writeFully(header.toByteArray())
                                channel.writeFully(bytes)
                                channel.writeFully("\r\n".toByteArray())
                                channel.flush()
                                delay(500)
                            }
                        }
                    })
                }

                webSocket("/ws") {
                    wsClients.add(this)
                    try {
                        for (frame in incoming) { }
                    } finally {
                        wsClients.remove(this)
                    }
                }

                get("/mcp/screenshot") {
                    val bytes = captureScreenshot()
                    if (bytes != null) {
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        call.respondText("""{"type":"image","data":"$base64","mime":"image/jpeg"}""")
                    } else {
                        call.respondText("""{"error":"MediaProjection not granted"}""")
                    }
                }

                get("/mcp/battery") {
                    val info = DeviceInfo.getBatteryInfo(context)
                    call.respondText("""{"battery":"$info"}""")
                }

                get("/mcp/status") {
                    call.respondText("""{"status":"online","version":"1.0","device":"${android.os.Build.MODEL}"}""")
                }
            }
        }.start(wait = false)

        // WebSocket frame pusher
        CoroutineScope(Dispatchers.IO).launch {
            while (server != null) {
                if (wsClients.isNotEmpty()) {
                    val bytes = captureScreenshot()
                    if (bytes != null) {
                        val frame = Frame.Binary(true, bytes)
                        wsClients.forEach {
                            try { it.send(frame) } catch (e: Exception) { wsClients.remove(it) }
                        }
                    }
                }
                delay(500)
            }
        }
    }

    fun setupProjection(projection: MediaProjection, width: Int, height: Int, density: Int) {
        mediaProjection = projection
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection.createVirtualDisplay(
            "PhoneServer", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )
    }

    fun captureScreenshot(): ByteArray? {
        return try {
            val image = imageReader?.acquireLatestImage() ?: return null
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val width = image.width
            val height = image.height
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
            bitmap.recycle()
            out.toByteArray()
        } catch (e: Exception) { null }
    }

    fun stop() {
        server?.stop(0, 0)
        server = null
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
    }
}
