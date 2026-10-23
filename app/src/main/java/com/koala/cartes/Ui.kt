package com.koala.cartes

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Navy = Color(0xFF00071A)
val Navy2 = Color(0xFF0D1733)
val Navy3 = Color(0xFF16213F)
val Red = Color(0xFFE3000F)
val Yellow = Color(0xFFFFCC00)
val Muted = Color(0xFFB7C0D8)

@Composable
fun KoalaTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Yellow, onPrimary = Navy, secondary = Red, onSecondary = Color.White,
        background = Navy, onBackground = Color.White, surface = Navy2, onSurface = Color.White,
        surfaceVariant = Navy3, onSurfaceVariant = Muted, surfaceContainer = Navy2,
        error = Color(0xFFFF6B6B),
    ),
    content = content,
)

fun onColor(c: Long): Color {
    val r = (c shr 16 and 0xFF).toInt(); val g = (c shr 8 and 0xFF).toInt(); val b = (c and 0xFF).toInt()
    return if (0.299 * r + 0.587 * g + 0.114 * b > 170) Navy else Color.White
}

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

private fun Context.activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

@Composable
fun Monogram(name: String, fg: Color, size: Int = 38) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(fg.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) { Text(Stores.initials(name), color = fg, fontWeight = FontWeight.Black, fontSize = (size * 0.38).sp) }
}

@Composable
fun CardTile(c: Card, onClick: () -> Unit) {
    val bg = Color(c.color); val fg = onColor(c.color)
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.586f).clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(bg, lerp(bg, Color.Black, 0.28f))))
            .clickable(onClick = onClick).padding(8.dp)
    ) {
        Monogram(c.storeName, fg, size = 24)
        Text(
            c.storeName, color = fg, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis, lineHeight = 13.sp, modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
fun BarcodeImage(number: String, format: String) {
    val two = Codes.is2D(format)
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val maxW = if (two) minOf(maxWidth, 280.dp) else maxWidth
        val px = with(LocalDensity.current) { maxW.roundToPx() }
        val bmp = remember(number, format, px) { Codes.render(number, format, px) }
        if (bmp == null) {
            Text("Code impossible à afficher : vérifie le numéro ou le type de code.", color = Red, textAlign = TextAlign.Center)
        } else {
            Image(
                bmp.asImageBitmap(), "Code-barres",
                modifier = Modifier.widthIn(max = maxW).fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height),
                contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None,
            )
        }
    }
}

private fun spaced(n: String) = if (n.length > 6 && n.all { it.isDigit() }) n.chunked(4).joinToString(" ") else n

// ---------- Accueil ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(repo: CardRepo, cards: List<Card>, upd: UpdateState, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(repo.exportJson().toByteArray()) }
        }.onSuccess { toast(ctx, "Sauvegarde enregistrée (${cards.size} cartes)") }
            .onFailure { toast(ctx, "Échec de la sauvegarde") }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val txt = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            repo.importJson(txt)
        }.onSuccess { toast(ctx, "$it nouvelle(s) carte(s) restaurée(s)") }
            .onFailure { toast(ctx, "Fichier de sauvegarde illisible") }
    }

    Scaffold(
        containerColor = Navy,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Koala", fontWeight = FontWeight.Black, color = Color.White)
                        Text("Cartes", fontWeight = FontWeight.Black, color = Yellow)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Sauvegarder mes cartes") }, onClick = {
                                menu = false
                                val d = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
                                exporter.launch("koalacartes-$d.json")
                            })
                            DropdownMenuItem(text = { Text("Restaurer une sauvegarde") }, onClick = {
                                menu = false
                                importer.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
                            })
                            DropdownMenuItem(text = { Text("Rechercher une mise à jour") }, onClick = {
                                menu = false
                                scope.launch {
                                    runCatching { Updater.check() }
                                        .onSuccess { i -> upd.info = i; if (i == null) toast(ctx, "KoalaCartes est à jour (${BuildConfig.VERSION_NAME})") }
                                        .onFailure { toast(ctx, "Pas de connexion, réessaie plus tard") }
                                }
                            })
                            DropdownMenuItem(text = { Text("Version ${BuildConfig.VERSION_NAME}") }, onClick = {}, enabled = false)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy, actionIconContentColor = Color.White),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd, containerColor = Yellow, contentColor = Navy,
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("Ajouter", fontWeight = FontWeight.Bold) },
            )
        },
    ) { pad ->
      Column(Modifier.padding(pad)) {
        UpdateBanner(upd)
        if (cards.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text("Aucune carte pour l'instant", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Scanne le code-barres d'une carte de fidélité, ou importe une capture d'écran de ton ancienne appli.",
                    color = Muted, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Navy)) {
                    Text("Ajouter ma première carte", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            val sorted = remember(cards, query) {
                val n = Stores.norm(query)
                cards.filter { n.isBlank() || Stores.norm(it.storeName).contains(n) || Stores.norm(it.note).contains(n) }
                    .sortedWith(compareByDescending<Card> { it.uses }.thenBy { Stores.norm(it.storeName) })
            }
            Column {
                if (cards.size >= 6) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text("Rechercher un magasin") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(sorted, key = { it.id }) { c -> CardTile(c) { onOpen(c.id) } }
                }
            }
        }
      }
    }
}

