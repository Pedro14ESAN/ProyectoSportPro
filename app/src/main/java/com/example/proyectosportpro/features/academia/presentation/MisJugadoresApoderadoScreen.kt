package com.example.proyectosportpro.features.academia.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.proyectosportpro.features.academia.domain.EstadoSolicitudApoderado
import com.example.proyectosportpro.ui.theme.SportProColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MisJugadoresApoderadoScreen(
    usuarioId: String,
    nombreUsuario: String,
    viewModel: MisJugadoresApoderadoViewModel = viewModel(key = "mis_jugadores_pad_$usuarioId")
) {
    var verSolicitud by rememberSaveable(usuarioId) { mutableStateOf(false) }
    if (verSolicitud) {
        BackHandler { verSolicitud = false; viewModel.actualizar() }
        Column(Modifier.fillMaxSize().background(SportProColors.Background)) {
            TextButton(onClick = { verSolicitud = false; viewModel.actualizar() },
                modifier = Modifier.statusBarsPadding()) {
                Text("← Volver a mis jugadores")
            }
            Box(Modifier.weight(1f)) {
                SolicitudApoderadoScreen(usuarioId = usuarioId, nombreUsuario = nombreUsuario)
            }
        }
        return
    }
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(usuarioId) { viewModel.actualizar() }
    val formato = remember {
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("es-PE"))
            .withZone(ZoneId.of("America/Lima"))
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
                    Text("PADRE / APODERADO", fontSize = 10.sp, color = SportProColors.TextSecondary)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Mis jugadores", fontFamily = FontFamily.Serif, fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp, color = SportProColors.Navy)
            Text(nombreUsuario, color = SportProColors.TextSecondary)
            Text("Jugadores con los que la academia aprobó tu vínculo y solicitudes en revisión.",
                color = SportProColors.TextSecondary, fontSize = 12.sp)
        }
        item {
            TextButton(onClick = viewModel::actualizar, enabled = !estado.cargando) { Text("Actualizar") }
            if (estado.cargando) LinearProgressIndicator(Modifier.fillMaxWidth(), color = SportProColors.Field)
            estado.error?.let { Text(it, color = SportProColors.Alert) }
        }
        if (!estado.cargando && estado.error == null && estado.jugadores.isEmpty()) {
            item {
                TarjetaJugadorVinculado {
                    Text("👤 Todavía no tienes jugadores vinculados", fontWeight = FontWeight.Bold,
                        color = SportProColors.Navy)
                    Text("Pide al administrador una invitación privada y úsala con el botón de abajo. " +
                            "Cuando la academia apruebe tu solicitud, el jugador aparecerá aquí.",
                        color = SportProColors.TextSecondary)
                }
            }
        }
        items(estado.jugadores, key = { it.solicitudId }) { jugador ->
            TarjetaJugadorVinculado {
                if (jugador.estado == EstadoSolicitudApoderado.APROBADA) {
                    Text(jugador.nombreAcademia, color = SportProColors.Field,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text("👤 ${jugador.nombreJugador}", fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold, fontSize = 22.sp, color = SportProColors.Navy)
                Text("Relación: ${jugador.relacion.etiqueta}", color = SportProColors.Navy)
                Surface(shape = RoundedCornerShape(8.dp), color =
                    if (jugador.estado == EstadoSolicitudApoderado.APROBADA)
                        SportProColors.SelectedGreen else SportProColors.SoftGreen) {
                    Text(when (jugador.estado) {
                        EstadoSolicitudApoderado.APROBADA -> "Vínculo activo"
                        else -> jugador.estado.etiqueta
                    }, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = SportProColors.Navy, fontSize = 13.sp)
                }
                jugador.fechaAprobacionMillis?.let {
                    Text("Aprobado el ${formato.format(Instant.ofEpochMilli(it))}",
                        color = SportProColors.TextSecondary, fontSize = 12.sp)
                }
                if (jugador.estado == EstadoSolicitudApoderado.PENDIENTE) {
                    Text("La academia debe verificar tu relación antes de aprobar el vínculo.",
                        color = SportProColors.TextSecondary, fontSize = 12.sp)
                }
            }
        }
        item {
            OutlinedButton(onClick = { verSolicitud = true }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)) { Text("Usar una invitación / consultar solicitud") }
        }
    }
}

@Composable
private fun TarjetaJugadorVinculado(contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SportProColors.Surface),
        border = BorderStroke(1.dp, SportProColors.Border)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            contenido()
        }
    }
}
