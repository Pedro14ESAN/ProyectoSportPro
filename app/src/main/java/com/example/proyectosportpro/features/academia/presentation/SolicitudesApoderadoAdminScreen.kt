package com.example.proyectosportpro.features.academia.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.proyectosportpro.features.academia.domain.EstadoSolicitudApoderado
import com.example.proyectosportpro.features.academia.domain.SolicitudApoderado
import com.example.proyectosportpro.ui.theme.SportProColors

private data class DecisionApoderado(
    val solicitud: SolicitudApoderado,
    val destino: EstadoSolicitudApoderado
)

@Composable
fun SolicitudesApoderadoAdminScreen(
    onVolver: () -> Unit,
    viewModel: SolicitudesApoderadoAdminViewModel = viewModel()
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var decision by remember { mutableStateOf<DecisionApoderado?>(null) }
    var autorizacionVerificada by remember(decision) { mutableStateOf(false) }

    // Descarta confirmaciones que ya no correspondan a los datos mostrados.
    LaunchedEffect(estado.solicitudes, estado.requiereActualizar) {
        val pendiente = decision
        if (pendiente != null && (estado.requiereActualizar ||
                    estado.solicitudes.none { it == pendiente.solicitud })) decision = null
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
                        Text("⚽", fontSize = 24.sp)
                    }
                }
                Text("SPORTPRO", fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.ExtraBold, fontSize = 23.sp, color = SportProColors.Navy)
            }
        }
        item {
            TextButton(onClick = onVolver, enabled = !estado.cargando) {
                Text("← Volver", color = SportProColors.Field)
            }
            Text("Apoderados", fontFamily = FontFamily.Serif, fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold, color = SportProColors.Navy)
            Text("Revisa las solicitudes y administra los vínculos con los jugadores.",
                color = SportProColors.TextSecondary)
        }
        item {
            Surface(color = SportProColors.SoftGreen, shape = RoundedCornerShape(16.dp)) {
                Text("🔒 Antes de aprobar, verifica la identidad y la autorización del apoderado " +
                        "por el procedimiento de la academia. Recibir un código no demuestra el parentesco.",
                    modifier = Modifier.padding(16.dp), color = SportProColors.Navy)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val filtros = listOf<EstadoSolicitudApoderado?>(
                    EstadoSolicitudApoderado.PENDIENTE, null, EstadoSolicitudApoderado.APROBADA,
                    EstadoSolicitudApoderado.RECHAZADA, EstadoSolicitudApoderado.REVOCADA
                )
                filtros.forEach { filtro ->
                    FilterChip(selected = estado.filtro == filtro,
                        onClick = { viewModel.seleccionarFiltro(filtro) },
                        label = { Text(filtro?.etiqueta ?: "Todas") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SportProColors.Surface,
                            labelColor = SportProColors.Navy,
                            selectedContainerColor = SportProColors.SelectedGreen,
                            selectedLabelColor = SportProColors.Navy))
                }
            }
            TextButton(onClick = viewModel::actualizar, enabled = !estado.cargando) {
                Text("Actualizar", color = SportProColors.Field)
            }
            if (estado.cargando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = SportProColors.Field)
                Text(if (estado.procesandoId != null) "Guardando y consultando el resultado…" else "Cargando solicitudes…",
                    color = SportProColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
            }
        }
        estado.error?.let { error ->
            item {
                Surface(color = SportProColors.InactiveBackground, shape = RoundedCornerShape(14.dp)) {
                    Text(error, Modifier.padding(16.dp), color = SportProColors.InactiveText)
                }
            }
        }
        estado.mensaje?.let { mensaje ->
            item { Text(mensaje, color = SportProColors.Field, fontWeight = FontWeight.SemiBold) }
        }
        if (!estado.cargando && !estado.requiereActualizar && estado.solicitudesVisibles.isEmpty()) {
            item {
                TarjetaRevisionApoderado {
                    Text("📋 No hay solicitudes en este filtro", color = SportProColors.Navy,
                        fontWeight = FontWeight.SemiBold)
                    Text("Las solicitudes aparecerán después de que el apoderado utilice su invitación.",
                        color = SportProColors.TextSecondary)
                }
            }
        }
        items(estado.solicitudesVisibles, key = { it.id }) { solicitud ->
            TarjetaRevisionApoderado {
                Text("👤 ${solicitud.nombreApoderado}", fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold, fontSize = 22.sp, color = SportProColors.Navy)
                Text("Relación declarada: ${solicitud.relacionDeclarada.etiqueta}", color = SportProColors.Navy)
                Surface(shape = RoundedCornerShape(8.dp), color =
                    if (solicitud.estado == EstadoSolicitudApoderado.APROBADA)
                        SportProColors.SelectedGreen else SportProColors.SoftGreen) {
                    Text(solicitud.estado.etiqueta, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = SportProColors.Navy, fontSize = 13.sp)
                }
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Identificador del jugador: ${solicitud.jugadorUid}", color = SportProColors.TextSecondary)
                        Text("Cuenta del apoderado: ${solicitud.apoderadoUid}", color = SportProColors.TextSecondary)
                    }
                }
                solicitud.revisadoPor?.let {
                    Text("Revisado por: $it", color = SportProColors.TextSecondary, fontSize = 12.sp)
                }
                solicitud.revocadoPor?.let {
                    Text("Revocado por: $it", color = SportProColors.TextSecondary, fontSize = 12.sp)
                }
                val habilitado = !estado.cargando && !estado.requiereActualizar
                if (solicitud.estado == EstadoSolicitudApoderado.PENDIENTE) {
                    Button(onClick = { decision = DecisionApoderado(solicitud, EstadoSolicitudApoderado.APROBADA) },
                        enabled = habilitado, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SportProColors.Field),
                        shape = RoundedCornerShape(12.dp)) { Text("Aprobar vínculo") }
                    OutlinedButton(onClick = { decision = DecisionApoderado(solicitud, EstadoSolicitudApoderado.RECHAZADA) },
                        enabled = habilitado, modifier = Modifier.fillMaxWidth()) { Text("Rechazar solicitud", color = SportProColors.InactiveText) }
                } else if (solicitud.estado == EstadoSolicitudApoderado.APROBADA) {
                    OutlinedButton(onClick = { decision = DecisionApoderado(solicitud, EstadoSolicitudApoderado.REVOCADA) },
                        enabled = habilitado, modifier = Modifier.fillMaxWidth()) { Text("Revocar vínculo", color = SportProColors.InactiveText) }
                }
            }
        }
    }

    decision?.let { pendiente ->
        val aprobar = pendiente.destino == EstadoSolicitudApoderado.APROBADA
        val titulo = when (pendiente.destino) {
            EstadoSolicitudApoderado.APROBADA -> "Aprobar vínculo"
            EstadoSolicitudApoderado.RECHAZADA -> "Rechazar solicitud"
            else -> "Revocar vínculo"
        }
        AlertDialog(
            onDismissRequest = { decision = null }, containerColor = SportProColors.Surface,
            title = { Text(titulo, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = SportProColors.Navy) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Apoderado: ${pendiente.solicitud.nombreApoderado}")
                    Text("Jugador (identificador): ${pendiente.solicitud.jugadorUid}")
                    Text(when (pendiente.destino) {
                        EstadoSolicitudApoderado.APROBADA -> "Se registrará un vínculo autorizado entre estas dos cuentas."
                        EstadoSolicitudApoderado.RECHAZADA -> "Esta solicitud quedará rechazada y no creará un vínculo."
                        else -> "El vínculo dejará de estar activo. Se conservará el historial de la solicitud."
                    })
                    if (aprobar) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = autorizacionVerificada,
                                onCheckedChange = { autorizacionVerificada = it },
                                colors = CheckboxDefaults.colors(checkedColor = SportProColors.Field))
                            Text("He verificado la identidad del apoderado y su autorización para representar a este jugador.")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !estado.cargando && !estado.requiereActualizar &&
                        (!aprobar || autorizacionVerificada), onClick = {
                    decision = null
                    when (pendiente.destino) {
                        EstadoSolicitudApoderado.APROBADA -> viewModel.aprobar(pendiente.solicitud)
                        EstadoSolicitudApoderado.RECHAZADA -> viewModel.rechazar(pendiente.solicitud)
                        EstadoSolicitudApoderado.REVOCADA -> viewModel.revocar(pendiente.solicitud)
                        else -> Unit
                    }
                }) { Text("Confirmar", color = SportProColors.Field) }
            },
            dismissButton = { TextButton(onClick = { decision = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun TarjetaRevisionApoderado(contenido: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = SportProColors.Surface,
        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, SportProColors.Border)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = contenido)
    }
}
