package com.example.proyectosportpro.features.comunidad.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.proyectosportpro.features.auth.domain.RolUsuario
import com.example.proyectosportpro.features.comunidad.domain.Aviso
import com.example.proyectosportpro.features.partidos.domain.EquipoPartido
import com.example.proyectosportpro.ui.theme.SportProColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formatoFecha: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm", Locale.forLanguageTag("es-PE"))
        .withZone(ZoneId.of("America/Lima"))

@Composable
fun ComunidadScreen(
    usuarioId: String,
    rol: RolUsuario,
    viewModel: ComunidadViewModel = viewModel(key = "comunidad_$usuarioId")
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var publicando by remember { mutableStateOf(false) }
    var retirarDe by remember { mutableStateOf<Aviso?>(null) }
    LaunchedEffect(usuarioId, rol) { viewModel.iniciar(rol) }
    val ocupado = estado.cargando || estado.guardando

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SportProColors.Background).safeDrawingPadding(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = SportProColors.Navy, shape = RoundedCornerShape(14.dp)) {
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        Text("⚽", fontSize = 22.sp)
                    }
                }
                Column {
                    Text("SPORTPRO", fontFamily = FontFamily.Serif, fontWeight = FontWeight.ExtraBold,
                        fontSize = 23.sp, color = SportProColors.Navy)
                    Text(rol.etiqueta.uppercase(Locale.forLanguageTag("es-PE")), fontSize = 10.sp,
                        color = SportProColors.TextSecondary)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Comunidad", fontFamily = FontFamily.Serif, fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp, color = SportProColors.Navy)
            Text("Avisos de la academia y del cuerpo técnico para cada equipo.",
                color = SportProColors.TextSecondary)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = viewModel::actualizar, enabled = !ocupado) { Text("Actualizar") }
                Spacer(Modifier.weight(1f))
                if (estado.equiposGestionables.isNotEmpty()) {
                    Button(onClick = { publicando = true }, enabled = !ocupado,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SportProColors.Field)) {
                        Text("+ Publicar aviso")
                    }
                }
            }
            if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth(), color = SportProColors.Field)
            estado.error?.let { Text(it, color = SportProColors.Alert) }
            estado.mensaje?.let { Text(it, color = SportProColors.Field, fontWeight = FontWeight.SemiBold) }
        }
        if (!estado.cargando && estado.error == null && estado.avisos.isEmpty()) {
            item {
                TarjetaAviso {
                    Text("📣 Todavía no hay avisos", fontWeight = FontWeight.Bold, color = SportProColors.Navy)
                    Text(when {
                        estado.equipos.isEmpty() ->
                            "Cuando formes parte de un equipo, verás aquí sus avisos."
                        estado.equiposGestionables.isNotEmpty() ->
                            "Pulsa Publicar aviso para comunicar algo a tu equipo."
                        else -> "Aquí aparecerán los avisos de tu DT y de la academia."
                    }, color = SportProColors.TextSecondary)
                }
            }
        }
        items(estado.avisos, key = { "${it.academiaId}/${it.id}" }) { aviso ->
            TarjetaAviso {
                Text(aviso.nombreEquipo, color = SportProColors.Field, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp)
                Text(aviso.titulo, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                    fontSize = 20.sp, color = SportProColors.Navy)
                Text(aviso.mensaje, color = SportProColors.Navy)
                Text("${aviso.autorNombre} · ${formatoFecha.format(Instant.ofEpochMilli(aviso.fechaMillis))}",
                    color = SportProColors.TextSecondary, fontSize = 12.sp)
                if (estado.puedeRetirar(aviso)) {
                    TextButton(onClick = { retirarDe = aviso }, enabled = !ocupado) {
                        Text("Retirar aviso", color = SportProColors.Alert)
                    }
                }
            }
        }
    }

    if (publicando) {
        PublicarAvisoDialog(
            equipos = estado.equiposGestionables,
            validar = viewModel::validar,
            onCerrar = { publicando = false },
            onPublicar = { equipo, titulo, mensaje ->
                publicando = false
                viewModel.publicar(equipo, titulo, mensaje)
            }
        )
    }
    retirarDe?.let { aviso ->
        AlertDialog(
            onDismissRequest = { retirarDe = null }, containerColor = SportProColors.Surface,
            title = { Text("¿Retirar aviso?", color = SportProColors.Navy) },
            text = { Text("\"${aviso.titulo}\" dejará de mostrarse a ${aviso.nombreEquipo}.") },
            confirmButton = {
                TextButton(onClick = { retirarDe = null; viewModel.retirar(aviso) }) {
                    Text("Retirar", color = SportProColors.Alert)
                }
            },
            dismissButton = { TextButton(onClick = { retirarDe = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun PublicarAvisoDialog(
    equipos: List<EquipoPartido>,
    validar: (String, String) -> String?,
    onCerrar: () -> Unit,
    onPublicar: (EquipoPartido, String, String) -> Unit
) {
    var equipo by remember { mutableStateOf(equipos.singleOrNull()) }
    var titulo by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = SportProColors.Surface,
        title = { Text("Publicar aviso", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            color = SportProColors.Navy) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Equipo", fontWeight = FontWeight.SemiBold, color = SportProColors.Navy)
                Column(Modifier.selectableGroup()) {
                    equipos.forEach { opcion ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            .selectable(selected = equipo == opcion, role = Role.RadioButton,
                                onClick = { equipo = opcion; error = null }),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = equipo == opcion, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text("${opcion.nombre} · ${opcion.academiaNombre}")
                        }
                    }
                }
                OutlinedTextField(value = titulo, onValueChange = { titulo = it.take(80); error = null },
                    label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mensaje, onValueChange = { mensaje = it.take(1000); error = null },
                    label = { Text("Mensaje") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = SportProColors.Alert) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val elegido = equipo
                val problema = if (elegido == null) "Selecciona el equipo." else validar(titulo, mensaje)
                if (problema != null || elegido == null) error = problema
                else onPublicar(elegido, titulo, mensaje)
            }) { Text("Publicar", color = SportProColors.Field) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } }
    )
}

@Composable
private fun TarjetaAviso(contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SportProColors.Surface),
        border = BorderStroke(1.dp, SportProColors.Border)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            contenido()
        }
    }
}
