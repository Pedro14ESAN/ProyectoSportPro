package com.example.proyectosportpro.features.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onCrearCuenta: () -> Unit
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val recuperacion by viewModel.recuperacion.collectAsStateWithLifecycle()

    var correo by rememberSaveable { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var mostrarContrasena by remember { mutableStateOf(false) }

    val azulOscuro = Color(0xFF102E3C)
    val verdeLima = Color(0xFFB1F568)
    val habilitado = !estado.cargando && !recuperacion.visible

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(azulOscuro)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "SPORTPRO",
            color = verdeLima,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Text(
            text = "Bienvenido",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
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
                Text(
                    text = "Inicia sesión",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = correo,
                    onValueChange = { correo = it },
                    label = { Text("Correo electrónico") },
                    singleLine = true,
                    enabled = habilitado,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = contrasena,
                    onValueChange = { contrasena = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    enabled = habilitado,
                    visualTransformation = if (mostrarContrasena) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = mostrarContrasena,
                        onCheckedChange = { mostrarContrasena = it },
                        enabled = habilitado
                    )
                    Text("Mostrar contraseña")
                }

                TextButton(
                    onClick = {
                        contrasena = ""
                        mostrarContrasena = false
                        viewModel.abrirRecuperacion(correo)
                    },
                    enabled = habilitado,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("¿Olvidaste tu contraseña?")
                }

                estado.error?.let { mensaje ->
                    Text(
                        text = mensaje,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (estado.cargando) {
                    CircularProgressIndicator()
                    Text("Comprobando acceso…")
                }

                Button(
                    onClick = {
                        viewModel.iniciarSesion(correo, contrasena)
                        contrasena = ""
                    },
                    enabled = habilitado,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = verdeLima,
                        contentColor = azulOscuro
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("Iniciar sesión")
                }

                TextButton(
                    onClick = onCrearCuenta,
                    enabled = habilitado,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Crear una cuenta")
                }

                if (estado.error != null) {
                    TextButton(
                        onClick = { viewModel.cerrarSesion() },
                        enabled = habilitado
                    ) {
                        Text("Limpiar sesión y volver a intentar")
                    }
                }
            }
        }
    }

    if (recuperacion.visible) {
        AlertDialog(
            onDismissRequest = {
                viewModel.cerrarRecuperacion()
            },
            title = {
                Text(
                    if (recuperacion.solicitudCompletada) {
                        "Revisa tu correo"
                    } else {
                        "Recuperar contraseña"
                    }
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(
                        rememberScrollState()
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (recuperacion.solicitudCompletada) {
                        Text(
                            "Si existe una cuenta con ese correo, " +
                                    "recibirás un enlace para restablecer " +
                                    "tu contraseña."
                        )
                        Text(
                            "Revisa también la carpeta de spam. " +
                                    "Después de cambiarla, vuelve a SportPro " +
                                    "e inicia sesión."
                        )
                    } else {
                        Text(
                            "Ingresa el correo que utilizaste " +
                                    "para crear tu cuenta."
                        )

                        OutlinedTextField(
                            value = recuperacion.correo,
                            onValueChange = {
                                viewModel.cambiarCorreoRecuperacion(it)
                            },
                            label = { Text("Correo electrónico") },
                            singleLine = true,
                            enabled = !recuperacion.enviando,
                            isError = recuperacion.error != null,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        recuperacion.error?.let { mensaje ->
                            Text(
                                text = mensaje,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (recuperacion.enviando) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(
                                    12.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Text("Procesando solicitud…")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (recuperacion.solicitudCompletada) {
                    TextButton(
                        onClick = { viewModel.cerrarRecuperacion() }
                    ) {
                        Text("Volver al inicio de sesión")
                    }
                } else {
                    TextButton(
                        onClick = { viewModel.enviarRecuperacion() },
                        enabled = !recuperacion.enviando
                    ) {
                        Text("Enviar enlace")
                    }
                }
            },
            dismissButton = {
                if (!recuperacion.solicitudCompletada) {
                    TextButton(
                        onClick = { viewModel.cerrarRecuperacion() },
                        enabled = !recuperacion.enviando
                    ) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }
}