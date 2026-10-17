package com.example.proyectosportpro.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.proyectosportpro.features.auth.domain.PerfilUsuario
import com.example.proyectosportpro.features.auth.presentation.CompletarFechaScreen
import com.example.proyectosportpro.features.auth.presentation.LoginScreen
import com.example.proyectosportpro.features.auth.presentation.LoginViewModel
import com.example.proyectosportpro.features.auth.presentation.RegistroScreen

private object Rutas {
    const val INICIO = "inicio"
    const val LOGIN = "login"
    const val REGISTRO = "registro"
    const val COMPLETAR_FECHA = "completar_fecha"
    const val PERFIL_FALTANTE = "perfil_faltante"
    const val CUENTA = "cuenta"
}

@Composable
fun SportProNavigation(
    loginViewModel: LoginViewModel = viewModel()
) {
    val navController = rememberNavController()

    val estado by loginViewModel.uiState.collectAsStateWithLifecycle()
    val entrada by navController.currentBackStackEntryAsState()
    val rutaActual = entrada?.destination?.route

    LaunchedEffect(
        estado.sesionRevisada,
        estado.cargando,
        estado.perfil,
        estado.requiereCrearPerfil,
        estado.requiereFechaNacimiento,
        rutaActual
    ) {
        if (!estado.sesionRevisada || estado.cargando) {
            return@LaunchedEffect
        }

        val destino = when {
            estado.requiereCrearPerfil ->
                Rutas.PERFIL_FALTANTE

            estado.requiereFechaNacimiento ->
                Rutas.COMPLETAR_FECHA

            estado.perfil != null ->
                Rutas.CUENTA

            rutaActual == Rutas.REGISTRO ->
                Rutas.REGISTRO

            else ->
                Rutas.LOGIN
        }

        if (rutaActual != destino) {
            navController.navigate(destino) {
                // Descarta las pantallas anteriores y sus ViewModels.
                popUpTo(navController.graph.id) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Rutas.INICIO
    ) {
        composable(Rutas.INICIO) {
            MensajeAcceso(
                titulo = "SportPro",
                mensaje = "Comprobando tu sesión…",
                cargando = true
            )
        }

        composable(Rutas.LOGIN) {
            LoginScreen(
                viewModel = loginViewModel,
                onCrearCuenta = {
                    navController.navigate(Rutas.REGISTRO) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Rutas.REGISTRO) {
            RegistroScreen(
                onRegistroCompletado = {
                    loginViewModel.revisarSesion()
                }
            )
        }

        composable(Rutas.COMPLETAR_FECHA) {
            if (estado.requiereFechaNacimiento) {
                CompletarFechaScreen(
                    onFechaGuardada = {
                        loginViewModel.revisarSesion()
                    }
                )
            }
        }

        composable(Rutas.PERFIL_FALTANTE) {
            if (estado.requiereCrearPerfil) {
                MensajeAcceso(
                    titulo = "Registro incompleto",
                    mensaje = "Tu cuenta existe, pero falta crear su perfil. " +
                            "La recuperación de este paso todavía está pendiente.",
                    cargando = estado.cargando,
                    onCerrarSesion = {
                        loginViewModel.cerrarSesion()
                    }
                )
            }
        }

        composable(Rutas.CUENTA) {
            val perfil = estado.perfil

            if (
                perfil != null &&
                !estado.requiereFechaNacimiento &&
                !estado.cargando
            ) {
                CuentaScreen(
                    perfil = perfil,
                    onCerrarSesion = {
                        loginViewModel.cerrarSesion()
                    }
                )
            }
        }
    }
}

@Composable
private fun CuentaScreen(
    perfil: PerfilUsuario,
    onCerrarSesion: () -> Unit
) {
    val mensaje = if (perfil.estado == "PENDIENTE_VINCULACION") {
        "Tu cuenta está pendiente de vinculación con una academia. " +
                "Todavía no tienes acceso a sus equipos ni a sus datos."
    } else {
        "Tu cuenta está activa. Los módulos se habilitarán según " +
                "tu rol y las relaciones autorizadas con la academia."
    }

    MensajeAcceso(
        titulo = "Hola, ${perfil.nombre}",
        mensaje = "Rol: ${perfil.rol.etiqueta}\n\n$mensaje",
        onCerrarSesion = onCerrarSesion
    )
}

@Composable
private fun MensajeAcceso(
    titulo: String,
    mensaje: String,
    cargando: Boolean = false,
    onCerrarSesion: (() -> Unit)? = null
) {
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
                Text(
                    text = titulo,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(mensaje)

                if (cargando) {
                    CircularProgressIndicator()
                }

                onCerrarSesion?.let { cerrar ->
                    Button(
                        onClick = cerrar,
                        enabled = !cargando,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cerrar sesión")
                    }
                }
            }
        }
    }
}