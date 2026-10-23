package com.koala.cartes

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Card(
    val id: String,
    val storeId: String?,
    val storeName: String,
    val color: Long,
    val number: String,
    val format: String,
    val note: String,
    val created: Long,
    val uses: Int,
    val lastUsed: Long,
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("storeId", storeId ?: ""); put("storeName", storeName)
        put("color", color); put("number", number); put("format", format); put("note", note)
        put("created", created); put("uses", uses); put("lastUsed", lastUsed)
    }

    fun toDraft() = Draft(id, storeId, storeName, color, number, format, note, formatLocked = true)

    companion object {
        fun fromJson(o: JSONObject) = Card(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            storeId = o.optString("storeId").ifBlank { null },
            storeName = o.optString("storeName"),
            color = o.optLong("color", 0xFF0058A3),
            number = o.optString("number"),
            format = o.optString("format", "CODE_128"),
            note = o.optString("note"),
            created = o.optLong("created", System.currentTimeMillis()),
            uses = o.optInt("uses"),
            lastUsed = o.optLong("lastUsed"),
        )
    }
}

data class Draft(
    val id: String? = null,
    val storeId: String? = null,
    val storeName: String = "",
    val color: Long = 0xFF16213F,
    val number: String = "",
    val format: String = "CODE_128",
    val note: String = "",
    val formatLocked: Boolean = false,
    val autoStore: Boolean = false,
) {
    fun withStore(st: Store, auto: Boolean = false) = copy(storeId = st.id, storeName = st.name, color = st.color, autoStore = auto)
    fun withCustom(name: String, color: Long, auto: Boolean = false) = copy(storeId = null, storeName = name, color = color, autoStore = auto)
}

class CardRepo private constructor(ctx: Context) {
    private val file = File(ctx.filesDir, "cards.json")
    private val prefs = ctx.getSharedPreferences("learned", Context.MODE_PRIVATE)
    private val _cards = MutableStateFlow(load())
    val cards: StateFlow<List<Card>> = _cards

    private fun load(): List<Card> = try {
        if (!file.exists()) emptyList() else parse(file.readText())
    } catch (e: Exception) {
        emptyList()
    }

    private fun parse(txt: String): List<Card> {
        val t = txt.trim()
        val arr = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).getJSONArray("cards")
        return (0 until arr.length()).map { Card.fromJson(arr.getJSONObject(it)) }.filter { it.number.isNotBlank() }
    }

    private fun persist(list: List<Card>) {
        _cards.value = list
        val tmp = File(file.parentFile, "cards.json.tmp")
        tmp.writeText(serialize(list))
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }

    private fun serialize(list: List<Card>) = JSONObject().apply {
        put("app", "KoalaCartes"); put("version", 1)
        put("cards", JSONArray().apply { list.forEach { put(it.toJson()) } })
    }.toString(2)

    @Synchronized
    fun save(d: Draft): String {
        val list = _cards.value.toMutableList()
        val old = list.firstOrNull { it.id == d.id }
        val c = Card(
            id = old?.id ?: UUID.randomUUID().toString(),
            storeId = d.storeId, storeName = d.storeName.trim(), color = d.color,
            number = d.number.trim(), format = d.format, note = d.note.trim(),
            created = old?.created ?: System.currentTimeMillis(),
            uses = old?.uses ?: 0, lastUsed = old?.lastUsed ?: 0,
        )
        if (old != null) list[list.indexOf(old)] = c else list.add(c)
        persist(list)
        learn(c)
        return c.id
    }

    @Synchronized
    fun delete(id: String) = persist(_cards.value.filter { it.id != id })

    @Synchronized
    fun markUsed(id: String) = persist(_cards.value.map {
        if (it.id == id) it.copy(uses = it.uses + 1, lastUsed = System.currentTimeMillis()) else it
    })

    fun exportJson(): String = serialize(_cards.value)

    /** Fusionne une sauvegarde. Renvoie le nombre de cartes ajoutées. */
    @Synchronized
    fun importJson(txt: String): Int {
        val incoming = parse(txt)
        val list = _cards.value.toMutableList()
        var added = 0
        for (c in incoming) {
            val same = list.indexOfFirst {
                it.id == c.id || (it.number == c.number && Stores.norm(it.storeName) == Stores.norm(c.storeName))
            }
            if (same >= 0) list[same] = c.copy(id = list[same].id) else { list.add(c); added++ }
        }
        persist(list)
        incoming.forEach { learn(it) }
        return added
    }

    // Mémorise le début du numéro pour reconnaître le magasin à la prochaine carte du même type
    private fun prefix(number: String): String? {
        val n = number.trim()
        return if (n.length >= 8 && n.all { it.isDigit() }) n.take(6) else null
    }

    private fun learn(c: Card) {
        val p = prefix(c.number) ?: return
        prefs.edit().putString(p, JSONObject().apply {
            put("storeId", c.storeId ?: ""); put("name", c.storeName); put("color", c.color)
        }.toString()).apply()
    }

    fun applyLearned(d: Draft): Draft {
        val p = prefix(d.number) ?: return d
        val raw = prefs.getString(p, null) ?: return d
        return try {
            val o = JSONObject(raw)
            val st = Stores.byId(o.optString("storeId").ifBlank { null })
            if (st != null) d.withStore(st, auto = true)
            else d.withCustom(o.getString("name"), o.optLong("color", Stores.colorFor(o.getString("name"))), auto = true)
        } catch (e: Exception) {
            d
        }
    }

    companion object {
        @Volatile private var inst: CardRepo? = null
        fun get(ctx: Context) = inst ?: synchronized(this) { inst ?: CardRepo(ctx.applicationContext).also { inst = it } }
    }
}
