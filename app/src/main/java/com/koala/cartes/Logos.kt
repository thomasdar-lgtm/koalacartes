package com.koala.cartes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

/**
 * Logos des enseignes : téléchargés une fois depuis le site du magasin (icône publiée par le site),
 * puis gardés sur le téléphone. Une image choisie dans la galerie peut les remplacer.
 */
object Logos {
    var version by mutableIntStateOf(0)
        private set
    private val tried = Collections.synchronizedSet(mutableSetOf<String>())
    private const val UA =
        "Mozilla/5.0 (Linux; Android 15; SM-S931B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    fun file(ctx: Context, id: String) = File(ctx.filesDir, "logos/$id.png")

    fun load(ctx: Context, id: String): ImageBitmap? {
        val f = file(ctx, id)
        return if (f.exists()) BitmapFactory.decodeFile(f.path)?.asImageBitmap() else null
    }

    fun delete(ctx: Context, id: String) {
        file(ctx, id).delete(); tried.remove(id); version++
    }

    /** Empêche le téléchargement automatique (une image perso arrive). */
    fun reserve(id: String) { tried.add(id) }

    private fun save(ctx: Context, id: String, bmp: Bitmap) {
        val f = file(ctx, id)
        f.parentFile?.mkdirs()
        val m = maxOf(bmp.width, bmp.height)
        val out = if (m > 256) Bitmap.createScaledBitmap(bmp, bmp.width * 256 / m, bmp.height * 256 / m, true) else bmp
        val tmp = File(f.parentFile, "$id.tmp")
        tmp.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        tmp.renameTo(f)
    }

    fun decodeUri(ctx: Context, uri: Uri, max: Int = 256): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= max) sample *= 2
        ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (e: Exception) {
        null
    }

    suspend fun importImage(ctx: Context, id: String, uri: Uri): Boolean {
        tried.add(id)
        val ok = withContext(Dispatchers.IO) {
            val b = decodeUri(ctx, uri, 512) ?: return@withContext false
            runCatching { save(ctx, id, b) }.isSuccess
        }
        version++
        return ok
    }

    /** Télécharge les logos manquants (une tentative par carte et par lancement). */
    suspend fun fetchMissing(ctx: Context, cards: List<Card>) {
        for (c in cards) {
            if (c.id in tried || file(ctx, c.id).exists()) continue
            val domain = c.logoDomain() ?: continue
            val done = withContext(Dispatchers.IO) {
                // Même magasin déjà téléchargé pour une autre carte : on recopie
                val twin = cards.firstOrNull { it.id != c.id && it.logoDomain() == domain && file(ctx, it.id).exists() }
                if (twin != null) {
                    runCatching { file(ctx, twin.id).copyTo(file(ctx, c.id), overwrite = true) }.isSuccess
                } else {
                    val bmp = runCatching { download(domain) }.getOrNull()
                    bmp != null && runCatching { save(ctx, c.id, bmp) }.isSuccess
                }
            }
            tried.add(c.id)
            if (done) version++
        }
    }

    private fun readLimited(input: InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < max) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun get(url: String, max: Int): Pair<ByteArray, URL>? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 10000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9")
        return try {
            if (c.responseCode !in 200..299) null
            else c.inputStream.use { readLimited(it, max) } to c.url
        } catch (e: Exception) {
            null
        } finally {
            c.disconnect()
        }
    }

    private fun attr(tag: String, name: String): String? {
        val m = Regex("""\b$name\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE).find(tag) ?: return null
        return (m.groupValues[1].ifEmpty { m.groupValues[2] }.ifEmpty { m.groupValues[3] }).replace("&amp;", "&")
    }

    /** Icônes déclarées dans la page d'accueil, les plus grandes d'abord. */
    private fun icons(html: String, base: URL): List<String> {
        val found = mutableListOf<Pair<Int, String>>()
        for (m in Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html)) {
            val tag = m.value
            val rel = attr(tag, "rel")?.lowercase() ?: continue
            if (!rel.contains("icon") || rel.contains("mask")) continue
            val href = attr(tag, "href") ?: continue
            if (href.lowercase().substringBefore("?").endsWith(".svg")) continue
            val size = attr(tag, "sizes")?.lowercase()?.substringBefore("x")?.toIntOrNull()
                ?: if (rel.contains("apple")) 180 else 32
            val score = size + if (rel.contains("apple")) 1000 else 0
            runCatching { found.add(score to URL(base, href).toString()) }
        }
        return found.sortedByDescending { it.first }.map { it.second }
    }

    private fun download(domain: String): Bitmap? {
        val cands = mutableListOf<String>()
        val page = get("https://$domain/", 600_000) ?: get("https://www.$domain/", 600_000)
        if (page != null) cands += icons(String(page.first), page.second)
        cands += "https://$domain/apple-touch-icon.png"
        cands += "https://www.google.com/s2/favicons?domain=$domain&sz=128"
        var best: Bitmap? = null
        for (u in cands.distinct()) {
            val bytes = get(u, 3_000_000)?.first ?: continue
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            if (minOf(bmp.width, bmp.height) >= 96) return bmp
            if (best == null || bmp.width > best.width) best = bmp
        }
        return best?.takeIf { minOf(it.width, it.height) >= 32 }
    }
}
