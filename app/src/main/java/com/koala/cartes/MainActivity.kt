package com.koala.cartes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue

sealed interface Screen {
    data object Home : Screen
    data class Show(val id: String) : Screen
    data class Scan(val editing: Draft?) : Screen
    data class Edit(val draft: Draft) : Screen
    data class Pick(val draft: Draft) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val repo = CardRepo.get(this)
        setContent { KoalaTheme { App(repo) } }
    }
}

@Composable
fun App(repo: CardRepo) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val cards by repo.cards.collectAsState()
    val upd = remember { UpdateState() }
    LaunchedEffect(Unit) { upd.info = runCatching { Updater.check() }.getOrNull() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(cards) { Logos.fetchMissing(ctx, cards) }

    fun back() {
        screen = when (val s = screen) {
            is Screen.Pick -> Screen.Edit(s.draft)
            is Screen.Scan -> s.editing?.let { Screen.Edit(it) } ?: Screen.Home
            is Screen.Edit -> s.draft.id?.let { Screen.Show(it) } ?: Screen.Home
            else -> Screen.Home
        }
    }
    BackHandler(enabled = screen != Screen.Home) { back() }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            repo, cards, upd,
            onOpen = { repo.markUsed(it); screen = Screen.Show(it) },
            onAdd = { screen = Screen.Scan(null) },
        )

        is Screen.Show -> {
            val c = cards.firstOrNull { it.id == s.id }
            if (c == null) LaunchedEffect(s) { screen = Screen.Home }
            else CardScreen(
                c, onBack = { back() },
                onEdit = { screen = Screen.Edit(c.toDraft()) },
                onDelete = { repo.delete(c.id); Logos.delete(ctx, c.id); screen = Screen.Home },
            )
        }

        is Screen.Scan -> ScannerScreen(
            onResult = { r ->
                var d = (s.editing ?: Draft()).copy(number = r.value, format = r.format, formatLocked = true)
                if (s.editing == null || d.storeName.isBlank()) {
                    val st = Stores.byId(r.storeId)
                    d = if (st != null) d.withStore(st, auto = true) else repo.applyLearned(d)
                }
                screen = Screen.Edit(d)
            },
            onManual = { screen = Screen.Edit(s.editing ?: Draft()) },
            onClose = { back() },
        )

        is Screen.Edit -> EditScreen(
            s.draft,
            onPickStore = { screen = Screen.Pick(it) },
            onRescan = { screen = Screen.Scan(it) },
            onSave = { d ->
                val old = cards.firstOrNull { it.id == d.id }
                if (d.logoUri != null && d.id != null) Logos.reserve(d.id)
                val id = repo.save(d)
                if (d.logoUri != null) {
                    Logos.reserve(id)
                    scope.launch { Logos.importImage(ctx, id, Uri.parse(d.logoUri)) }
                } else if (old != null && (old.storeId != d.storeId || old.storeName != d.storeName.trim() ||
                        old.domain != (if (d.storeId == null) Stores.cleanDomain(d.domain) else ""))) {
                    Logos.delete(ctx, id)
                }
                screen = Screen.Show(id)
            },
            onBack = { back() },
        )

        is Screen.Pick -> StorePicker(
            onPicked = { st -> screen = Screen.Edit(s.draft.withStore(st)) },
            onCustom = { name -> screen = Screen.Edit(s.draft.withCustom(name, Stores.colorFor(name))) },
            onBack = { back() },
        )
    }
}
