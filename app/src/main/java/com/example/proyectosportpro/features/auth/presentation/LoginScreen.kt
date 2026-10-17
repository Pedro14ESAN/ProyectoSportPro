package com.example.proyectosportpro.features.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

    var correo by rememberSaveable { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var mostrarContrasena by remember { mutableStateOf(false) }

    val azulOscuro = Color(0xFF102E3C)
    val verdeLima = Color(0xFFB1F568)

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
                    enabled = !estado.cargando,
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
                    enabled = !estado.cargando,
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

                Row {
                    Checkbox(
                        checked = mostrarContrasena,
                        onCheckedChange = {
                            mostrarContrasena = it
                        },
                        enabled = !estado.cargando
                    )

                    Text(
                        text = "Mostrar contraseña",
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                estado.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (estado.cargando) {
                    CircularProgressIndicator()
                    Text("Comprobando acceso…")
                }

                Button(
                    onClick = {
                        viewModel.iniciarSesion(
                            correo = correo,
                            contrasena = contrasena
                        )
                        contrasena = ""
                    },
                    enabled = !estado.cargando,
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
                    enabled = !estado.cargando,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Crear una cuenta")
                }

                if (estado.error != null) {
                    TextButton(
                        onClick = { viewModel.cerrarSesion() },
                        enabled = !estado.cargando
                    ) {
                        Text("Limpiar sesión y volver a intentar")
                    }
                }
            }
        }
    }
}