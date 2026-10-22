package com.example.proyectosportpro.features.academia.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import com.example.proyectosportpro.ui.theme.SportProColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AcademiasScreen(
    usuarioId: String,
    onVerCategorias: (String) -> Unit,
    onVerEquipos: (String) -> Unit,
    onVerSolicitudes: (String) -> Unit,
    onCerrarSesion: () -> Unit
) {
    val academiaViewModel: AcademiaViewModel = viewModel(
        key = "academias_$usuarioId"
    )
    val estado by academiaViewModel.uiState.collectAsStateWithLifecycle()

    val azul = SportProColors.Navy
    val verde = SportProColors.Field

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SportProColors.Background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SPORTPRO",
            color = azul,
            fontFamily = FontFamily.Serif,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Text(
            text = "Academias",
            color = azul,
            fontFamily = FontFamily.Serif,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, SportProColors.Border),
            colors = CardDefaults.cardColors(
                containerColor = SportProColors.Surface,
                contentColor = SportProColors.TextPrimary
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Crear academia",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = estado.nombre,
                    onValueChange = academiaViewModel::cambiarNombre,
                    label = { Text("Nombre de la academia") },
                    singleLine = true,
                    enabled = !estado.cargando && !estado.pendiente,
                    modifier = Modifier.fillMaxWidth()
                )

                if (estado.pendiente) {
                    Text(
                        "Hay una creación pendiente de confirmar. " +
                                "Comprobaremos su resultado antes de repetirla."
                    )
                }

                estado.error?.let {
                    Text(
                        text = it,
                        color = SportProColors.Alert
                    )
                }

                estado.mensaje?.let {
                    Text(it, color = SportProColors.Field)
                }

                if (estado.cargando) {
                    CircularProgressIndicator(color = SportProColors.Field)
                    Text("Consultando el servidor…")
                }

                Button(
                    onClick = academiaViewModel::guardar,
                    enabled = !estado.cargando &&
                            estado.nombre.trim().length in 2..100,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = verde,
                        contentColor = SportProColors.Surface
                    )
                ) {
                    Text(
                        if (estado.pendiente) {
                            "Comprobar y reintentar"
                        } else {
                            "Guardar academia"
                        }
                    )
                }

                OutlinedButton(
                    onClick = academiaViewModel::actualizar,
                    enabled = !estado.cargando,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Actualizar")
                }

                HorizontalDivider(color = SportProColors.Border)

                Text(
                    text = "Mis academias",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                if (estado.academias.isEmpty() &&
                    !estado.cargando && estado.error == null
                ) {
                    Text("Todavía no tienes academias registradas.")
                }

                estado.academias.forEach { academia ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, SportProColors.Border),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = SportProColors.Surface,
                            contentColor = SportProColors.TextPrimary
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = academia.nombre,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                if (academia.activa) "Activa" else "Inactiva"

                            )
                            Text(
                                text = "Código de academia",
                                color = SportProColors.TextSecondary,
                                fontSize = 12.sp
                            )
                            SelectionContainer {
                                Text(
                                    text = academia.id,
                                    color = SportProColors.Navy,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "Mantén pulsado el código para copiarlo y compartirlo con jugadores o directores técnicos.",
                                color = SportProColors.TextSecondary,
                                fontSize = 12.sp
                            )
                            TextButton(
                                onClick = {
                                    onVerCategorias(academia.id)
                                },
                                enabled = !estado.cargando && academia.activa
                            ) {
                                Text("Ver categorías")
                            }

                            TextButton(
                                onClick = {
                                    onVerEquipos(academia.id)
                                },
                                enabled = !estado.cargando && academia.activa
                            ) {
                                Text("Ver equipos")
                            }
                            TextButton(
                                onClick = { onVerSolicitudes(academia.id) },
                                enabled = !estado.cargando
                            ) {
                                Text("Solicitudes de ingreso")
                            }
                        }
                    }
                }

                TextButton(
                    onClick = onCerrarSesion,
                    enabled = !estado.cargando,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar sesión")
                }
            }
        }
    }
}