// ---------- Affichage en caisse ----------

@Composable
fun CardScreen(c: Card, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val w = ctx.activity()?.window
        val old = w?.attributes?.screenBrightness ?: -1f
        w?.let {
            val lp = it.attributes; lp.screenBrightness = 1f; it.attributes = lp
            it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            w?.let {
                val lp = it.attributes; lp.screenBrightness = old; it.attributes = lp
                it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    var confirm by remember { mutableStateOf(false) }
    val bg = Color(c.color); val fg = onColor(c.color)

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg, lerp(bg, Color.Black, 0.35f)))).systemBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = fg) }
            Text(
                c.storeName, color = fg, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Modifier", tint = fg) }
            IconButton(onClick = { confirm = true }) { Icon(Icons.Default.Delete, "Supprimer", tint = fg) }
        }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BarcodeImage(c.number, c.format)
            Spacer(Modifier.height(12.dp))
            Text(spaced(c.number), color = Color.Black, fontFamily = FontFamily.Monospace, fontSize = 20.sp, textAlign = TextAlign.Center)
            if (c.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(c.note, color = Color(0xFF555555), fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.weight(1.4f))
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Supprimer la carte ${c.storeName} ?") },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Supprimer", color = Red) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } },
        )
    }
}

// ---------- Ajout / modification ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    initial: Draft,
    onPickStore: (Draft) -> Unit,
    onRescan: (Draft) -> Unit,
    onSave: (Draft) -> Unit,
    onBack: () -> Unit,
) {
    var d by remember(initial) { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var fmtMenu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Navy,
        topBar = {
            TopAppBar(
                title = { Text(if (d.id == null) "Nouvelle carte" else "Modifier la carte", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy, navigationIconContentColor = Color.White),
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Magasin", color = Muted, fontSize = 13.sp)
            val has = d.storeName.isNotBlank()
            val fg = if (has) onColor(d.color) else Color.White
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (has) Color(d.color) else Navy3)
                    .clickable { onPickStore(d) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (has) { Monogram(d.storeName, fg); Spacer(Modifier.size(12.dp)) }
                Text(
                    if (has) d.storeName else "Choisir le magasin", color = fg, fontWeight = FontWeight.Bold,
                    fontSize = 17.sp, modifier = Modifier.weight(1f),
                )
                Text("Changer", color = fg.copy(alpha = 0.8f), fontSize = 13.sp)
            }
            if (d.autoStore && has) Text("Reconnu automatiquement — touche pour changer", color = Muted, fontSize = 12.sp)

            OutlinedTextField(
                value = d.number,
                onValueChange = { v -> d = d.copy(number = v, format = if (d.formatLocked) d.format else Codes.guess(v)); error = null },
                label = { Text("Numéro de carte") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onRescan(d) }) { Text("Scanner le code") }
                Box {
                    OutlinedButton(onClick = { fmtMenu = true }) { Text("Type : ${Codes.label(d.format)}") }
                    DropdownMenu(expanded = fmtMenu, onDismissRequest = { fmtMenu = false }) {
                        Codes.formats.forEach { f ->
                            DropdownMenuItem(text = { Text(Codes.label(f)) }, onClick = {
                                d = d.copy(format = f, formatLocked = true); fmtMenu = false
                            })
                        }
                    }
                }
            }
            OutlinedTextField(
                value = d.note, onValueChange = { d = d.copy(note = it) },
                label = { Text("Note (facultatif)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            if (d.number.isNotBlank()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { BarcodeImage(d.number.trim(), d.format) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    error = when {
                        d.storeName.isBlank() -> "Choisis le magasin"
                        d.number.isBlank() -> "Saisis ou scanne le numéro de carte"
                        else -> null
                    }
                    if (error == null) onSave(d.copy(number = d.number.trim()))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Navy),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Enregistrer", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

// ---------- Choix du magasin ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorePicker(onPicked: (Store) -> Unit, onCustom: (String) -> Unit, onBack: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = remember(q) { Stores.search(q) }
    Scaffold(
        containerColor = Navy,
        topBar = {
            TopAppBar(
                title = { Text("Choisir le magasin", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy, navigationIconContentColor = Color.White),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).imePadding()) {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                placeholder = { Text("Rechercher ou saisir un nom") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val typed = q.trim()
                if (typed.isNotEmpty() && list.none { Stores.norm(it.name) == Stores.norm(typed) }) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Navy3)
                                .clickable { onCustom(typed) }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Add, null, tint = Yellow)
                            Spacer(Modifier.size(10.dp))
                            Text("Autre magasin : « $typed »", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                items(list, key = { it.id }) { st ->
                    val fg = onColor(st.color)
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1.586f).clip(RoundedCornerShape(12.dp))
                            .background(Color(st.color)).clickable { onPicked(st) }.padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            st.name, color = fg, fontWeight = FontWeight.Black, fontSize = 14.sp,
                            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
