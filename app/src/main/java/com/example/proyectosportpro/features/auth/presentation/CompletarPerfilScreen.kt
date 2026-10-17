package com.example.proyectosportpro.features.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.proyectosportpro.features.auth.domain.PoliticaRegistro
import com.example.proyectosportpro.features.auth.domain.RolUsuario
import com.example.proyectosportpro.features.auth.domain.TipoRegistro
import java.time.LocalDate

@Composable
fun CompletarPerfilScreen(
    onPerfilCompletado: () -> Unit,
    onCerrarSesion: () -> Unit,
    viewModel: CompletarPerfilViewModel = viewModel()
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    var nombre by rememberSaveable { mutableStateOf("") }
    var fechaTexto by rememberSaveable { mutableStateOf("") }
    var codigoRol by rememberSaveable { mutableStateOf("") }
    var aceptoTerminos by rememberSaveable { mutableStateOf(false) }
    var mostrarTerminos by rememberSaveable { mutableStateOf(false) }

    val fecha = remember(fechaTexto) {
        fechaTexto.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
    }

    val rol = RolUsuario.rolesDeRegistro.firstOrNull {
        it.name == codigoRol
    }

    val evaluacion = if (fecha != null && rol != null) {
        PoliticaRegistro.evaluar(
            fechaNacimiento = fecha,
            rol = rol
        )
    } else {
        null
    }

    val habilitado = !estado.guardando && !estado.completado

    LaunchedEffect(estado.completado) {
        if (estado.completado) {
            onPerfilCompletado()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF102E3C))
            .safeDrawingPadding()
            .imePadding()
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
                Text(
                    text = "Completa tu perfil",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Tu cuenta ya está creada. Completa tus datos " +
                            "para terminar el registro."
                )

                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre completo") },
                    singleLine = true,
                    enabled = habilitado,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Fecha de nacimiento")

                FechaNacimientoField(
                    fecha = fecha,
                    habilitado = habilitado,
                    onFechaSeleccionada = {
                        fechaTexto = it.toString()
                    }
                )

                Text(
                    text = "Selecciona tu rol",
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier.selectableGroup()
                ) {
                    RolUsuario.rolesDeRegistro.forEach { opcion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = codigoRol == opcion.name,
                                    enabled = habilitado,
                                    role = Role.RadioButton,
                                    onClick = {
                                        codigoRol = opcion.name
                                    }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = codigoRol == opcion.name,
                                onClick = null,
                                enabled = habilitado
                            )

                            Spacer(Modifier.width(12.dp))

                            Text(opcion.etiqueta)
                        }
                    }
                }

                evaluacion?.let {
                    Text(
                        text = it.mensaje,
                        color = if (it.tipo == TipoRegistro.ADULTO) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )

                    if (it.tipo == TipoRegistro.REQUIERE_APODERADO ||
                        it.tipo == TipoRegistro.SOLO_PERFIL_GESTIONADO
                    ) {
                        Text(
                            "El registro de menores estará disponible " +
                                    "cuando se habilite el proceso de " +
                                    "autorización del apoderado."
                        )
                    }
                }

                TextButton(
                    onClick = { mostrarTerminos = true },
                    enabled = habilitado
                ) {
                    Text("Leer términos y privacidad del prototipo")
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = aceptoTerminos,
                        onCheckedChange = { aceptoTerminos = it },
                        enabled = habilitado
                    )

                    Text(
                        "Acepto los términos y la política " +
                                "de privacidad del prototipo."
                    )
                }

                estado.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (estado.guardando) {
                    CircularProgressIndicator()
                    Text("Guardando tu perfil…")
                }

                if (estado.completado) {
                    Text("Perfil guardado. Comprobando tu acceso…")
                }

                Button(
                    onClick = {
                        viewModel.guardar(
                            nombre = nombre,
                            fechaNacimiento = fecha,
                            rol = rol,
                            aceptoTerminos = aceptoTerminos
                        )
                    },
                    enabled = habilitado &&
                            nombre.trim().length in 2..100 &&
                            evaluacion?.tipo == TipoRegistro.ADULTO &&
                            aceptoTerminos,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB1F568),
                        contentColor = Color(0xFF102E3C)
                    )
                ) {
                    Text("Guardar perfil")
                }

                TextButton(
                    onClick = onCerrarSesion,
                    enabled = habilitado,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar sesión")
                }
            }
        }
    }

    if (mostrarTerminos) {
        AlertDialog(
            onDismissRequest = { mostrarTerminos = false },
            title = {
                Text("Términos y privacidad del prototipo")
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(
                        rememberScrollState()
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "SportPro es un prototipo académico. " +
                                "Utiliza datos de prueba durante su evaluación."
                    )

                    Text(
                        "El perfil guarda tu nombre, correo, rol, " +
                                "fecha de nacimiento declarada, estado " +
                                "y aceptación de términos."
                    )

                    Text(
                        "La fecha declarada no verifica tu edad. " +
                                "Elegir un rol no concede acceso automático " +
                                "a una academia ni a sus datos."
                    )

                    Text(
                        "La contraseña se gestiona mediante " +
                                "Firebase Authentication y no se guarda " +
                                "en el documento de tu perfil."
                    )

                    Text(
                        "Estos términos son provisionales y deben " +
                                "reemplazarse por la política definitiva " +
                                "antes de utilizar la aplicación con " +
                                "usuarios reales."
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { mostrarTerminos = false }
                ) {
                    Text("Entendido")
                }
            }
        )
    }
}