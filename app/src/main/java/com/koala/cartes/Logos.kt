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

    private fun customMark(ctx: Context, id: String) = File(ctx.filesDir, "logos/$id.custom")

    private fun width(f: File): Int {
        if (!f.exists()) return 0
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        return minOf(o.outWidth, o.outHeight)
    }

    /** Logo à (re)chercher : absent, ou trop petit et pas choisi à la main. */
    private fun needs(ctx: Context, id: String): Boolean {
        val f = file(ctx, id)
        return !f.exists() || (!customMark(ctx, id).exists() && width(f) < GOOD)
    }

    private const val GOOD = 96

    fun delete(ctx: Context, id: String) {
        file(ctx, id).delete(); customMark(ctx, id).delete(); tried.remove(id); version++
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
            runCatching { save(ctx, id, b); customMark(ctx, id).createNewFile() }.isSuccess
        }
        version++
        return ok
    }

    /** Télécharge les logos manquants ou flous (une tentative par carte et par lancement). */
    suspend fun fetchMissing(ctx: Context, cards: List<Card>) {
        for (c in cards) {
            if (c.id in tried || !needs(ctx, c.id)) continue
            val domain = c.logoDomain() ?: continue
            val play = Stores.byId(c.storeId)?.play
            val done = withContext(Dispatchers.IO) {
                val current = width(file(ctx, c.id))
                // Même magasin déjà bien téléchargé pour une autre carte : on recopie
                val twin = cards.firstOrNull {
                    it.id != c.id && it.logoDomain() == domain && !customMark(ctx, it.id).exists() && width(file(ctx, it.id)) >= GOOD
                }
                if (twin != null) {
                    runCatching { file(ctx, twin.id).copyTo(file(ctx, c.id), overwrite = true) }.isSuccess
                } else {
                    val bmp = runCatching { download(domain, play) }.getOrNull()
                    bmp != null && minOf(bmp.width, bmp.height) > current && runCatching { save(ctx, c.id, bmp) }.isSuccess
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

    /** Icônes listées dans le manifeste d'appli web du site (souvent 192 et 512 px). */
    private fun manifestIcons(html: String, base: URL): List<String> {
        val tag = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).map { it.value }
            .firstOrNull { attr(it, "rel")?.lowercase()?.split(" ")?.contains("manifest") == true } ?: return emptyList()
        val href = attr(tag, "href") ?: return emptyList()
        val murl = runCatching { URL(base, href) }.getOrNull() ?: return emptyList()
        val body = get(murl.toString(), 200_000) ?: return emptyList()
        return try {
            val arr = org.json.JSONObject(String(body.first)).optJSONArray("icons") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val src = o.optString("src").ifBlank { return@mapNotNull null }
                if (src.lowercase().substringBefore("?").endsWith(".svg")) return@mapNotNull null
                val size = o.optString("sizes").lowercase().substringBefore("x").toIntOrNull() ?: 0
                size to URL(body.second, src).toString()
            }.sortedByDescending { it.first }.map { it.second }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Icône de l'appli officielle de l'enseigne sur le Play Store (256 px). */
    private fun playIcon(pkg: String): String? {
        val page = get("https://play.google.com/store/apps/details?id=$pkg&hl=fr", 3_000_000) ?: return null
        val html = String(page.first)
        val m = Regex("""<meta[^>]+property="og:image"[^>]+content="([^"]+)"""").find(html) ?: return null
        return m.groupValues[1].replace("&amp;", "&").replace(Regex("=[a-z0-9-]+$"), "") + "=s256"
    }

    private fun download(domain: String, play: String?): Bitmap? {
        val hosts = if (domain.startsWith("www.")) listOf(domain) else listOf(domain, "www.$domain")
        val cands = linkedSetOf<String>()
        play?.let { p -> runCatching { playIcon(p) }.getOrNull()?.let { cands += it } }
        for (h in hosts) {
            val page = get("https://$h/", 600_000) ?: continue
            val html = String(page.first)
            cands += manifestIcons(html, page.second)
            cands += icons(html, page.second)
            break
        }
        for (h in hosts) {
            cands += "https://$h/apple-touch-icon.png"
            cands += "https://$h/apple-touch-icon-precomposed.png"
        }
        // Copies des icônes gardées par Google et DuckDuckGo, utiles quand le site bloque les applis
        for (h in hosts) {
            cands += "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://$h&size=256"
        }
        cands += "https://www.google.com/s2/favicons?domain=$domain&sz=256"
        cands += "https://icons.duckduckgo.com/ip3/$domain.ico"

        var best: Bitmap? = null
        for (u in cands) {
            val bytes = get(u, 3_000_000)?.first ?: continue
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            val m = minOf(bmp.width, bmp.height)
            if (m >= 180) return bmp
            if (best == null || m > minOf(best.width, best.height)) best = bmp
        }
        return best?.takeIf { minOf(it.width, it.height) >= 32 }
    }
}
