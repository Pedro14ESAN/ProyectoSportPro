package com.example.proyectosportpro.features.academia.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.example.proyectosportpro.features.academia.domain.EstadoSolicitudApoderado
import com.example.proyectosportpro.features.academia.domain.RelacionApoderado
import com.example.proyectosportpro.ui.theme.SportProColors

@Composable
fun SolicitudApoderadoScreen(
    usuarioId: String,
    nombreUsuario: String,
    viewModel: SolicitudApoderadoViewModel =
        viewModel(key = "solicitud_apoderado_$usuarioId")
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmarOtroCodigo by remember(usuarioId) { mutableStateOf(false) }
    val editable = !estado.cargando && !estado.debeConsultar && estado.solicitud == null

    Column(
        modifier = Modifier.fillMaxSize()
            .background(SportProColors.Background)
            .safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = SportProColors.Navy, shape = RoundedCornerShape(14.dp)) {
                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    Text("⚽", fontSize = 24.sp)
                }
            }
            Column {
                Text("SPORTPRO", fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.ExtraBold, fontSize = 23.sp,
                    color = SportProColors.Navy)
                Text("PADRE / APODERADO", fontSize = 10.sp,
                    color = SportProColors.TextSecondary)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Vincularme con un jugador", fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold, fontSize = 30.sp,
                color = SportProColors.Navy)
            Text(nombreUsuario, color = SportProColors.TextSecondary)
            Text("Solicita al administrador una invitación privada para tu cuenta de apoderado.",
                color = SportProColors.TextSecondary)
        }

        TarjetaSolicitudApoderado {
            Text("🔒 Invitación privada", fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold, fontSize = 21.sp, color = SportProColors.Navy)
            Text("Usa el código de invitación para apoderados. Es diferente del código de ingreso a la academia.",
                color = SportProColors.TextSecondary, fontSize = 13.sp)

            OutlinedTextField(
                value = estado.codigo,
                onValueChange = viewModel::cambiarCodigo,
                enabled = editable,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Código de invitación") },
                shape = RoundedCornerShape(12.dp)
            )

            Text("¿Cuál es tu relación con el jugador?", fontWeight = FontWeight.SemiBold,
                color = SportProColors.Navy)
            Column(Modifier.selectableGroup()) {
                RelacionApoderado.entries.forEach { opcion ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(
                                selected = estado.relacion == opcion,
                                enabled = editable,
                                role = Role.RadioButton,
                                onClick = { viewModel.seleccionarRelacion(opcion) }
                            ).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(selected = estado.relacion == opcion,
                            onClick = null, enabled = editable)
                        Text(opcion.etiqueta, color = SportProColors.Navy)
                    }
                }
            }
            Text("La relación declarada será verificada por el administrador. " +
                    "Enviar la solicitud no concede acceso a los datos del jugador.",
                fontSize = 12.sp, color = SportProColors.TextSecondary)

            Button(
                onClick = viewModel::enviar,
                enabled = editable && estado.codigo.isNotBlank() && estado.relacion != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SportProColors.Field)
            ) { Text("Enviar solicitud de vinculación") }

            OutlinedButton(
                onClick = viewModel::consultar,
                enabled = !estado.cargando && estado.codigo.isNotBlank(),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
            ) { Text("Consultar estado") }

            if (estado.cargando) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = SportProColors.Field)
                Text("Consultando…", color = SportProColors.TextSecondary)
            }
            estado.error?.let { Text(it, color = SportProColors.Alert) }
            estado.mensaje?.let { Text(it, color = SportProColors.Navy) }
            if (estado.debeConsultar && !estado.cargando) {
                Text("Consulta el estado antes de repetir el envío.",
                    color = SportProColors.TextSecondary, fontSize = 12.sp)
            }
        }

        estado.solicitud?.let { solicitud ->
            TarjetaSolicitudApoderado {
                val titulo = when (solicitud.estado) {
                    EstadoSolicitudApoderado.PENDIENTE -> "⏳ Pendiente de revisión"
                    EstadoSolicitudApoderado.APROBADA -> "✓ Solicitud aprobada"
                    EstadoSolicitudApoderado.RECHAZADA -> "Solicitud rechazada"
                    EstadoSolicitudApoderado.REVOCADA -> "Vínculo revocado"
                }
                val descripcion = when (solicitud.estado) {
                    EstadoSolicitudApoderado.PENDIENTE ->
                        "El administrador debe verificar tu relación o autorización antes de aprobar el vínculo."
                    EstadoSolicitudApoderado.APROBADA ->
                        "La academia aprobó tu solicitud de vinculación."
                    EstadoSolicitudApoderado.RECHAZADA ->
                        "La academia rechazó la solicitud. Contacta al administrador si necesitas una aclaración."
                    EstadoSolicitudApoderado.REVOCADA ->
                        "Este vínculo dejó de estar autorizado. Contacta al administrador si necesitas una aclaración."
                }
                Text(titulo, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                    fontSize = 22.sp, color = SportProColors.Navy)
                Text("Relación declarada: ${solicitud.relacionDeclarada.etiqueta}",
                    color = SportProColors.TextSecondary)
                Text(descripcion, color = SportProColors.TextSecondary)
            }
        }

        TextButton(
            onClick = { confirmarOtroCodigo = true },
            enabled = !estado.cargando && estado.codigo.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Usar otra invitación", color = SportProColors.Field) }
    }

    if (confirmarOtroCodigo) {
        AlertDialog(
            onDismissRequest = { confirmarOtroCodigo = false },
            containerColor = SportProColors.Surface,
            title = { Text("¿Usar otra invitación?", color = SportProColors.Navy) },
            text = {
                Text("Se limpiará este formulario. Esto no cancela una solicitud que ya haya recibido la academia. " +
                        "Conserva el código anterior para consultar su estado.")
            },
            confirmButton = {
                TextButton(enabled = !estado.cargando, onClick = {
                    confirmarOtroCodigo = false
                    viewModel.usarOtraInvitacion()
                }) { Text("Continuar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarOtroCodigo = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun TarjetaSolicitudApoderado(contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SportProColors.Surface),
        border = BorderStroke(1.dp, SportProColors.Border)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            contenido()
        }
    }
}
