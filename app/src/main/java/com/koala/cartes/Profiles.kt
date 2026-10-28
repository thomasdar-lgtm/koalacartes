package com.koala.cartes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pastille ronde avec l'initiale du profil, comme le bouton du compte Google. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileBadge(p: Profile, size: Int = 34, onClick: () -> Unit = {}, onLongClick: (() -> Unit)? = null) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Color(p.color))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(p.initial, color = onColor(p.color), fontWeight = FontWeight.Black, fontSize = (size * 0.48).sp)
    }
}

/** Demandé une seule fois : le prénom du propriétaire du téléphone. */
@Composable
fun NameDialog(onDone: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Ton prénom") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tes cartes sont rangées dans ton profil. Il sert aussi quand tu partages tes cartes.", color = Muted)
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(20) }, singleLine = true, placeholder = { Text("Prénom") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text("Valider") } },
    )
}

/** Appui long sur la pastille : ajouter, renommer, recolorer ou supprimer un profil. */
@Composable
fun ProfilesDialog(repo: CardRepo, state: WalletState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<Profile?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Profile?>(null) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Profils") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.profiles.forEach { p ->
                    val n = state.cards.count { it.profile == p.id }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (p.id == state.current) Navy3 else Color.Transparent)
                            .clickable { repo.setCurrent(p.id); onClose() }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileBadge(p, 32, onClick = { repo.setCurrent(p.id); onClose() })
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            Text("$n carte${if (n > 1) "s" else ""}", color = Muted, fontSize = 12.sp)
                        }
                        IconButton(onClick = { editing = p }) { Icon(Icons.Default.Edit, "Modifier") }
                        if (state.profiles.size > 1) IconButton(onClick = { deleting = p }) { Icon(Icons.Default.Delete, "Supprimer") }
                    }
                }
                TextButton(onClick = { adding = true }) { Text("+ Ajouter un profil") }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Fermer") } },
    )

    editing?.let { p ->
        ProfileEditDialog(p.name, p.color, "Modifier le profil",
            onDone = { name, color -> repo.editProfile(p.id, name, color); editing = null },
            onCancel = { editing = null })
    }
    if (adding) {
        ProfileEditDialog("", null, "Nouveau profil",
            onDone = { name, color ->
                val id = repo.addProfile(name)
                if (color != null) repo.editProfile(id, name, color)
                repo.setCurrent(id); adding = false; onClose()
            },
            onCancel = { adding = false })
    }
    deleting?.let { p ->
        val n = state.cards.count { it.profile == p.id }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Supprimer le profil ${p.name} ?") },
            text = { Text(if (n > 0) "Ses $n carte${if (n > 1) "s" else ""} seront supprimées de ce téléphone." else "Ce profil ne contient aucune carte.") },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteProfile(p.id).forEach { Logos.delete(ctx, it.id) }
                    deleting = null
                }) { Text("Supprimer", color = Red) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun ProfileEditDialog(name0: String, color0: Long?, title: String, onDone: (String, Long?) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(name0) }
    var color by remember { mutableStateOf(color0) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(20) }, singleLine = true, placeholder = { Text("Prénom") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardRepo.PROFILE_COLORS.forEach { c ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                                .then(if (c == color) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                                .clickable { color = c }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(name, color) }, enabled = name.isNotBlank()) { Text("Valider") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Annuler") } },
    )
}

/** QR code à faire scanner par l'autre téléphone (bouton « Ajouter » de KoalaCartes). */
@Composable
fun ShareDialog(title: String, payload: String, onClose: () -> Unit) {
    val px = with(LocalDensity.current) { 300.dp.roundToPx() }
    val bmp = remember(payload, px) { Codes.render(payload, "QR_CODE", px) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp)) {
                    if (bmp != null) Image(
                        bmp.asImageBitmap(), "QR code", Modifier.fillMaxWidth().aspectRatio(1f),
                        contentScale = ContentScale.Fit, filterQuality = FilterQuality.None,
                    ) else Text("QR impossible à générer", color = Red)
                }
                Text(
                    "Sur l'autre téléphone : KoalaCartes → Ajouter, puis vise ce QR code.",
                    color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Fermer") } },
    )
}
