package com.b5solucoes.resultadostv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

data class JsonResponse(val json: JSONObject, val cached: Boolean, val fetchedAt: Long, val warning: String = "")
class SourceException(val status: Int, message: String) : IOException(message)

/** Each URL has its own atomic cache. Only responses validated against the selection are saved. */
class Repository(context: Context) {
    private val directory = File(context.filesDir, "official-data").apply { mkdirs() }
    private val blocked = mutableMapOf<String, Long>()
    private var hostBlockedUntil = 0L
    private var lastRequestAt = 0L
    private fun file(url: String) = File(directory, digest(url) + ".json")
    @Synchronized
    fun cached(url: String, validate: (JSONObject) -> Unit = {}): JsonResponse? = runCatching {
        val entry = JSONObject(file(url).readText())
        require(entry.getString("url") == url)
        val json = entry.getJSONObject("data")
        validate(json)
        JsonResponse(json, true, entry.getLong("checkedAt"), "Exibindo a última consulta salva.")
    }.getOrNull()

    @Synchronized
    fun get(url: String, maxAge: Long = 0, validate: (JSONObject) -> Unit = {}): JsonResponse {
        require(URL(url).protocol == "https" && URL(url).host == "resultados.tse.jus.br")
        val old = cached(url, validate)
        val now = System.currentTimeMillis()
        if (old != null && now - old.fetchedAt < maxAge) return old.copy(cached = false, warning = "")
        val waitUntil = maxOf(hostBlockedUntil, blocked[url] ?: 0L)
        if (now < waitUntil) {
            if (old != null) return old.copy(warning = "Fonte temporariamente indisponível. Exibindo consulta salva.")
            throw SourceException(429, "Aguardando nova consulta para respeitar o limite da fonte. Tente em alguns minutos.")
        }
        var connection: HttpURLConnection? = null
        try {
            // One connection at a time and at least 500 ms between requests, including UI changes.
            val pause = 500 - (now - lastRequestAt)
            if (pause > 0) Thread.sleep(pause)
            connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 12_000
            connection.readTimeout = 18_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "ResultadosTV/1.0 (cliente independente; Android TV)")
            val oldEntry = if (old == null) null else runCatching { JSONObject(file(url).readText()) }.getOrNull()
            oldEntry?.optString("etag")?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("If-None-Match", it) }
            oldEntry?.optString("modified")?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("If-Modified-Since", it) }
            lastRequestAt = System.currentTimeMillis()
            val status = connection.responseCode
            if (status == 304 && old != null && oldEntry != null) {
                oldEntry.put("checkedAt", System.currentTimeMillis())
                save(url, oldEntry)
                return old.copy(cached = false, fetchedAt = oldEntry.getLong("checkedAt"), warning = "")
            }
            if (status != 200) {
                val delay = when(status) { 403, 429 -> 610_000L; 404 -> 300_000L; else -> 120_000L }
                blocked[url] = System.currentTimeMillis() + delay
                if (status == 403 || status == 429) hostBlockedUntil = System.currentTimeMillis() + delay
                throw SourceException(status, when(status) {
                    404 -> "Este resultado ainda não está disponível na fonte para a seleção atual."
                    403, 429 -> "A fonte limitou temporariamente as consultas. A próxima tentativa será automática."
                    else -> "O TSE não respondeu à consulta (HTTP $status)."
                })
            }
            val bytes = connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val size = stream.read(buffer)
                    if (size < 0) break
                    output.write(buffer, 0, size)
                    require(output.size() <= 8_000_000) { "Resposta maior que o limite esperado." }
                }
                output.toByteArray()
            }
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            validate(json)
            val checked = System.currentTimeMillis()
            save(url, JSONObject().put("url", url).put("checkedAt", checked).put("data", json)
                .put("etag", connection.getHeaderField("ETag") ?: "")
                .put("modified", connection.getHeaderField("Last-Modified") ?: ""))
            return JsonResponse(json, false, checked)
        } catch (error: Exception) {
            if (error !is SourceException) blocked[url] = System.currentTimeMillis() + 120_000L
            if (old != null) return old.copy(warning = "Sem atualização da fonte. Exibindo consulta salva.")
            throw error
        } finally { connection?.disconnect() }
    }
    private fun save(url: String, json: JSONObject) {
        val target = file(url)
        val temporary = File(directory, "${target.name}.tmp")
        temporary.writeText(json.toString())
        check(temporary.renameTo(target)) { "Não foi possível salvar a consulta." }
        val files = directory.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() } ?: return
        // At most 100 result/configuration documents; older results are evicted first.
        files.drop(100).forEach { it.delete() }
    }
    companion object {
        fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/** Downsampled portraits, bounded memory/disk use and negative caching for missing photographs. */
class Portraits(context: Context) {
    private val directory = File(context.cacheDir, "portraits").apply { mkdirs() }
    private val memory = object : LruCache<String, Bitmap>(4 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val misses = mutableMapOf<String, Long>()
    private var hostBlockedUntil = 0L
    fun load(selection: Selection, candidate: Candidate, view: ImageView) {
        if (!candidate.photoId.matches(Regex("[0-9]+"))) return
        val uf = if (selection.office.code == "1") "br" else selection.place.uf
        val url = "${Endpoints.BASE}${selection.election.cycle}/${selection.election.id}/fotos/$uf/${candidate.photoId}.jpeg"
        view.tag = url
        memory.get(url)?.let { view.setImageBitmap(it); return }
        executor.execute {
            if (System.currentTimeMillis() < hostBlockedUntil || (misses[url] ?: 0) > System.currentTimeMillis()) return@execute
            var connection: HttpURLConnection? = null
            try {
                val target = File(directory, Repository.digest(url))
                if (!target.isFile) {
                    Thread.sleep(500)
                    connection = URL(url).openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 10_000
                    val status = connection.responseCode
                    if (status != 200) {
                        if (status == 403 || status == 429) hostBlockedUntil = System.currentTimeMillis() + 610_000
                        throw IOException("Portrait unavailable")
                    }
                    val bytes = connection.inputStream.use { stream ->
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = stream.read(buffer); if (n < 0) break
                            out.write(buffer, 0, n)
                            require(out.size() <= 1_500_000)
                        }
                        out.toByteArray()
                    }
                    val tmp = File(directory, target.name + ".tmp"); tmp.writeBytes(bytes)
                    if (!tmp.renameTo(target)) tmp.delete()
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(target.path, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while (bounds.outWidth / inSampleSize > 256 || bounds.outHeight / inSampleSize > 320) inSampleSize *= 2
                }
                val bitmap = BitmapFactory.decodeFile(target.path, options) ?: throw IOException()
                memory.put(url, bitmap)
                handler.post { if (view.tag == url) view.setImageBitmap(bitmap) }
                var bytes = 0L
                directory.listFiles()?.sortedByDescending { it.lastModified() }?.forEach {
                    bytes += it.length(); if (bytes > 20_000_000) it.delete()
                }
            } catch (_: Exception) { misses[url] = System.currentTimeMillis() + 3_600_000 }
            finally { connection?.disconnect() }
        }
    }
    fun close() { executor.shutdownNow(); handler.removeCallbacksAndMessages(null) }
}
