package com.example.proyectosportpro.features.jugador.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.proyectosportpro.features.jugador.domain.PosicionJugador
import com.example.proyectosportpro.ui.theme.SportProColors

@Composable
fun PerfilJugadorScreen(
    usuarioId: String,
    nombreUsuario: String,
    onCerrarSesion: () -> Unit,
    viewModel: PerfilJugadorViewModel =
        viewModel(key = "perfil_jugador_$usuarioId")
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    var posicion by remember(usuarioId) {
        mutableStateOf<PosicionJugador?>(null)
    }
    var altura by remember(usuarioId) { mutableStateOf("") }
    var peso by remember(usuarioId) { mutableStateOf("") }
    var telefono by remember(usuarioId) { mutableStateOf("") }
    var nombreEmergencia by remember(usuarioId) { mutableStateOf("") }
    var telefonoEmergencia by remember(usuarioId) { mutableStateOf("") }
    var hayCambios by remember(usuarioId) { mutableStateOf(false) }
    var confirmarActualizacion by remember(usuarioId) {
        mutableStateOf(false)
    }
    var confirmarSalida by remember(usuarioId) {
        mutableStateOf(false)
    }

    LaunchedEffect(
        usuarioId,
        estado.cargaCompletada,
        estado.perfil
    ) {
        if (estado.cargaCompletada) {
            val perfil = estado.perfil
            posicion = perfil?.posicion
            altura = perfil?.alturaCm?.toString().orEmpty()
            peso = perfil?.pesoKg?.toString().orEmpty()
            telefono = perfil?.telefonoContacto.orEmpty()
            nombreEmergencia =
                perfil?.nombreContactoEmergencia.orEmpty()
            telefonoEmergencia =
                perfil?.telefonoContactoEmergencia.orEmpty()
            hayCambios = false
        }
    }

    val ocupado = estado.cargando || estado.guardando
    val editable = estado.cargaCompletada &&
            !ocupado && !estado.necesitaRecargar

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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = SportProColors.Navy,
                shape = RoundedCornerShape(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(46.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚽", fontSize = 24.sp)
                }
            }

            Column {
                Text(
                    text = "SPORTPRO",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 23.sp,
                    color = SportProColors.Navy
                )
                Text(
                    text = "PERFIL DEL JUGADOR",
                    fontSize = 10.sp,
                    color = SportProColors.TextSecondary
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Mi perfil deportivo",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 30.sp,
                color = SportProColors.Navy
            )
            Text(
                text = nombreUsuario,
                color = SportProColors.TextSecondary
            )
            Text(
                text = "Completa tus datos para preparar tu participación deportiva.",
                color = SportProColors.TextSecondary,
                fontSize = 13.sp
            )
        }

        if (ocupado) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = SportProColors.Field
            )
            Text(
                text = if (estado.guardando) {
                    "Guardando perfil…"
                } else {
                    "Consultando perfil…"
                },
                color = SportProColors.TextSecondary
            )
        }

        estado.error?.let {
            Text(text = it, color = SportProColors.Alert)
        }

        estado.mensaje?.let {
            Text(
                text = "✓ $it",
                color = SportProColors.Field,
                fontWeight = FontWeight.SemiBold
            )
        }

        OutlinedButton(
            onClick = {
                if (hayCambios) {
                    confirmarActualizacion = true
                } else {
                    viewModel.cargar()
                }
            },
            enabled = !ocupado,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Actualizar perfil")
        }

        if (estado.cargaCompletada) {
            TarjetaPerfilJugador(titulo = "⚽ Datos deportivos") {
                Text(
                    text = "Posición habitual",
                    fontWeight = FontWeight.SemiBold,
                    color = SportProColors.Navy
                )

                Row(
                    modifier = Modifier.horizontalScroll(
                        rememberScrollState()
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = posicion == null,
                        onClick = {
                            posicion = null
                            hayCambios = true
                        },
                        enabled = editable,
                        label = { Text("Sin definir") },
                        colors = coloresPosicion()
                    )

                    PosicionJugador.entries.forEach { opcion ->
                        FilterChip(
                            selected = posicion == opcion,
                            onClick = {
                                posicion = opcion
                                hayCambios = true
                            },
                            enabled = editable,
                            label = { Text(opcion.etiqueta) },
                            colors = coloresPosicion()
                        )
                    }
                }

                CampoPerfilJugador(
                    etiqueta = "Altura en centímetros",
                    valor = altura,
                    onCambio = {
                        altura = it
                        hayCambios = true
                    },
                    enabled = editable,
                    teclado = KeyboardType.Number
                )

                CampoPerfilJugador(
                    etiqueta = "Peso en kilogramos",
                    valor = peso,
                    onCambio = {
                        peso = it
                        hayCambios = true
                    },
                    enabled = editable,
                    teclado = KeyboardType.Decimal
                )

                Text(
                    text = "Puedes dejar estos campos vacíos y completarlos después.",
                    fontSize = 12.sp,
                    color = SportProColors.TextSecondary
                )
            }

            TarjetaPerfilJugador(titulo = "📱 Contacto") {
                CampoPerfilJugador(
                    etiqueta = "Teléfono de contacto",
                    valor = telefono,
                    onCambio = {
                        telefono = it
                        hayCambios = true
                    },
                    enabled = editable,
                    teclado = KeyboardType.Phone
                )
            }

            TarjetaPerfilJugador(titulo = "🆘 Contacto de emergencia") {
                CampoPerfilJugador(
                    etiqueta = "Nombre completo",
                    valor = nombreEmergencia,
                    onCambio = {
                        nombreEmergencia = it
                        hayCambios = true
                    },
                    enabled = editable
                )

                CampoPerfilJugador(
                    etiqueta = "Teléfono de emergencia",
                    valor = telefonoEmergencia,
                    onCambio = {
                        telefonoEmergencia = it
                        hayCambios = true
                    },
                    enabled = editable,
                    teclado = KeyboardType.Phone
                )

                Text(
                    text = "Registrar este contacto no le concede acceso " +
                            "a tu cuenta ni lo vincula como apoderado.",
                    fontSize = 12.sp,
                    color = SportProColors.TextSecondary
                )
            }

            Button(
                onClick = {
                    viewModel.guardar(
                        posicion = posicion,
                        alturaTexto = altura,
                        pesoTexto = peso,
                        telefonoContacto = telefono,
                        nombreContactoEmergencia = nombreEmergencia,
                        telefonoContactoEmergencia = telefonoEmergencia
                    )
                },
                enabled = editable &&
                        (hayCambios || estado.perfil == null),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SportProColors.Field,
                    contentColor = SportProColors.Surface
                )
            ) {
                Text(
                    text = if (estado.guardando) {
                        "Guardando…"
                    } else {
                        "Guardar perfil"
                    }
                )
            }
        }

        HorizontalDivider(color = SportProColors.Border)

        TextButton(
            onClick = {
                if (hayCambios) {
                    confirmarSalida = true
                } else {
                    onCerrarSesion()
                }
            },
            enabled = !ocupado,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cerrar sesión", color = SportProColors.Navy)
        }
    }

    if (confirmarActualizacion) {
        AlertDialog(
            onDismissRequest = { confirmarActualizacion = false },
            containerColor = SportProColors.Surface,
            title = { Text("¿Cargar el perfil guardado?") },
            text = {
                Text(
                    "Si la consulta se completa, se reemplazarán " +
                            "los cambios que todavía no guardaste."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarActualizacion = false
                        viewModel.cargar()
                    }
                ) {
                    Text("Actualizar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmarActualizacion = false }
                ) {
                    Text("Seguir editando")
                }
            }
        )
    }

    if (confirmarSalida) {
        AlertDialog(
            onDismissRequest = { confirmarSalida = false },
            containerColor = SportProColors.Surface,
            title = { Text("¿Cerrar sesión?") },
            text = {
                Text("Se perderán los cambios que todavía no guardaste.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarSalida = false
                        onCerrarSesion()
                    }
                ) {
                    Text("Cerrar sesión")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarSalida = false }) {
                    Text("Seguir editando")
                }
            }
        )
    }
}

@Composable
private fun TarjetaPerfilJugador(
    titulo: String,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = SportProColors.Surface
        ),
        border = BorderStroke(1.dp, SportProColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = titulo,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                color = SportProColors.Navy
            )
            contenido()
        }
    }
}

@Composable
private fun CampoPerfilJugador(
    etiqueta: String,
    valor: String,
    onCambio: (String) -> Unit,
    enabled: Boolean,
    teclado: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(etiqueta) },
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SportProColors.Field,
            unfocusedBorderColor = SportProColors.Border,
            focusedLabelColor = SportProColors.Field,
            cursorColor = SportProColors.Field,
            focusedTextColor = SportProColors.Navy,
            unfocusedTextColor = SportProColors.Navy
        )
    )
}

@Composable
private fun coloresPosicion() = FilterChipDefaults.filterChipColors(
    containerColor = SportProColors.SoftGreen,
    selectedContainerColor = SportProColors.SelectedGreen,
    selectedLabelColor = SportProColors.Navy
)