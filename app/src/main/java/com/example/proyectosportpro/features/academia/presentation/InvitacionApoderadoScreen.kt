package com.example.proyectosportpro.features.academia.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.proyectosportpro.features.academia.domain.EstadoInvitacionApoderado
import com.example.proyectosportpro.ui.theme.SportProColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun InvitacionApoderadoScreen(
    nombreJugador: String,
    onVolver: () -> Unit,
    viewModel: InvitacionApoderadoViewModel = viewModel()
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var confirmarGeneracion by remember { mutableStateOf(false) }
    var confirmarNueva by remember { mutableStateOf(false) }
    var confirmarAnulacion by remember { mutableStateOf(false) }
    var copiada by remember(estado.invitacion?.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(SportProColors.Background)
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
            Text("SPORTPRO", fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold, fontSize = 23.sp,
                color = SportProColors.Navy)
        }
        TextButton(onClick = onVolver, enabled = !estado.cargando) {
            Text("← Volver al plantel", color = SportProColors.Field)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Invitar a un apoderado", fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold, fontSize = 30.sp,
                color = SportProColors.Navy)
            Text("Jugador: $nombreJugador", fontWeight = FontWeight.SemiBold,
                color = SportProColors.Navy)
            Text("Genera una invitación privada para una cuenta de padre o apoderado.",
                color = SportProColors.TextSecondary)
        }

        TarjetaInvitacionApoderado {
            Text("🔒 Cuenta destinataria", fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold, fontSize = 21.sp, color = SportProColors.Navy)
            Text("Pide al apoderado que copie su identificador desde Perfil y te lo comparta. " +
                    "No ingreses su correo ni el código de la academia.",
                color = SportProColors.TextSecondary)
            OutlinedTextField(
                value = estado.apoderadoUid,
                onValueChange = viewModel::cambiarApoderadoUid,
                enabled = !estado.cargando && !estado.operacionIniciada,
                singleLine = true,
                label = { Text("Identificador de la cuenta PAD") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Text("La invitación se prepara con 48 horas de vigencia y solo puede utilizarla " +
                    "la cuenta indicada. Después deberás revisar y aprobar la solicitud.",
                color = SportProColors.TextSecondary, fontSize = 12.sp)

            Button(
                onClick = {
                    if (estado.operacionIniciada) viewModel.generarORecuperar()
                    else confirmarGeneracion = true
                },
                enabled = !estado.cargando && estado.apoderadoUid.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SportProColors.Field)
            ) {
                Text(if (estado.operacionIniciada) "Recuperar invitación" else "Generar invitación")
            }
            if (estado.cargando) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = SportProColors.Field)
                Text("Consultando…", color = SportProColors.TextSecondary)
            }
            estado.error?.let { Text(it, color = SportProColors.Alert) }
        }

        estado.invitacion?.let { invitacion ->
            val fecha = remember(invitacion.fechaVencimientoMillis) {
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-PE"))
                    .withZone(ZoneId.of("America/Lima"))
                    .format(Instant.ofEpochMilli(invitacion.fechaVencimientoMillis))
            }
            TarjetaInvitacionApoderado {
                Text("Invitación registrada", fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold, fontSize = 22.sp, color = SportProColors.Navy)
                Text(when (invitacion.estado) {
                    EstadoInvitacionApoderado.DISPONIBLE -> "Sin utilizar"
                    EstadoInvitacionApoderado.UTILIZADA -> "Ya utilizada para crear una solicitud"
                    EstadoInvitacionApoderado.REVOCADA -> "Invitación anulada"
                }, color = SportProColors.TextSecondary)
                Text("Vence: $fecha (hora de Lima)", color = SportProColors.TextSecondary)
                Text("Después de esa fecha, una invitación sin utilizar ya no puede enviarse.",
                    fontSize = 12.sp, color = SportProColors.TextSecondary)
                SelectionContainer {
                    Text(invitacion.id, color = SportProColors.Navy, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(invitacion.id)); copiada = true },
                    enabled = !estado.cargando &&
                            invitacion.estado == EstadoInvitacionApoderado.DISPONIBLE,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                ) { Text(if (copiada) "✓ Código copiado" else "Copiar invitación") }
                Text("Comparte el código por un canal privado con la cuenta destinataria. " +
                        "La invitación no constituye una aprobación del vínculo.",
                    fontSize = 12.sp, color = SportProColors.TextSecondary)
                if (invitacion.estado == EstadoInvitacionApoderado.DISPONIBLE) {
                    TextButton(
                        onClick = { confirmarAnulacion = true },
                        enabled = !estado.cargando,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Anular invitación", color = SportProColors.Alert) }
                }
            }
        }

        if (estado.operacionIniciada) {
            TextButton(onClick = { confirmarNueva = true }, enabled = !estado.cargando,
                modifier = Modifier.fillMaxWidth()) {
                Text("Preparar otra invitación", color = SportProColors.Field)
            }
        }
    }

    if (confirmarGeneracion) {
        AlertDialog(
            onDismissRequest = { confirmarGeneracion = false },
            containerColor = SportProColors.Surface,
            title = { Text("¿Generar invitación?", color = SportProColors.Navy) },
            text = {
                Text("Jugador: $nombreJugador\n\nCuenta PAD: ${estado.apoderadoUid.trim()}\n\n" +
                        "Comprueba que el identificador sea el que te compartió el apoderado.")
            },
            confirmButton = {
                TextButton(enabled = !estado.cargando, onClick = {
                    confirmarGeneracion = false
                    viewModel.generarORecuperar()
                }) { Text("Generar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarGeneracion = false }) { Text("Cancelar") }
            }
        )
    }
    if (confirmarAnulacion) {
        AlertDialog(
            onDismissRequest = { confirmarAnulacion = false },
            containerColor = SportProColors.Surface,
            title = { Text("¿Anular invitación?", color = SportProColors.Navy) },
            text = {
                Text("El código dejará de servir para enviar una solicitud. " +
                        "Si ya lo compartiste por error, anúlalo y prepara otra invitación.")
            },
            confirmButton = {
                TextButton(enabled = !estado.cargando, onClick = {
                    confirmarAnulacion = false
                    viewModel.anular()
                }) { Text("Anular", color = SportProColors.Alert) }
            },
            dismissButton = {
                TextButton(onClick = { confirmarAnulacion = false }) { Text("Cancelar") }
            }
        )
    }
    if (confirmarNueva) {
        AlertDialog(
            onDismissRequest = { confirmarNueva = false },
            containerColor = SportProColors.Surface,
            title = { Text("¿Preparar otra invitación?", color = SportProColors.Navy) },
            text = {
                Text("Esto no revoca una invitación anterior ni cancela solicitudes. " +
                        "Si no se confirmó el resultado, intenta primero Recuperar invitación.")
            },
            confirmButton = {
                TextButton(enabled = !estado.cargando, onClick = {
                    confirmarNueva = false
                    viewModel.prepararOtraInvitacion()
                }) { Text("Continuar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarNueva = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun TarjetaInvitacionApoderado(contenido: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SportProColors.Surface),
        border = BorderStroke(1.dp, SportProColors.Border)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            contenido()
        }
    }
}
