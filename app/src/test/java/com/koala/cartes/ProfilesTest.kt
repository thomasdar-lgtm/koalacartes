package com.koala.cartes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Simule deux téléphones : Thomas partage ses cartes, Cindy les importe. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfilesTest {
    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        Stores.load(ctx)
        ctx.filesDir.listFiles()?.forEach { it.delete() }
    }

    private fun phone(name: String, owner: String): CardRepo {
        val r = CardRepo(ctx, "$name.json")
        val me = r.state.value.profiles.first()
        r.editProfile(me.id, owner, me.color)
        return r
    }

    private fun card(store: String, number: String, format: String = "EAN_13") =
        Draft(storeId = Stores.byId(store)?.id, storeName = Stores.byId(store)?.name ?: store, color = 0xFF123456, number = number, format = format)

    /** Le QR affiché à l'écran est-il relisible ? On le dessine puis on le décode avec ZXing. */
    private fun scanQr(payload: String): String {
        val bmp = Codes.render(payload, "QR_CODE", 700)!!
        val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bmp.width, bmp.height, px)))).text
    }

    @Test
    fun catalogueCharge() {
        assertTrue("catalogue chargé", Stores.all.size > 600)
        assertNotNull(Stores.byId("leclerc"))
    }

    @Test
    fun partageEtImportDansLeProfilDuPrenom() {
        val thomas = phone("thomas", "Thomas")
        thomas.save(card("leclerc", "4012345678901"))
        thomas.save(card("intermarche", "2900001234567"))
        thomas.save(Draft(storeName = "Boulangerie du coin", color = 0xFFAA0000, number = "ABC123", format = "QR_CODE", note = "fidélité"))

        val st = thomas.state.value
        val payload = thomas.sharePayload(st.profile, st.currentCards)!!
        assertEquals(payload, scanQr(payload))

        val cindy = phone("cindy", "Cindy")
        cindy.save(card("sephora", "1111222233334"))
        val res = cindy.importShare(scanQr(payload))!!
        assertEquals("Thomas", res.profileName)
        assertEquals(3, res.added)

        val cs = cindy.state.value
        assertEquals(listOf("Cindy", "Thomas"), cs.profiles.map { it.name })
        assertEquals("bascule sur le profil importé", res.profileId, cs.current)
        assertEquals(3, cs.currentCards.size)
        assertEquals(1, cs.cards.count { it.profile == cs.profiles[0].id })
        val boul = cs.currentCards.first { it.storeName == "Boulangerie du coin" }
        assertEquals("QR_CODE", boul.format); assertEquals("fidélité", boul.note); assertNull(boul.storeId)
        assertEquals("leclerc", cs.currentCards.first { it.number == "4012345678901" }.storeId)

        // Rescanner : pas de doublon
        val again = cindy.importShare(payload)!!
        assertEquals(0, again.added); assertEquals(3, again.updated)
        assertEquals(4, cindy.state.value.cards.size)
    }

    @Test
    fun basculeEtDernierProfilMemorise() {
        val t = phone("bascule", "Thomas")
        val cId = t.addProfile("Cindy")
        t.cycleProfile()
        assertEquals(cId, t.state.value.current)
        t.save(card("auchan", "9999"))
        assertEquals("nouvelle carte dans le profil affiché", cId, t.state.value.cards.single().profile)
        // Relancer l'appli : même fichier, dernier profil conservé
        val reopened = CardRepo(ctx, "bascule.json")
        assertEquals(cId, reopened.state.value.current)
        reopened.cycleProfile()
        assertEquals("Thomas", reopened.state.value.profile.name)
    }

    @Test
    fun ancienneVersionSansProfils() {
        File(ctx.filesDir, "ancien.json").writeText(
            """{"app":"KoalaCartes","version":1,"cards":[{"id":"a","storeId":"leclerc","storeName":"E.Leclerc","number":"123","format":"EAN_13"}]}"""
        )
        val r = CardRepo(ctx, "ancien.json")
        val s = r.state.value
        assertTrue("prénom demandé", s.needsName)
        assertEquals(1, s.currentCards.size)
        r.editProfile(s.profile.id, "Thomas", s.profile.color)
        assertEquals("Thomas", r.state.value.profile.name)
        assertEquals(1, r.state.value.currentCards.size)
    }

    @Test
    fun vingtCartesTiennentDansUnQr() {
        val t = phone("vingt", "Thomas")
        val ids = Stores.all.take(20).map { it.id }
        ids.forEachIndexed { i, id -> t.save(card(id, "4006381333%03d".format(i))) }
        val s = t.state.value
        val p = t.sharePayload(s.profile, s.currentCards)
        assertNotNull("20 cartes doivent tenir dans un QR", p)
        assertEquals(p, scanQr(p!!))
    }

    @Test
    fun sauvegardeRestaureLesProfils() {
        val t = phone("save", "Thomas")
        t.save(card("leclerc", "1"))
        t.setCurrent(t.addProfile("Cindy"))
        t.save(card("sephora", "2"))
        val backup = t.exportJson()

        val n = phone("neuf", "Thomas")
        assertEquals(2, n.importJson(backup))
        val s = n.state.value
        assertEquals(setOf("Thomas", "Cindy"), s.profiles.map { it.name }.toSet())
        assertEquals("sephora", s.cards.first { it.profile == s.profiles.first { p -> p.name == "Cindy" }.id }.storeId)
    }
}
