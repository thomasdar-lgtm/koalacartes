package com.koala.cartes

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.Inflater

data class Card(
    val id: String,
    val profile: String,
    val storeId: String?,
    val storeName: String,
    val color: Long,
    val number: String,
    val format: String,
    val note: String,
    val domain: String,
    val created: Long,
    val uses: Int,
    val lastUsed: Long,
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("profile", profile); put("storeId", storeId ?: ""); put("storeName", storeName)
        put("color", color); put("number", number); put("format", format); put("note", note)
        put("domain", domain); put("created", created); put("uses", uses); put("lastUsed", lastUsed)
    }

    fun toDraft() = Draft(id, storeId, storeName, color, number, format, note, domain = domain, profile = profile, formatLocked = true)

    /** Site d'où télécharger le logo. */
    fun logoDomain(): String? = Stores.byId(storeId)?.domain ?: domain.ifBlank { null }

    companion object {
        fun fromJson(o: JSONObject, defaultProfile: String) = Card(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            profile = o.optString("profile").ifBlank { defaultProfile },
            storeId = o.optString("storeId").ifBlank { null },
            storeName = o.optString("storeName"),
            color = o.optLong("color", 0xFF0058A3),
            number = o.optString("number"),
            format = o.optString("format", "CODE_128"),
            note = o.optString("note"),
            domain = o.optString("domain"),
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
    val domain: String = "",
    val logoUri: String? = null,
    val profile: String? = null,
    val formatLocked: Boolean = false,
    val autoStore: Boolean = false,
) {
    fun withStore(st: Store, auto: Boolean = false) = copy(storeId = st.id, storeName = st.name, color = st.color, domain = "", autoStore = auto)
    fun withCustom(name: String, color: Long, auto: Boolean = false) = copy(storeId = null, storeName = name, color = color, autoStore = auto)
}

/** Un profil = les cartes d'une personne (Thomas, Cindy…). Tout reste sur le téléphone. */
data class Profile(val id: String, val name: String, val color: Long) {
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"
}

data class WalletState(val profiles: List<Profile>, val current: String, val cards: List<Card>) {
    val profile: Profile get() = profiles.firstOrNull { it.id == current } ?: profiles.first()
    val currentCards: List<Card> get() = cards.filter { it.profile == profile.id }
    val needsName: Boolean get() = profiles.any { it.name.isBlank() }
}

/** Résultat d'un import par QR code ou fichier. */
data class ImportResult(val profileName: String, val profileId: String, val added: Int, val updated: Int)

class CardRepo internal constructor(ctx: Context, fileName: String = "cards.json") {
    private val file = File(ctx.filesDir, fileName)
    private val prefs = ctx.getSharedPreferences("learned", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<WalletState> = _state

    // ---------- Lecture / écriture ----------

    private fun load(): WalletState = try {
        if (!file.exists()) fresh() else parseState(file.readText())
    } catch (e: Exception) {
        fresh()
    }

    private fun fresh() = WalletState(listOf(Profile("me", "", PROFILE_COLORS[0])), "me", emptyList())

    /** Lit le fichier de l'appli (version 2) ou une ancienne version sans profils. */
    private fun parseState(txt: String): WalletState {
        val t = txt.trim()
        if (t.startsWith("[")) return fresh().copy(cards = parseCards(JSONArray(t), "me"))
        val o = JSONObject(t)
        val pa = o.optJSONArray("profiles")
        if (pa == null || pa.length() == 0) return fresh().copy(cards = parseCards(o.getJSONArray("cards"), "me"))
        val profiles = (0 until pa.length()).map {
            val p = pa.getJSONObject(it)
            Profile(p.getString("id"), p.optString("name"), p.optLong("color", PROFILE_COLORS[0]))
        }
        val current = o.optString("current").takeIf { c -> profiles.any { it.id == c } } ?: profiles.first().id
        return WalletState(profiles, current, parseCards(o.getJSONArray("cards"), profiles.first().id))
    }

    private fun parseCards(arr: JSONArray, defaultProfile: String) =
        (0 until arr.length()).map { Card.fromJson(arr.getJSONObject(it), defaultProfile) }.filter { it.number.isNotBlank() }

    private fun serialize(s: WalletState) = JSONObject().apply {
        put("app", "KoalaCartes"); put("version", 2); put("current", s.current)
        put("profiles", JSONArray().apply {
            s.profiles.forEach { p -> put(JSONObject().apply { put("id", p.id); put("name", p.name); put("color", p.color) }) }
        })
        put("cards", JSONArray().apply { s.cards.forEach { put(it.toJson()) } })
    }.toString(2)

    private fun persist(s: WalletState) {
        _state.value = s
        val tmp = File(file.parentFile, "cards.json.tmp")
        tmp.writeText(serialize(s))
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }

    private fun update(f: (WalletState) -> WalletState) = synchronized(this) { persist(f(_state.value)) }

    // ---------- Profils ----------

    /** Appui sur la pastille : passe au profil suivant. */
    fun cycleProfile() = update { s ->
        val i = s.profiles.indexOfFirst { it.id == s.current }
        s.copy(current = s.profiles[(i + 1) % s.profiles.size].id)
    }

    fun setCurrent(id: String) = update { s -> if (s.profiles.any { it.id == id }) s.copy(current = id) else s }

    fun addProfile(name: String): String {
        val id = UUID.randomUUID().toString()
        update { s ->
            val color = PROFILE_COLORS.firstOrNull { c -> s.profiles.none { it.color == c } } ?: PROFILE_COLORS[s.profiles.size % PROFILE_COLORS.size]
            s.copy(profiles = s.profiles + Profile(id, name.trim(), color))
        }
        return id
    }

    fun editProfile(id: String, name: String, color: Long) = update { s ->
        s.copy(profiles = s.profiles.map { if (it.id == id) it.copy(name = name.trim(), color = color) else it })
    }

    /** Supprime un profil et ses cartes (jamais le dernier). Renvoie les cartes supprimées. */
    fun deleteProfile(id: String): List<Card> {
        var removed = emptyList<Card>()
        update { s ->
            if (s.profiles.size <= 1) return@update s
            removed = s.cards.filter { it.profile == id }
            val left = s.profiles.filter { it.id != id }
            s.copy(profiles = left, current = if (s.current == id) left.first().id else s.current, cards = s.cards.filter { it.profile != id })
        }
        return removed
    }

    private fun profileByName(s: WalletState, name: String): Profile? =
        s.profiles.firstOrNull { Stores.norm(it.name) == Stores.norm(name) && name.isNotBlank() }

    // ---------- Cartes ----------

    fun save(d: Draft): String {
        var id = ""
        update { s ->
            val list = s.cards.toMutableList()
            val old = list.firstOrNull { it.id == d.id }
            val c = Card(
                id = old?.id ?: UUID.randomUUID().toString(),
                profile = old?.profile ?: d.profile ?: s.profile.id,
                storeId = d.storeId, storeName = d.storeName.trim(), color = d.color,
                number = d.number.trim(), format = d.format, note = d.note.trim(),
                domain = if (d.storeId == null) Stores.cleanDomain(d.domain) else "",
                created = old?.created ?: System.currentTimeMillis(),
                uses = old?.uses ?: 0, lastUsed = old?.lastUsed ?: 0,
            )
            if (old != null) list[list.indexOf(old)] = c else list.add(c)
            learn(c)
            id = c.id
            s.copy(cards = list)
        }
        return id
    }

    fun delete(id: String) = update { s -> s.copy(cards = s.cards.filter { it.id != id }) }

    fun markUsed(id: String) = update { s ->
        s.copy(cards = s.cards.map { if (it.id == id) it.copy(uses = it.uses + 1, lastUsed = System.currentTimeMillis()) else it })
    }

    /** Ajoute ou met à jour des cartes dans un profil, sans doublons (même magasin + même numéro). */
    private fun merge(s: WalletState, profileId: String, incoming: List<Card>): Triple<WalletState, Int, Int> {
        val list = s.cards.toMutableList()
        var added = 0; var updated = 0
        for (c0 in incoming) {
            val c = c0.copy(profile = profileId)
            val same = list.indexOfFirst {
                it.profile == profileId && it.number == c.number && Stores.norm(it.storeName) == Stores.norm(c.storeName)
            }
            if (same >= 0) {
                val o = list[same]
                list[same] = c.copy(id = o.id, uses = o.uses, lastUsed = o.lastUsed, created = o.created); updated++
            } else {
                list.add(c.copy(id = UUID.randomUUID().toString(), uses = 0, lastUsed = 0)); added++
            }
            learn(c)
        }
        return Triple(s.copy(cards = list), added, updated)
    }

    // ---------- Sauvegarde fichier ----------

    fun exportJson(): String = serialize(_state.value)

    /**
     * Restaure une sauvegarde. Les profils sont retrouvés par prénom (créés s'ils manquent) ;
     * une ancienne sauvegarde sans profils va dans le profil affiché. Renvoie le nombre de cartes ajoutées.
     */
    fun importJson(txt: String): Int {
        val backup = parseState(txt)
        val legacy = !JSONObject(txt.trim().let { if (it.startsWith("[")) "{\"cards\":$it}" else it }).has("profiles")
        var added = 0
        update { s0 ->
            var s = s0
            for (bp in backup.profiles) {
                val cards = backup.cards.filter { it.profile == bp.id }
                if (cards.isEmpty() && !legacy) continue
                val target = when {
                    legacy -> s.profile
                    else -> profileByName(s, bp.name) ?: Profile(UUID.randomUUID().toString(), bp.name, bp.color).also {
                        s = s.copy(profiles = s.profiles + it)
                    }
                }
                val r = merge(s, target.id, cards)
                s = r.first; added += r.second
            }
            s
        }
        return added
    }

    // ---------- Partage par QR code ----------

    /** Contenu du QR : prénom + cartes, compressé. Null si trop gros pour un QR. */
    fun sharePayload(profile: Profile, cards: List<Card>): String? {
        val o = JSONObject().apply {
            put("n", profile.name); put("k", profile.color)
            put("c", JSONArray().apply {
                cards.forEach { c ->
                    put(JSONObject().apply {
                        c.storeId?.let { put("s", it) }
                        put("m", c.storeName); put("u", c.number)
                        if (c.storeId == null) put("o", c.color)
                        if (c.format != "CODE_128") put("f", c.format)
                        if (c.note.isNotBlank()) put("t", c.note)
                        if (c.domain.isNotBlank()) put("d", c.domain)
                    })
                }
            })
        }
        val raw = o.toString().toByteArray(Charsets.UTF_8)
        val def = Deflater(Deflater.BEST_COMPRESSION).apply { setInput(raw); finish() }
        val out = ByteArrayOutputStream(); val buf = ByteArray(4096)
        while (!def.finished()) out.write(buf, 0, def.deflate(buf))
        def.end()
        val payload = SHARE_PREFIX + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
        return payload.takeIf { it.length <= SHARE_MAX }
    }

    /** Importe un QR KoalaCartes dans le profil du prénom qu'il contient (créé si besoin). */
    fun importShare(payload: String): ImportResult? = runCatching {
        val bytes = Base64.decode(payload.removePrefix(SHARE_PREFIX), Base64.URL_SAFE)
        val inf = Inflater().apply { setInput(bytes) }
        val out = ByteArrayOutputStream(); val buf = ByteArray(4096)
        while (!inf.finished()) { val n = inf.inflate(buf); if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break; out.write(buf, 0, n) }
        inf.end()
        val o = JSONObject(out.toString(Charsets.UTF_8.name()))
        val name = o.optString("n").ifBlank { "Partagé" }
        val arr = o.getJSONArray("c")
        val now = System.currentTimeMillis()
        val cards = (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            val st = Stores.byId(c.optString("s").ifBlank { null })
            Card(
                id = "", profile = "", storeId = st?.id, storeName = c.optString("m").ifBlank { st?.name ?: "?" },
                color = st?.color ?: c.optLong("o", Stores.colorFor(c.optString("m"))),
                number = c.getString("u"), format = c.optString("f", "CODE_128"), note = c.optString("t"),
                domain = if (st == null) c.optString("d") else "", created = now, uses = 0, lastUsed = 0,
            )
        }.filter { it.number.isNotBlank() }
        var result: ImportResult? = null
        update { s0 ->
            var s = s0
            val target = profileByName(s, name) ?: Profile(UUID.randomUUID().toString(), name, o.optLong("k", PROFILE_COLORS[1])).let { p ->
                // Couleur différente des profils existants
                val col = if (s.profiles.any { it.color == p.color }) PROFILE_COLORS.firstOrNull { c -> s.profiles.none { it.color == c } } ?: p.color else p.color
                p.copy(color = col).also { s = s.copy(profiles = s.profiles + it) }
            }
            val (s2, added, updated) = merge(s, target.id, cards)
            result = ImportResult(target.name, target.id, added, updated)
            s2.copy(current = target.id)
        }
        result
    }.getOrNull()

    // ---------- Reconnaissance du magasin par le début du numéro ----------

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
        const val SHARE_PREFIX = "KOALACARTES1:"
        /** Au-delà, le QR devient trop dense pour être lu de façon fiable d'un écran à l'autre. */
        const val SHARE_MAX = 1200
        val PROFILE_COLORS = listOf(0xFFFFCC00, 0xFFE6007E, 0xFF00A0DF, 0xFF00973A, 0xFFF39200, 0xFF8E44AD)

        @Volatile private var inst: CardRepo? = null
        fun get(ctx: Context) = inst ?: synchronized(this) { inst ?: CardRepo(ctx.applicationContext).also { inst = it } }
    }
}
