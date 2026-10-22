package com.example.proyectosportpro.features.academia.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.proyectosportpro.features.academia.domain.EstadoSolicitudVinculacion
import com.example.proyectosportpro.ui.theme.SportProColors

@Composable
fun SolicitudVinculacionScreen(
    usuarioId: String,
    viewModel: SolicitudVinculacionViewModel = viewModel(
        key = "solicitud_vinculacion_$usuarioId"
    )
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    val puedeEnviar = !estado.cargando &&
            estado.academiaId.isNotBlank() &&
            estado.solicitud == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SportProColors.Background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "SPORTPRO",
            color = SportProColors.Navy,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp
        )

        Text(
            text = "Vincularme a una academia",
            color = SportProColors.Navy,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp
        )

        Text(
            text = "Solicita al administrador el código de su academia. " +
                    "Él revisará tu solicitud como director técnico.",
            color = SportProColors.TextSecondary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = SportProColors.Surface
            ),
            border = BorderStroke(1.dp, SportProColors.Border)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Solicitud de vinculación",
                    color = SportProColors.Navy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                OutlinedTextField(
                    value = estado.academiaId,
                    onValueChange = viewModel::cambiarAcademiaId,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !estado.cargando,
                    singleLine = true,
                    label = { Text("Código de academia") },
                    supportingText = {
                        Text("Pega el código completo que te compartieron.")
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = viewModel::enviarSolicitud,
                    enabled = puedeEnviar,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SportProColors.Field,
                        contentColor = SportProColors.Surface
                    )
                ) {
                    Text("Enviar solicitud")
                }

                OutlinedButton(
                    onClick = viewModel::actualizar,
                    enabled = !estado.cargando &&
                            estado.academiaId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Actualizar estado")
                }

                if (estado.cargando) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = SportProColors.Field
                    )

                    Text(
                        text = "Consultando…",
                        color = SportProColors.TextSecondary
                    )
                }

                estado.error?.let { error ->
                    Text(
                        text = error,
                        color = SportProColors.Alert
                    )
                }

                estado.mensaje?.let { mensaje ->
                    Text(
                        text = mensaje,
                        color = SportProColors.TextPrimary
                    )
                }
            }
        }

        estado.solicitud?.let { solicitud ->
            val titulo = when (solicitud.estado) {
                EstadoSolicitudVinculacion.PENDIENTE ->
                    "Pendiente de revisión"

                EstadoSolicitudVinculacion.APROBADA ->
                    "Solicitud aprobada"

                EstadoSolicitudVinculacion.RECHAZADA ->
                    "Solicitud rechazada"
            }

            val detalle = when (solicitud.estado) {
                EstadoSolicitudVinculacion.PENDIENTE ->
                    "El administrador debe revisar tu solicitud. " +
                            "Puedes volver a consultar su estado."

                EstadoSolicitudVinculacion.APROBADA ->
                    "El administrador aprobó tu vinculación. " +
                            "La asignación de equipos se gestiona por separado."

                EstadoSolicitudVinculacion.RECHAZADA ->
                    "Contacta al administrador si necesitas aclarar " +
                            "el motivo de la decisión."
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = SportProColors.Surface
                ),
                border = BorderStroke(1.dp, SportProColors.Border)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = titulo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (
                            solicitud.estado ==
                            EstadoSolicitudVinculacion.RECHAZADA
                        ) {
                            SportProColors.Alert
                        } else {
                            SportProColors.Navy
                        }
                    )

                    Text(
                        text = solicitud.nombreSolicitante,
                        color = SportProColors.TextPrimary
                    )

                    Text(
                        text = detalle,
                        color = SportProColors.TextSecondary
                    )
                }
            }
        }
    }
}