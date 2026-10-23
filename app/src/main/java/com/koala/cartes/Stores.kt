package com.koala.cartes

import java.text.Normalizer
import kotlin.math.abs

data class Store(val id: String, val name: String, val color: Long, val keys: List<String>)

object Stores {
    private fun s(id: String, name: String, color: Long, vararg keys: String) =
        Store(id, name, color, if (keys.isEmpty()) listOf(name) else keys.toList())

    val all: List<Store> = listOf(
        // Alimentaire
        s("leclerc", "E.Leclerc", 0xFF005EB8, "leclerc", "e leclerc"),
        s("carrefour", "Carrefour", 0xFF0E4C96, "carrefour"),
        s("auchan", "Auchan", 0xFFE2001A, "auchan"),
        s("intermarche", "Intermarché", 0xFFD7001B, "intermarche", "les mousquetaires"),
        s("u", "Magasins U", 0xFFE30613, "super u", "hyper u", "u express", "magasins u", "cooperative u", "carte u"),
        s("lidl", "Lidl", 0xFF0050AA, "lidl"),
        s("aldi", "Aldi", 0xFF00005F, "aldi"),
        s("casino", "Casino", 0xFFC8102E, "casino", "geant casino"),
        s("monoprix", "Monoprix", 0xFFD50032, "monoprix"),
        s("franprix", "Franprix", 0xFFF39200, "franprix"),
        s("netto", "Netto", 0xFFE30613, "netto"),
        s("cora", "Cora", 0xFFE30613, "cora"),
        s("spar", "Spar", 0xFF00843D, "spar"),
        s("vival", "Vival", 0xFFE30613, "vival"),
        s("grandfrais", "Grand Frais", 0xFF2E7D32, "grand frais"),
        s("picard", "Picard", 0xFF1E4F9C, "picard"),
        s("biocoop", "Biocoop", 0xFF6AB023, "biocoop"),
        s("naturalia", "Naturalia", 0xFF78BE20, "naturalia"),
        // Maison, bricolage, jardin
        s("leroymerlin", "Leroy Merlin", 0xFF78BE20, "leroy merlin"),
        s("castorama", "Castorama", 0xFF0058A3, "castorama"),
        s("bricodepot", "Brico Dépôt", 0xFFF7A600, "brico depot"),
        s("bricomarche", "Bricomarché", 0xFFE30613, "bricomarche"),
        s("mrbricolage", "Mr.Bricolage", 0xFFE2001A, "mr bricolage", "mr.bricolage"),
        s("weldom", "Weldom", 0xFFEE7203, "weldom"),
        s("gammvert", "Gamm vert", 0xFF00973A, "gamm vert"),
        s("jardiland", "Jardiland", 0xFF00A651, "jardiland"),
        s("truffaut", "Truffaut", 0xFF00594F, "truffaut"),
        s("botanic", "Botanic", 0xFF6BA539, "botanic"),
        s("ikea", "IKEA", 0xFF0058A3, "ikea", "ikea family"),
        s("conforama", "Conforama", 0xFFE30613, "conforama"),
        s("but", "BUT", 0xFFE4032E, "but fr", "magasins but"),
        s("maisonsdumonde", "Maisons du Monde", 0xFF3D3D3D, "maisons du monde"),
        s("gifi", "Gifi", 0xFFE6007E, "gifi"),
        s("action", "Action", 0xFF0047AB, "action com", "magasins action"),
        s("centrakor", "Centrakor", 0xFFE5005B, "centrakor"),
        s("foirfouille", "La Foir'Fouille", 0xFFF5B400, "foir fouille", "foirfouille"),
        s("babou", "Babou", 0xFFE6007E, "babou"),
        s("stokomani", "Stokomani", 0xFFE30613, "stokomani"),
        // High-tech, culture, jouets
        s("darty", "Darty", 0xFFE2001A, "darty"),
        s("boulanger", "Boulanger", 0xFFF15A22, "boulanger"),
        s("fnac", "Fnac", 0xFFE1A925, "fnac"),
        s("cultura", "Cultura", 0xFF6D2077, "cultura"),
        s("electrodepot", "Electro Dépôt", 0xFFFFD100, "electro depot"),
        s("micromania", "Micromania", 0xFFE30613, "micromania"),
        s("kingjouet", "King Jouet", 0xFFE30613, "king jouet"),
        s("joueclub", "JouéClub", 0xFFED1C24, "joueclub", "joue club"),
        s("granderecre", "La Grande Récré", 0xFFE30613, "grande recre"),
        s("bureauvallee", "Bureau Vallée", 0xFFE30613, "bureau vallee"),
        // Sport, mode
        s("decathlon", "Decathlon", 0xFF0082C3, "decathlon"),
        s("intersport", "Intersport", 0xFF003DA5, "intersport"),
        s("gosport", "Go Sport", 0xFFE30613, "go sport"),
        s("sport2000", "Sport 2000", 0xFFE2001A, "sport 2000"),
        s("kiabi", "Kiabi", 0xFF0E3F7E, "kiabi"),
        s("hm", "H&M", 0xFFE50010, "h&m", "h & m"),
        s("zara", "Zara", 0xFF111111, "zara"),
        s("celio", "Celio", 0xFFE30613, "celio"),
        s("jules", "Jules", 0xFF1A1A1A, "jules"),
        s("lahalle", "La Halle", 0xFFE30613, "la halle"),
        s("gemo", "Gémo", 0xFFE6007E, "gemo"),
        s("chaussea", "Chaussea", 0xFFE30613, "chaussea"),
        s("okaidi", "Okaïdi", 0xFFE5007D, "okaidi"),
        // Beauté
        s("sephora", "Sephora", 0xFF111111, "sephora"),
        s("nocibe", "Nocibé", 0xFFE4007C, "nocibe"),
        s("marionnaud", "Marionnaud", 0xFF8B1E3F, "marionnaud"),
        s("yvesrocher", "Yves Rocher", 0xFF00754A, "yves rocher"),
        // Animaux, divers
        s("maxizoo", "Maxi Zoo", 0xFF003DA5, "maxi zoo"),
        s("animalis", "Animalis", 0xFFF39200, "animalis"),
        s("totalenergies", "TotalEnergies", 0xFFE4002B, "totalenergies", "total energies"),
        s("flunch", "Flunch", 0xFFE30613, "flunch"),
        s("starbucks", "Starbucks", 0xFF00704A, "starbucks"),
        s("pathe", "Pathé", 0xFFFFC20E, "pathe"),
        s("cgr", "CGR Cinémas", 0xFFE30613, "cgr"),
    )

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
