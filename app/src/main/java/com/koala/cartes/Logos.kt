package com.koala.cartes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

/** Un logo prêt à afficher, avec la couleur de fond qui le prolonge sur toute la tuile. */
data class Logo(val image: ImageBitmap, val bg: Color, val transparent: Boolean, val ratio: Float)

/**
 * Logos des enseignes : téléchargés une fois (appli officielle sur le Play Store, sinon site du magasin),
 * puis gardés sur le téléphone. Une image choisie dans la galerie peut remplacer celui d'une carte.
 */
object Logos {
    var version by mutableIntStateOf(0)
        private set
    private val tried = Collections.synchronizedSet(mutableSetOf<String>())
    private val cache = Collections.synchronizedMap(HashMap<String, Logo?>())
    private const val GOOD = 96
    private const val UA =
        "Mozilla/5.0 (Linux; Android 15; SM-S931B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    private fun dir(ctx: Context) = File(ctx.filesDir, "logos")
    fun storeFile(ctx: Context, storeId: String) = File(dir(ctx), "store_$storeId.png")
    private fun cardFile(ctx: Context, cardId: String) = File(dir(ctx), "$cardId.png")
    private fun customMark(ctx: Context, cardId: String) = File(dir(ctx), "$cardId.custom")

    /** Fichier du logo à afficher : image perso de la carte, sinon logo de l'enseigne, sinon logo du site saisi. */
    fun fileFor(ctx: Context, cardId: String?, storeId: String?): File? = when {
        cardId != null && customMark(ctx, cardId).exists() -> cardFile(ctx, cardId)
        storeId != null -> storeFile(ctx, storeId)
        cardId != null -> cardFile(ctx, cardId)
        else -> null
    }

    fun load(f: File?): Logo? {
        if (f == null || !f.exists()) return null
        val key = "${f.path}:${f.lastModified()}"
        cache[key]?.let { return it }
        val bmp = BitmapFactory.decodeFile(f.path) ?: return null
        return analyze(bmp).also { cache[key] = it }
    }

    /** Couleur du bord du logo (hors coins) : sert de fond à la tuile pour que le logo s'y fonde. */
    fun analyze(bmp: Bitmap): Logo {
        val w = bmp.width; val h = bmp.height
        val inset = maxOf(1, minOf(w, h) / 25)
        val rs = ArrayList<Int>(); val gs = ArrayList<Int>(); val bs = ArrayList<Int>()
        var clear = 0; var total = 0
        fun sample(x: Int, y: Int) {
            val p = bmp.getPixel(x.coerceIn(0, w - 1), y.coerceIn(0, h - 1))
            total++
            if ((p ushr 24) < 128) { clear++; return }
            rs += (p shr 16) and 0xFF; gs += (p shr 8) and 0xFF; bs += p and 0xFF
        }
        for (i in 0 until 24) {
            val fx = 0.2f + 0.6f * i / 23f
            sample((w * fx).toInt(), inset); sample((w * fx).toInt(), h - 1 - inset)
            sample(inset, (h * fx).toInt()); sample(w - 1 - inset, (h * fx).toInt())
        }
        val transparent = clear * 2 > total || rs.isEmpty()
        val bg = if (transparent) Color.White else {
            rs.sort(); gs.sort(); bs.sort()
            Color(rs[rs.size / 2], gs[gs.size / 2], bs[bs.size / 2])
        }
        return Logo(bmp.asImageBitmap(), bg, transparent, w.toFloat() / h)
    }

    private fun width(f: File): Int {
        if (!f.exists()) return 0
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        return minOf(o.outWidth, o.outHeight)
    }

    private suspend fun bump() = withContext(Dispatchers.Main) { version++ }

    /** Supprime le logo propre à une carte (image perso ou site saisi). */
    fun delete(ctx: Context, cardId: String) {
        cardFile(ctx, cardId).delete(); customMark(ctx, cardId).delete(); tried.remove(cardId); version++
    }

    /** Empêche le téléchargement automatique (une image perso arrive). */
    fun reserve(cardId: String) { tried.add(cardId) }

    private fun save(f: File, bmp: Bitmap) {
        f.parentFile?.mkdirs()
        val m = maxOf(bmp.width, bmp.height)
        val out = if (m > 256) Bitmap.createScaledBitmap(bmp, bmp.width * 256 / m, bmp.height * 256 / m, true) else bmp
        val tmp = File(f.parentFile, f.name + ".tmp")
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

    suspend fun importImage(ctx: Context, cardId: String, uri: Uri): Boolean {
        tried.add(cardId)
        val ok = withContext(Dispatchers.IO) {
            val b = decodeUri(ctx, uri, 512) ?: return@withContext false
            runCatching { save(cardFile(ctx, cardId), b); customMark(ctx, cardId).createNewFile() }.isSuccess
        }
        version++
        return ok
    }

    /**
     * Logos de toutes les enseignes de la liste, pour qu'ils soient visibles dès le choix du magasin.
     * Celles déjà utilisées par une carte passent en premier. 4 téléchargements en parallèle.
     */
    suspend fun fetchStores(ctx: Context, cards: List<Card>) = withContext(Dispatchers.IO) {
        // Anciennes versions : logo copié par carte, remplacé par le logo commun de l'enseigne
        cards.filter { it.storeId != null && !customMark(ctx, it.id).exists() }.forEach { cardFile(ctx, it.id).delete() }
        val used = cards.mapNotNull { it.storeId }.toSet()
        val order = Stores.all.sortedBy { if (it.id in used) 0 else 1 }
        val gate = Semaphore(4)
        coroutineScope {
            for (st in order) {
                val key = "store:${st.id}"
                val f = storeFile(ctx, st.id)
                if (key in tried || width(f) >= GOOD) continue
                tried.add(key)
                launch {
                    gate.withPermit {
                        val bmp = runCatching { download(st.domain, st.play) }.getOrNull()
                        if (bmp != null && minOf(bmp.width, bmp.height) > width(f) && runCatching { save(f, bmp) }.isSuccess) bump()
                    }
                }
            }
        }
    }

    /** Logos des magasins hors liste pour lesquels un site a été saisi. */
    suspend fun fetchCustom(ctx: Context, cards: List<Card>) {
        for (c in cards) {
            if (c.storeId != null || c.domain.isBlank() || c.id in tried) continue
            if (customMark(ctx, c.id).exists()) continue
            val f = cardFile(ctx, c.id)
            if (width(f) >= GOOD) continue
            tried.add(c.id)
            val ok = withContext(Dispatchers.IO) {
                val bmp = runCatching { download(c.domain, null) }.getOrNull()
                bmp != null && minOf(bmp.width, bmp.height) > width(f) && runCatching { save(f, bmp) }.isSuccess
            }
            if (ok) version++
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
        val page = get("https://play.google.com/store/apps/details?id=$pkg&hl=fr&gl=FR", 3_000_000) ?: return null
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
