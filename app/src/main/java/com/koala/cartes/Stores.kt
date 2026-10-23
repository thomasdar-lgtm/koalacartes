package com.koala.cartes

import java.text.Normalizer
import kotlin.math.abs

data class Store(val id: String, val name: String, val color: Long, val domain: String, val keys: List<String>, val play: String? = null)

object Stores {
    private fun s(id: String, name: String, color: Long, domain: String, vararg keys: String) =
        Store(id, name, color, domain, if (keys.isEmpty()) listOf(name) else keys.toList())

    // Appli officielle sur le Play Store : icône nette quand le site n'en publie qu'une petite
    private val PLAY = mapOf(
        "leclerc" to "com.infomil.bill",
        "intermarche" to "fr.stime.mcommerce",
        "carrefour" to "com.carrefour.fid.android",
        "auchan" to "com.auchan.android",
        "aldi" to "fr.aldi.android",
        "casino" to "fr.casino.fidelite",
        "netto" to "com.mousquetaires.netto",
        "grandfrais" to "com.grandfrais.app",
        "picard" to "com.picard.catalogue",
        "naturalia" to "com.naturalia.mob",
        "leroymerlin" to "com.adeo.android.app",
        "castorama" to "com.kingfisher.cafr",
        "action" to "com.action.consumerapp",
        "gifi" to "fr.profilweb.gifi",
        "foirfouille" to "com.app.appfoirfouille",
        "darty" to "com.darty.catalogue",
        "electrodepot" to "com.pictime.electrodepot",
        "intersport" to "com.ua.intersport.app",
        "hm" to "com.hm.goe",
        "sephora" to "fr.sephora.sephorafrance",
        "nocibe" to "com.pictime.nocibe",
        "marionnaud" to "com.marionnaud.marionnaudfrance",
    )

