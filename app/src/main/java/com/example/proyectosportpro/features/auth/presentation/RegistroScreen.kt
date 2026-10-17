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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.proyectosportpro.features.auth.domain.PoliticaRegistro
import com.example.proyectosportpro.features.auth.domain.RolUsuario
import com.example.proyectosportpro.features.auth.domain.TipoRegistro
import java.time.LocalDate

@Composable
fun RegistroScreen(
    onRegistroCompletado: () -> Unit,
    viewModel: RegistroViewModel = viewModel()
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(estado.registroExitoso) {
        if (estado.registroExitoso) {
            onRegistroCompletado()
        }
    }

    var nombre by rememberSaveable { mutableStateOf("") }
    var correo by rememberSaveable { mutableStateOf("") }
    var rolSeleccionado by rememberSaveable { mutableStateOf("") }
    var fechaNacimientoTexto by rememberSaveable { mutableStateOf("") }
    var aceptoTerminos by rememberSaveable { mutableStateOf(false) }

    // Las contraseñas se mantienen solamente en memoria.
    var contrasena by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }
    var mostrarContrasena by remember { mutableStateOf(false) }
    var mostrarTerminos by remember { mutableStateOf(false) }

    val fechaNacimiento = fechaNacimientoTexto
        .takeIf { it.isNotBlank() }
        ?.let { LocalDate.parse(it) }

    val rolElegido = RolUsuario.rolesDeRegistro.firstOrNull {
        it.name == rolSeleccionado
    }

    val evaluacionEdad = if (
        fechaNacimiento != null && rolElegido != null
    ) {
        PoliticaRegistro.evaluar(
            fechaNacimiento = fechaNacimiento,
            rol = rolElegido
        )
    } else {
        null
    }

    val azulOscuro = Color(0xFF102E3C)
    val verdeLima = Color(0xFFB1F568)

    val formularioHabilitado =
        !estado.cargando &&
                !estado.perfilPendiente &&
                !estado.registroExitoso

    val puedeCrearCuenta =
        formularioHabilitado &&
                aceptoTerminos &&
                evaluacionEdad?.tipo == TipoRegistro.ADULTO

    LaunchedEffect(estado.perfilPendiente, estado.registroExitoso) {
        if (estado.perfilPendiente || estado.registroExitoso) {
            contrasena = ""
            confirmacion = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(azulOscuro)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SPORTPRO",
            color = verdeLima,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Text(
            text = "Crea tu cuenta",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Únete a tu equipo y organiza tu actividad deportiva.",
            color = Color(0xFFCEDAE0)
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (estado.registroExitoso) {
                    Text(
                        text = "¡Cuenta creada!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Tu cuenta y perfil se guardaron correctamente."
                    )

                    Text(
                        text = "Estado: pendiente de vinculación con tu academia."
                    )
                } else {
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        label = { Text("Nombre completo") },
                        enabled = formularioHabilitado,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Fecha de nacimiento",
                        fontWeight = FontWeight.Bold
                    )

                    FechaNacimientoField(
                        fecha = fechaNacimiento,
                        habilitado = formularioHabilitado,
                        onFechaSeleccionada = {
                            fechaNacimientoTexto = it.toString()
                        }
                    )

                    Text(
                        text = "La fecha declarada no acredita tu edad.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedTextField(
                        value = correo,
                        onValueChange = { correo = it },
                        label = { Text("Correo electrónico") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email
                        ),
                        enabled = formularioHabilitado,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = contrasena,
                        onValueChange = { contrasena = it },
                        label = { Text("Contraseña") },
                        supportingText = {
                            Text("Mínimo 8 caracteres")
                        },
                        visualTransformation = if (mostrarContrasena) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        ),
                        enabled = formularioHabilitado,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmacion,
                        onValueChange = { confirmacion = it },
                        label = { Text("Confirmar contraseña") },
                        visualTransformation = if (mostrarContrasena) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        ),
                        enabled = formularioHabilitado,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = mostrarContrasena,
                            onCheckedChange = {
                                mostrarContrasena = it
                            },
                            enabled = formularioHabilitado
                        )

                        Text("Mostrar contraseñas")
                    }

                    Text(
                        text = "Selecciona tu rol",
                        fontWeight = FontWeight.Bold
                    )

                    RolUsuario.rolesDeRegistro.forEach { rol ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = rolSeleccionado == rol.name,
                                onClick = {
                                    rolSeleccionado = rol.name
                                },
                                enabled = formularioHabilitado
                            )

                            Text(rol.etiqueta)
                        }
                    }

                    evaluacionEdad?.let { evaluacion ->
                        Text(
                            text = evaluacion.mensaje,
                            color = if (
                                evaluacion.tipo == TipoRegistro.ADULTO
                            ) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }

                    TextButton(
                        onClick = { mostrarTerminos = true }
                    ) {
                        Text("Leer términos y privacidad del prototipo")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = aceptoTerminos,
                            onCheckedChange = {
                                aceptoTerminos = it
                            },
                            enabled = formularioHabilitado
                        )

                        Text(
                            text = "Acepto los términos y la política " +
                                    "de privacidad del prototipo.",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    estado.error?.let { mensaje ->
                        Text(
                            text = mensaje,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (estado.cargando) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(
                                Alignment.CenterHorizontally
                            )
                        )

                        Text("Procesando registro…")
                    }

                    if (estado.perfilPendiente) {
                        Button(
                            onClick = {
                                viewModel.reintentarPerfil()
                            },
                            enabled = !estado.cargando,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reintentar perfil")
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.registrar(
                                    nombre = nombre,
                                    correo = correo,
                                    contrasena = contrasena,
                                    confirmacion = confirmacion,
                                    rol = rolElegido,
                                    aceptoTerminos = aceptoTerminos,
                                    fechaNacimiento = fechaNacimiento
                                )
                            },
                            enabled = puedeCrearCuenta,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = verdeLima,
                                contentColor = azulOscuro
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(
                                text = "Crear cuenta",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (mostrarTerminos) {
        AlertDialog(
            onDismissRequest = {
                mostrarTerminos = false
            },
            title = {
                Text("Términos y privacidad — prototipo académico")
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(
                        rememberScrollState()
                    )
                ) {
                    Text(
                        text = "Esta versión está destinada a pruebas del " +
                                "proyecto académico SportPro. Usa datos de prueba " +
                                "y un correo que controles.\n\n" +
                                "Firebase Authentication gestiona el acceso. " +
                                "Firestore guarda tu nombre, correo, rol, estado, " +
                                "aceptación de términos y fecha de creación.\n\n" +
                                "En esta etapa, la fecha de nacimiento se utiliza " +
                                "en la aplicación para evaluar el registro; " +
                                "todavía no se guarda en Firestore.\n\n" +
                                "Elegir un rol no concede acceso a una academia. " +
                                "No ingreses datos reales de menores en estas " +
                                "pruebas.\n\n" +
                                "Este texto es provisional y deberá sustituirse " +
                                "por los términos y la política revisados del " +
                                "proyecto antes de incorporar usuarios reales."
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarTerminos = false
                    }
                ) {
                    Text("Entendido")
                }
            }
        )
    }
}