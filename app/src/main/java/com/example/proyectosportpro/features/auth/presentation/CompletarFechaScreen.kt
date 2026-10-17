package com.example.proyectosportpro.features.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

@Composable
fun CompletarFechaScreen(
    viewModel: CompletarFechaViewModel = viewModel()
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    var fechaTexto by rememberSaveable { mutableStateOf("") }
    var fechaConfirmada by rememberSaveable { mutableStateOf(false) }

    val fecha = fechaTexto
        .takeIf { it.isNotBlank() }
        ?.let { LocalDate.parse(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF102E3C))
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "SPORTPRO",
            color = Color(0xFFB1F568),
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White,
                contentColor = Color(0xFF17252D)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when {
                    estado.cargando -> {
                        CircularProgressIndicator()
                        Text("Consultando o guardando tu perfil…")
                    }

                    !estado.consultado -> {
                        Text(
                            text = estado.error
                                ?: "No se pudo consultar el perfil."
                        )

                        Button(
                            onClick = { viewModel.consultar() }
                        ) {
                            Text("Reintentar consulta")
                        }
                    }

                    estado.fechaGuardada != null -> {
                        Text(
                            text = "Fecha de nacimiento registrada",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Fecha: ${estado.fechaGuardada}"
                        )

                        Text(
                            text = "Tu cuenta conserva el mismo identificador. " +
                                    "Guardar la fecha no verifica tu edad ni " +
                                    "concede permisos de academia."
                        )
                    }

                    else -> {
                        Text(
                            text = "Completa tu perfil",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Tu cuenta ya existe. Falta guardar " +
                                    "tu fecha de nacimiento."
                        )

                        FechaNacimientoField(
                            fecha = fecha,
                            habilitado = true,
                            onFechaSeleccionada = {
                                fechaTexto = it.toString()
                                fechaConfirmada = false
                            }
                        )

                        Text(
                            text = "Revisa la fecha antes de guardarla. " +
                                    "Después, su corrección requerirá revisión " +
                                    "administrativa."
                        )

                        Row {
                            Checkbox(
                                checked = fechaConfirmada,
                                onCheckedChange = {
                                    fechaConfirmada = it
                                }
                            )

                            Text(
                                text = "Confirmo que la fecha seleccionada " +
                                        "es correcta.",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        estado.error?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.guardar(fecha)
                            },
                            enabled = fecha != null && fechaConfirmada,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Guardar fecha de nacimiento")
                        }
                    }
                }
            }
        }
    }
}