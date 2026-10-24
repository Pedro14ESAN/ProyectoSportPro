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

private data class CambioPlantel(
    val uid: String,
    val nombre: String,
    val agregar: Boolean
)

@Composable
fun PlantelScreen(
    puedeGestionar: Boolean,
    onVolver: () -> Unit,
    onInvitarApoderado: (String, String) -> Unit,
    viewModel: PlantelViewModel = viewModel()
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var filtro by rememberSaveable { mutableStateOf(FiltroEquipos.ACTIVOS) }
    var mostrarDisponibles by rememberSaveable { mutableStateOf(false) }
    var cambio by remember { mutableStateOf<CambioPlantel?>(null) }

    LaunchedEffect(viewModel, puedeGestionar) {
        viewModel.iniciar(puedeGestionar)
    }

    val ocupado = estado.cargando || estado.guardando
    val puedeModificar = puedeGestionar && estado.cargaCompletada &&
            !ocupado && !estado.necesitaActualizar
    val visibles = estado.jugadores.filter {
        when (filtro) {
            FiltroEquipos.ACTIVOS -> it.activo
            FiltroEquipos.TODOS -> true
            FiltroEquipos.INACTIVOS -> !it.activo
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
            .background(SportProColors.Background).safeDrawingPadding(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(color = SportProColors.Navy, shape = RoundedCornerShape(14.dp)) {
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        Text("⚽", fontSize = 24.sp)
                    }
                }
                Text("SPORTPRO", fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.ExtraBold, fontSize = 23.sp,
                    color = SportProColors.Navy)
            }
            TextButton(onClick = onVolver, enabled = !ocupado) {
                Text("← Volver a equipos", color = SportProColors.Field)
            }
            Text("Plantel del equipo", fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold, fontSize = 30.sp,
                color = SportProColors.Navy)
            Spacer(Modifier.height(8.dp))
            Text(
                if (puedeGestionar) "Gestiona los jugadores incorporados a este equipo."
                else "Consulta los jugadores de tu equipo asignado.",
                color = SportProColors.TextSecondary
            )
            if (estado.cargaCompletada) {
                Spacer(Modifier.height(8.dp))
                Text("${estado.jugadores.count { it.activo }} jugadores activos",
                    color = SportProColors.Field, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FiltroEquipos.entries.forEach { opcion ->
                    FilterChip(
                        selected = filtro == opcion,
                        onClick = { filtro = opcion },
                        label = { Text(opcion.etiqueta) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SportProColors.SoftGreen,
                            selectedContainerColor = SportProColors.Navy,
                            selectedLabelColor = SportProColors.Surface
                        )
                    )
                }
            }
            OutlinedButton(
                onClick = { cambio = null; viewModel.actualizar() },
                enabled = !ocupado,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Actualizar plantel") }
            if (ocupado) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = SportProColors.Field)
                Text(if (estado.guardando) "Guardando cambio…" else "Consultando plantel…",
                    color = SportProColors.TextSecondary)
            }
            estado.error?.let { Text(it, color = SportProColors.Alert) }
            estado.mensaje?.let { Text("✓ $it", color = SportProColors.Field) }
        }

        if (estado.cargaCompletada && visibles.isEmpty()) {
            item {
                TarjetaPlantel {
                    Text("⚽ No hay jugadores en este filtro", fontWeight = FontWeight.Bold,
                        color = SportProColors.Navy)
                    Text(
                        if (estado.jugadores.isEmpty()) {
                            if (puedeGestionar) "Agrega un jugador aprobado de tu academia."
                            else "El administrador todavía no ha incorporado jugadores."
                        } else "Selecciona Todos para consultar los demás registros.",
                        color = SportProColors.TextSecondary
                    )
                }
            }
        }

        items(visibles, key = { "plantel_${it.jugadorUid}" }) { jugador ->
            TarjetaPlantel {
                Text("⚽ ${jugador.nombre}", fontWeight = FontWeight.Bold,
                    fontSize = 19.sp, color = SportProColors.Navy)
                Text(if (jugador.activo) "Activo en este equipo" else "Retirado del plantel activo",
                    color = SportProColors.TextSecondary)
                if (puedeGestionar && jugador.activo) {
                    OutlinedButton(
                        onClick = { onInvitarApoderado(jugador.jugadorUid, jugador.nombre) },
                        enabled = puedeModificar,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Invitar apoderado") }
                    OutlinedButton(
                        onClick = { cambio = CambioPlantel(jugador.jugadorUid, jugador.nombre, false) },
                        enabled = puedeModificar,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SportProColors.Alert)
                    ) { Text("Retirar del plantel") }
                }
                if (puedeGestionar && !jugador.activo &&
                    estado.disponibles.any { it.uid == jugador.jugadorUid }) {
                    OutlinedButton(
                        onClick = { cambio = CambioPlantel(jugador.jugadorUid, jugador.nombre, true) },
                        enabled = puedeModificar,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Reincorporar") }
                }
            }
        }

        if (puedeGestionar && estado.cargaCompletada) {
            item {
                HorizontalDivider(color = SportProColors.Border)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { mostrarDisponibles = !mostrarDisponibles },
                    enabled = puedeModificar,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SportProColors.Field)
                ) {
                    Text(if (mostrarDisponibles) "Ocultar jugadores disponibles" else "+ Agregar jugador")
                }
            }
            if (mostrarDisponibles) {
                item {
                    Text("Jugadores disponibles", fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold, fontSize = 23.sp, color = SportProColors.Navy)
                    Text("Con solicitud aprobada y vínculo activo en esta academia.",
                        color = SportProColors.TextSecondary)
                    if (estado.disponibles.isEmpty()) {
                        Text("No hay jugadores disponibles para incorporar.",
                            color = SportProColors.TextSecondary)
                    }
                }
                items(estado.disponibles, key = { "disponible_${it.uid}" }) { jugador ->
                    TarjetaPlantel {
                        Text(jugador.nombre, fontWeight = FontWeight.Bold, color = SportProColors.Navy)
                        if (estado.disponibles.count { it.nombre == jugador.nombre } > 1) {
                            Text("Identificador: ${jugador.uid}", fontSize = 12.sp,
                                color = SportProColors.TextSecondary)
                        }
                        Button(
                            onClick = { cambio = CambioPlantel(jugador.uid, jugador.nombre, true) },
                            enabled = puedeModificar,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SportProColors.Field)
                        ) { Text("Agregar al equipo") }
                    }
                }
            }
        }
    }

    cambio?.let { pendiente ->
        AlertDialog(
            onDismissRequest = { cambio = null },
            containerColor = SportProColors.Surface,
            title = {
                Text(if (pendiente.agregar) "¿Incorporar jugador?" else "¿Retirar jugador?",
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                    color = SportProColors.Navy)
            },
            text = {
                Text(if (pendiente.agregar) "${pendiente.nombre} quedará activo en este equipo."
                else "${pendiente.nombre} dejará de estar activo en este equipo. " +
                        "Se conservarán su registro y su vínculo con la academia.")
            },
            confirmButton = {
                TextButton(enabled = puedeModificar, onClick = {
                    cambio = null
                    if (pendiente.agregar) viewModel.agregar(pendiente.uid)
                    else viewModel.retirar(pendiente.uid)
                }) { Text(if (pendiente.agregar) "Incorporar" else "Retirar") }
            },
            dismissButton = {
                TextButton(onClick = { cambio = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun TarjetaPlantel(contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SportProColors.Surface),
        border = BorderStroke(1.dp, SportProColors.Border)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            contenido()
        }
    }
}