    val all: List<Store> = listOf(
        // Alimentaire
        s("leclerc", "E.Leclerc", 0xFF005EB8, "e.leclerc", "leclerc", "e leclerc"),
        s("carrefour", "Carrefour", 0xFF0E4C96, "carrefour.fr", "carrefour"),
        s("auchan", "Auchan", 0xFFE2001A, "auchan.fr", "auchan"),
        s("intermarche", "Intermarché", 0xFFD7001B, "intermarche.com", "intermarche", "les mousquetaires"),
        s("u", "Magasins U", 0xFFE30613, "magasins-u.com", "super u", "hyper u", "u express", "magasins u", "cooperative u", "carte u"),
        s("lidl", "Lidl", 0xFF0050AA, "lidl.fr", "lidl"),
        s("aldi", "Aldi", 0xFF00005F, "aldi.fr", "aldi"),
        s("casino", "Casino", 0xFFC8102E, "supercasino.fr", "casino", "geant casino"),
        s("monoprix", "Monoprix", 0xFFD50032, "monoprix.fr", "monoprix"),
        s("franprix", "Franprix", 0xFFF39200, "franprix.fr", "franprix"),
        s("netto", "Netto", 0xFFE30613, "netto.fr", "netto"),
        s("cora", "Cora", 0xFFE30613, "cora.fr", "cora"),
        s("spar", "Spar", 0xFF00843D, "spar.fr", "spar"),
        s("vival", "Vival", 0xFFE30613, "vival.fr", "vival"),
        s("grandfrais", "Grand Frais", 0xFF2E7D32, "grandfrais.com", "grand frais"),
        s("picard", "Picard", 0xFF1E4F9C, "picard.fr", "picard"),
        s("biocoop", "Biocoop", 0xFF6AB023, "biocoop.fr", "biocoop"),
        s("naturalia", "Naturalia", 0xFF78BE20, "naturalia.fr", "naturalia"),
        // Maison, bricolage, jardin
        s("leroymerlin", "Leroy Merlin", 0xFF78BE20, "leroymerlin.fr", "leroy merlin"),
        s("castorama", "Castorama", 0xFF0058A3, "castorama.fr", "castorama"),
        s("bricodepot", "Brico Dépôt", 0xFFF7A600, "bricodepot.fr", "brico depot"),
        s("bricomarche", "Bricomarché", 0xFFE30613, "bricomarche.com", "bricomarche"),
        s("mrbricolage", "Mr.Bricolage", 0xFFE2001A, "mr-bricolage.fr", "mr bricolage", "mr.bricolage"),
        s("weldom", "Weldom", 0xFFEE7203, "weldom.fr", "weldom"),
        s("gammvert", "Gamm vert", 0xFF00973A, "gammvert.fr", "gamm vert"),
        s("jardiland", "Jardiland", 0xFF00A651, "jardiland.com", "jardiland"),
        s("truffaut", "Truffaut", 0xFF00594F, "truffaut.com", "truffaut"),
        s("botanic", "Botanic", 0xFF6BA539, "botanic.com", "botanic"),
        s("ikea", "IKEA", 0xFF0058A3, "ikea.com", "ikea", "ikea family"),
        s("conforama", "Conforama", 0xFFE30613, "conforama.fr", "conforama"),
        s("but", "BUT", 0xFFE4032E, "but.fr", "but fr", "magasins but"),
        s("maisonsdumonde", "Maisons du Monde", 0xFF3D3D3D, "maisonsdumonde.com", "maisons du monde"),
        s("gifi", "Gifi", 0xFFE6007E, "gifi.fr", "gifi"),
        s("action", "Action", 0xFF0047AB, "action.com", "action com", "magasins action"),
        s("centrakor", "Centrakor", 0xFFE5005B, "centrakor.com", "centrakor"),
        s("foirfouille", "La Foir'Fouille", 0xFFF5B400, "lafoirfouille.fr", "foir fouille", "foirfouille"),
        s("babou", "Babou", 0xFFE6007E, "babou.fr", "babou"),
        s("stokomani", "Stokomani", 0xFFE30613, "stokomani.fr", "stokomani"),
        // High-tech, culture, jouets
        s("darty", "Darty", 0xFFE2001A, "darty.com", "darty"),
        s("boulanger", "Boulanger", 0xFFF15A22, "boulanger.com", "boulanger"),
        s("fnac", "Fnac", 0xFFE1A925, "fnac.com", "fnac"),
        s("cultura", "Cultura", 0xFF6D2077, "cultura.com", "cultura"),
        s("electrodepot", "Electro Dépôt", 0xFFFFD100, "electrodepot.fr", "electro depot"),
        s("micromania", "Micromania", 0xFFE30613, "micromania.fr", "micromania"),
        s("kingjouet", "King Jouet", 0xFFE30613, "king-jouet.com", "king jouet"),
        s("joueclub", "JouéClub", 0xFFED1C24, "joueclub.fr", "joueclub", "joue club"),
        s("granderecre", "La Grande Récré", 0xFFE30613, "lagranderecre.fr", "grande recre"),
        s("bureauvallee", "Bureau Vallée", 0xFFE30613, "bureau-vallee.fr", "bureau vallee"),
        // Sport, mode
        s("decathlon", "Decathlon", 0xFF0082C3, "decathlon.fr", "decathlon"),
        s("intersport", "Intersport", 0xFF003DA5, "intersport.fr", "intersport"),
        s("gosport", "Go Sport", 0xFFE30613, "go-sport.com", "go sport"),
        s("sport2000", "Sport 2000", 0xFFE2001A, "sport2000.fr", "sport 2000"),
        s("kiabi", "Kiabi", 0xFF0E3F7E, "kiabi.com", "kiabi"),
        s("hm", "H&M", 0xFFE50010, "hm.com", "h&m", "h & m"),
        s("zara", "Zara", 0xFF111111, "zara.com", "zara"),
        s("celio", "Celio", 0xFFE30613, "celio.com", "celio"),
        s("jules", "Jules", 0xFF1A1A1A, "jules.com", "jules"),
        s("lahalle", "La Halle", 0xFFE30613, "lahalle.com", "la halle"),
        s("gemo", "Gémo", 0xFFE6007E, "gemo.fr", "gemo"),
        s("chaussea", "Chaussea", 0xFFE30613, "chaussea.com", "chaussea"),
        s("okaidi", "Okaïdi", 0xFFE5007D, "okaidi.fr", "okaidi"),
        // Beauté
        s("sephora", "Sephora", 0xFF111111, "sephora.fr", "sephora"),
        s("nocibe", "Nocibé", 0xFFE4007C, "nocibe.fr", "nocibe"),
        s("marionnaud", "Marionnaud", 0xFF8B1E3F, "marionnaud.fr", "marionnaud"),
        s("yvesrocher", "Yves Rocher", 0xFF00754A, "yves-rocher.fr", "yves rocher"),
        // Animaux, divers
        s("maxizoo", "Maxi Zoo", 0xFF003DA5, "maxizoo.fr", "maxi zoo"),
        s("animalis", "Animalis", 0xFFF39200, "animalis.com", "animalis"),
        s("totalenergies", "TotalEnergies", 0xFFE4002B, "totalenergies.fr", "totalenergies", "total energies"),
        s("flunch", "Flunch", 0xFFE30613, "flunch.fr", "flunch"),
        s("starbucks", "Starbucks", 0xFF00704A, "starbucks.fr", "starbucks"),
        s("pathe", "Pathé", 0xFFFFC20E, "pathe.fr", "pathe"),
        s("cgr", "CGR Cinémas", 0xFFE30613, "cgrcinemas.fr", "cgr"),
        // Alimentaire (suite)
        s("leaderprice", "Leader Price", 0xFFE2001A, "leaderprice.fr", "leader price"),
        s("coccimarket", "Coccimarket", 0xFFE30613, "coccimarket.fr", "coccimarket", "cocci market"),
        s("match", "Supermarchés Match", 0xFFE30613, "supermarchesmatch.fr", "supermarches match"),
        s("colruyt", "Colruyt", 0xFFE30613, "colruyt.fr", "colruyt"),
        s("biocbon", "Bio c' Bon", 0xFF7AB51D, "biocbon.fr", "bio c bon"),
        s("lavieclaire", "La Vie Claire", 0xFF8DC63F, "lavieclaire.com", "la vie claire"),
        s("thiriet", "Thiriet", 0xFF1D3F8C, "thiriet.com", "thiriet"),
        s("marieblachere", "Marie Blachère", 0xFFB3001B, "marieblachere.com", "marie blachere"),
        s("metro", "Metro", 0xFF003D7C, "metro.fr", "metro cash", "metro france"),
        s("promocash", "Promocash", 0xFFE30613, "promocash.com", "promocash"),
        s("costco", "Costco", 0xFFE31837, "costco.fr", "costco"),
        // Restauration
        s("mcdonalds", "McDonald's", 0xFFDA291C, "mcdonalds.fr", "mcdonald", "mcdonalds"),
        s("burgerking", "Burger King", 0xFFD62300, "burgerking.fr", "burger king"),
        s("kfc", "KFC", 0xFFE4002B, "kfc.fr", "kfc"),
        s("quick", "Quick", 0xFFE30613, "quick.fr", "quick fr", "quick restaurants"),
        s("subway", "Subway", 0xFF008C15, "subway.com", "subway"),
        s("dominos", "Domino's", 0xFF006491, "dominos.fr", "domino s pizza", "dominos"),
        s("pizzahut", "Pizza Hut", 0xFFE4002B, "pizzahut.fr", "pizza hut"),
        s("paul", "Paul", 0xFF1A1A1A, "paul.fr", "boulangerie paul"),
        s("briochedoree", "Brioche Dorée", 0xFFC8102E, "briochedoree.fr", "brioche doree"),
        s("columbus", "Columbus Café", 0xFF8B5E3C, "columbuscafe.com", "columbus cafe"),
        s("buffalo", "Buffalo Grill", 0xFFC8102E, "buffalo-grill.fr", "buffalo grill"),
        s("courtepaille", "Courtepaille", 0xFFE30613, "courtepaille.com", "courtepaille"),
        s("hippopotamus", "Hippopotamus", 0xFF00594F, "hippopotamus.fr", "hippopotamus"),
        s("delarte", "Del Arte", 0xFFC8102E, "delarte.fr", "del arte"),
        // Auto, carburant
        s("norauto", "Norauto", 0xFF004C97, "norauto.fr", "norauto"),
        s("feuvert", "Feu Vert", 0xFF009640, "feuvert.fr", "feu vert"),
        s("midas", "Midas", 0xFFFFD100, "midas.fr", "midas"),
        s("speedy", "Speedy", 0xFFE30613, "speedy.fr", "speedy"),
        s("points", "Point S", 0xFFE30613, "point-s.fr", "point s"),
        s("roady", "Roady", 0xFFE30613, "roady.fr", "roady"),
        s("esso", "Esso", 0xFF0033A0, "esso.fr", "esso"),
        s("shell", "Shell", 0xFFFFD500, "shell.fr", "shell"),
        s("avia", "Avia", 0xFFE30613, "avia-france.fr", "avia"),
        // Optique
        s("optic2000", "Optic 2000", 0xFF0055A4, "optic2000.com", "optic 2000"),
        s("krys", "Krys", 0xFFE4002B, "krys.com", "krys"),
        s("afflelou", "Alain Afflelou", 0xFF111111, "afflelou.com", "afflelou"),
        s("atol", "Atol", 0xFF0072BC, "opticiens-atol.com", "atol"),
        s("grandoptical", "Grand Optical", 0xFF1A1A1A, "grandoptical.com", "grand optical"),
        s("generaleoptique", "Générale d'Optique", 0xFF0072BC, "generale-optique.com", "generale d optique"),
        // Mode, chaussures
        s("primark", "Primark", 0xFF0DB5E7, "primark.com", "primark"),
        s("cea", "C&A", 0xFF00539F, "c-and-a.com", "c&a"),
        s("uniqlo", "Uniqlo", 0xFFE60012, "uniqlo.com", "uniqlo"),
        s("mango", "Mango", 0xFF111111, "mango.com", "mango"),
        s("bershka", "Bershka", 0xFF111111, "bershka.com", "bershka"),
        s("promod", "Promod", 0xFF111111, "promod.fr", "promod"),
        s("pimkie", "Pimkie", 0xFFE6007E, "pimkie.fr", "pimkie"),
        s("jennyfer", "Jennyfer", 0xFF111111, "jennyfer.com", "jennyfer"),
        s("etam", "Etam", 0xFF111111, "etam.com", "etam"),
        s("undiz", "Undiz", 0xFFE6007E, "undiz.com", "undiz"),
        s("damart", "Damart", 0xFF003DA5, "damart.fr", "damart"),
        s("galeries", "Galeries Lafayette", 0xFF111111, "galerieslafayette.com", "galeries lafayette"),
        s("printemps", "Printemps", 0xFF111111, "printemps.com", "printemps com"),
        s("tapealoeil", "Tape à l'œil", 0xFFE30613, "tapealoeil.com", "tape a l oeil"),
        s("dpam", "Du Pareil au Même", 0xFF0072BC, "dpam.com", "du pareil au meme", "dpam"),
        s("vertbaudet", "Vertbaudet", 0xFF009FE3, "vertbaudet.fr", "vertbaudet"),
        s("eram", "Eram", 0xFFE30613, "eram.fr", "eram"),
        s("besson", "Besson Chaussures", 0xFF111111, "besson-chaussures.com", "besson"),
        s("courir", "Courir", 0xFF111111, "courir.com", "courir"),
        s("footlocker", "Foot Locker", 0xFF111111, "footlocker.fr", "foot locker"),
        // Beauté, santé
        s("douglas", "Douglas", 0xFF111111, "douglas.fr", "douglas"),
        s("kiko", "Kiko Milano", 0xFF111111, "kikocosmetics.com", "kiko"),
        s("rituals", "Rituals", 0xFF111111, "rituals.com", "rituals"),
        s("loccitane", "L'Occitane", 0xFFFFC72C, "loccitane.com", "l occitane", "loccitane"),
        s("lush", "Lush", 0xFF111111, "lush.com", "lush"),
        s("pharmalafayette", "Pharmacie Lafayette", 0xFF00A651, "pharmacielafayette.com", "pharmacie lafayette"),
        // Maison, bricolage, divers
        s("natdec", "Nature & Découvertes", 0xFF2E5E3E, "natureetdecouvertes.com", "nature & decouvertes", "nature et decouvertes"),
        s("hema", "Hema", 0xFFE30613, "hema.com", "hema"),
        s("flyingtiger", "Flying Tiger", 0xFF111111, "flyingtiger.com", "flying tiger"),
        s("alinea", "Alinéa", 0xFF111111, "alinea.com", "alinea"),
        s("zodio", "Zôdio", 0xFFE6007E, "zodio.fr", "zodio"),
        s("casa", "Casa", 0xFF111111, "casashops.com", "casa shops"),
        s("bm", "B&M", 0xFF5B2C82, "bmstores.fr", "b&m"),
        s("noz", "Noz", 0xFFFFD100, "noz.fr", "noz"),
        s("maxibazar", "Maxi Bazar", 0xFFE30613, "maxibazar.fr", "maxi bazar"),
        s("bricoman", "Bricoman", 0xFFF39200, "bricoman.fr", "bricoman"),
        s("pointp", "Point.P", 0xFFE30613, "pointp.fr", "point p"),
        s("ldlc", "LDLC", 0xFF1E5AA8, "ldlc.com", "ldlc"),
        s("toysrus", "Toys\"R\"Us", 0xFF0055A4, "toysrus.fr", "toys r us"),
        s("picwic", "Picwic Toys", 0xFFE30613, "picwictoys.com", "picwic"),
        s("tomandco", "Tom&Co", 0xFF009FE3, "tomandco.com", "tom&co", "tom & co"),
        s("auvieuxcampeur", "Au Vieux Campeur", 0xFF2E5E3E, "auvieuxcampeur.fr", "vieux campeur"),
        // Cinéma
        s("ugc", "UGC", 0xFF111111, "ugc.fr", "ugc"),
        s("kinepolis", "Kinepolis", 0xFFE30613, "kinepolis.fr", "kinepolis"),
    ).map { it.copy(play = PLAY[it.id]) }

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
