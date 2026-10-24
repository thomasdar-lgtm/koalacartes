package com.koala.cartes

import android.content.Context
import org.json.JSONArray
import java.text.Normalizer
import kotlin.math.abs

data class Store(
    val id: String, val name: String, val color: Long, val domain: String, val keys: List<String>,
    val play: String? = null, val ios: Long? = null,
)

/**
 * Catalogue des enseignes : app/src/main/assets/stores.json, construit par tools/catalog/build.py
 * (liste maison + enseignes présentes en France d'après OpenStreetMap).
 */
object Stores {
    @Volatile var all: List<Store> = emptyList()
        private set

    fun load(ctx: Context) {
        if (all.isNotEmpty()) return
        all = runCatching {
            val arr = JSONArray(ctx.assets.open("stores.json").bufferedReader().use { it.readText() })
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val name = o.getString("name")
                val keysArr = o.optJSONArray("keys")
                val keys = if (keysArr == null) emptyList() else (0 until keysArr.length()).map { keysArr.getString(it) }
                val col = o.optString("color").removePrefix("#")
                Store(
                    id = o.getString("id"), name = name,
                    color = if (col.length == 6) 0xFF000000 or col.toLong(16) else colorFor(name),
                    domain = o.optString("domain"), keys = keys,
                    play = o.optString("play").ifBlank { null },
                    ios = o.optLong("ios").takeIf { it > 0 },
                )
            }
        }.getOrDefault(emptyList())
    }

    val palette = listOf(
        0xFFE3000F, 0xFF0058A3, 0xFF00973A, 0xFFF39200, 0xFF6D2077,
        0xFF00A0DF, 0xFFE6007E, 0xFF3D3D3D, 0xFF8B1E3F, 0xFF00594F,
    )

    fun byId(id: String?): Store? = all.firstOrNull { it.id == id }

    fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9&]+"), " ")
            .trim()

    /** Cherche un nom de magasin dans un texte lu par l'appareil photo. */
    fun detect(text: String): String? {
        if (text.isBlank()) return null
        val t = " " + norm(text) + " "
        var best: Store? = null
        var bestLen = 0
        for (st in all) for (k in st.keys) {
            val nk = norm(k)
            if (nk.length > bestLen && t.contains(" $nk ")) { best = st; bestLen = nk.length }
        }
        return best?.id
    }

    fun search(q: String): List<Store> {
        val sorted = all.sortedBy { norm(it.name) }
        if (q.isBlank()) return sorted
        val n = norm(q)
        return sorted.filter { st -> norm(st.name).contains(n) || st.keys.any { norm(it).contains(n) } }
    }

    fun cleanDomain(s: String): String = s.trim().lowercase()
        .removePrefix("https://").removePrefix("http://").removePrefix("www.")
        .substringBefore("/").substringBefore("?").trim()

    fun colorFor(name: String): Long = palette[abs(norm(name).hashCode()) % palette.size]

    fun initials(name: String): String {
        val words = norm(name).split(" ").filter { it.isNotBlank() }
        return when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].take(2)
            else -> "${words[0][0]}${words[1][0]}"
        }.uppercase()
    }
}
