package com.example.proyectosportpro.features.academia.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.proyectosportpro.features.academia.domain.EstadoSolicitudVinculacion
import com.example.proyectosportpro.features.academia.domain.SolicitudVinculacion
import com.example.proyectosportpro.ui.theme.SportProColors

@Composable
fun SolicitudesAdminScreen(
    onVolver: () -> Unit,
    viewModel: SolicitudesAdminViewModel = viewModel()
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var soloPendientes by rememberSaveable { mutableStateOf(true) }
    var seleccionada by remember { mutableStateOf<SolicitudVinculacion?>(null) }
    var aprobar by remember { mutableStateOf(true) }
    val visibles = estado.solicitudes.filter {
        !soloPendientes || it.estado == EstadoSolicitudVinculacion.PENDIENTE
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SportProColors.Background)
            .safeDrawingPadding(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("SPORTPRO", color = SportProColors.Navy, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            TextButton(onClick = onVolver, enabled = !estado.cargando) { Text("Volver a academias") }
            Text("Solicitudes de ingreso", color = SportProColors.Navy,
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
            Spacer(Modifier.height(8.dp))
            Text("Revisa quién solicita incorporarse a tu academia.",
                color = SportProColors.TextSecondary)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = soloPendientes, onClick = { soloPendientes = true },
                    label = { Text("Pendientes") })
                FilterChip(selected = !soloPendientes, onClick = { soloPendientes = false },
                    label = { Text("Todas") })
            }
            OutlinedButton(onClick = viewModel::actualizar, enabled = !estado.cargando,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("Actualizar")
            }
            if (estado.cargando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = SportProColors.Field)
            }
            estado.error?.let { Text(it, color = SportProColors.Alert) }
            estado.mensaje?.let { Text(it, color = SportProColors.Navy) }
        }
        if (visibles.isEmpty() && !estado.cargando && estado.error == null) {
            item {
                Text(if (soloPendientes) "No hay solicitudes pendientes." else "Todavía no hay solicitudes.",
                    color = SportProColors.TextSecondary)
            }
        }
        items(visibles, key = { it.id }) { solicitud ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, SportProColors.Border),
                colors = CardDefaults.cardColors(containerColor = SportProColors.Surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(solicitud.nombreSolicitante, fontWeight = FontWeight.Bold,
                        fontSize = 20.sp, color = SportProColors.Navy)
                    Text(solicitud.rolSolicitante.etiqueta, color = SportProColors.TextSecondary)
                    Surface(shape = RoundedCornerShape(8.dp),
                        color = if (solicitud.estado == EstadoSolicitudVinculacion.RECHAZADA)
                            SportProColors.InactiveBackground else SportProColors.SoftGreen) {
                        Text(when (solicitud.estado) {
                            EstadoSolicitudVinculacion.PENDIENTE -> "Pendiente"
                            EstadoSolicitudVinculacion.APROBADA -> "Aprobada"
                            EstadoSolicitudVinculacion.RECHAZADA -> "Rechazada"
                        }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = SportProColors.Navy)
                    }
                    if (solicitud.estado == EstadoSolicitudVinculacion.PENDIENTE) {
                        Button(onClick = { seleccionada = solicitud; aprobar = true },
                            enabled = !estado.cargando, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SportProColors.Field,
                                contentColor = SportProColors.Surface)) { Text("Aprobar") }
                        OutlinedButton(onClick = { seleccionada = solicitud; aprobar = false },
                            enabled = !estado.cargando, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SportProColors.Alert)) {
                            Text("Rechazar")
                        }
                    }
                }
            }
        }
    }
    seleccionada?.let { solicitud ->
        AlertDialog(
            onDismissRequest = { seleccionada = null },
            containerColor = SportProColors.Surface,
            title = { Text(if (aprobar) "¿Aprobar solicitud?" else "¿Rechazar solicitud?",
                color = SportProColors.Navy) },
            text = { Text(if (aprobar)
                "${solicitud.nombreSolicitante} quedará vinculado con el rol ${solicitud.rolSolicitante.etiqueta} a esta academia. " +
                        "Después podrás asignarle un equipo."
            else "Se rechazará la solicitud de ${solicitud.nombreSolicitante}.") },
            confirmButton = {
                TextButton(enabled = !estado.cargando, onClick = {
                    viewModel.resolver(solicitud, aprobar)
                    seleccionada = null
                }) { Text(if (aprobar) "Aprobar" else "Rechazar") }
            },
            dismissButton = { TextButton(onClick = { seleccionada = null }) { Text("Cancelar") } }
        )
    }
}
