package com.example.proyectosportpro.features.academia.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.proyectosportpro.ui.theme.SportProColors

@Composable
fun MisEquiposScreen(
    usuarioId: String,
    nombreUsuario: String,
    onVerPlantel: (String, String) -> Unit,
    viewModel: MisEquiposViewModel = viewModel(key = "mis_equipos_$usuarioId")
) {
    var verSolicitud by rememberSaveable(usuarioId) { mutableStateOf(false) }
    if (verSolicitud) {
        Column(Modifier.fillMaxSize().background(SportProColors.Background)) {
            TextButton(onClick = { verSolicitud = false }, modifier = Modifier.statusBarsPadding()) {
                Text("← Volver a mis equipos")
            }
            Box(Modifier.weight(1f)) { SolicitudVinculacionScreen(usuarioId = usuarioId) }
        }
        return
    }
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var filtro by rememberSaveable(usuarioId) { mutableStateOf(FiltroEquipos.ACTIVOS) }
    var detalleId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(usuarioId) { viewModel.actualizar() }
    val visibles = estado.equipos.filter {
        when (filtro) {
            FiltroEquipos.ACTIVOS -> it.activo
            FiltroEquipos.TODOS -> true
            FiltroEquipos.INACTIVOS -> !it.activo
        }
    }
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
                    Text("DIRECTOR TÉCNICO", fontSize = 10.sp, color = SportProColors.TextSecondary)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Mis equipos", fontFamily = FontFamily.Serif, fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp, color = SportProColors.Navy)
            Text(nombreUsuario, color = SportProColors.TextSecondary)
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FiltroEquipos.entries.forEach { opcion ->
                    FilterChip(selected = filtro == opcion, onClick = { filtro = opcion },
                        label = { Text(opcion.etiqueta) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SportProColors.Navy,
                            selectedLabelColor = SportProColors.Surface,
                            containerColor = SportProColors.SoftGreen))
                }
            }
            TextButton(onClick = viewModel::actualizar, enabled = !estado.cargando) { Text("Actualizar") }
            if (estado.cargando) LinearProgressIndicator(Modifier.fillMaxWidth(), color = SportProColors.Field)
            estado.error?.let { Text(it, color = SportProColors.Alert) }
        }
        if (!estado.cargando && estado.error == null && visibles.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = SportProColors.Surface),
                    shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, SportProColors.Border)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚽ Todavía no hay equipos para mostrar", fontWeight = FontWeight.Bold,
                            color = SportProColors.Navy)
                        Text(if (estado.equipos.isEmpty())
                            "El administrador debe aprobar tu vínculo y asignarte un equipo. " +
                                    "Después pulsa Actualizar."
                        else "Prueba el filtro Todos para ver tus otras asignaciones.",
                            color = SportProColors.TextSecondary)
                    }
                }
            }
        }
        items(visibles, key = { "${it.academiaId}/${it.id}" }) { equipo ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(equipo.academiaNombre, color = SportProColors.Field,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp)
                EquipoCard(nombre = equipo.nombre, categoria = equipo.categoriaNombre,
                    activo = equipo.activo, entrenador = nombreUsuario,
                    onClick = { detalleId = "${equipo.academiaId}/${equipo.id}" })
            }
        }
        item {
            OutlinedButton(onClick = { verSolicitud = true }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)) { Text("Vincularme / consultar solicitud") }
        }
    }
    estado.equipos.firstOrNull { "${it.academiaId}/${it.id}" == detalleId }?.let { equipo ->
        AlertDialog(onDismissRequest = { detalleId = null }, containerColor = SportProColors.Surface,
            title = { Text("⚽ ${equipo.nombre}", color = SportProColors.Navy,
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(equipo.academiaNombre)
                    Text("Categoría: ${equipo.categoriaNombre}")
                    Text("DT: $nombreUsuario")
                    Text(if (equipo.activo) "Equipo activo" else "Equipo inactivo")
                    OutlinedButton(
                        onClick = {
                            detalleId = null
                            onVerPlantel(equipo.academiaId, equipo.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("⚽ Ver plantel") }
                }
            }, confirmButton = { TextButton(onClick = { detalleId = null }) { Text("Cerrar") } })
    }
}
