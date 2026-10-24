package com.example.proyectosportpro.core.navigation

import android.net.Uri
import com.example.proyectosportpro.features.academia.presentation.SolicitudesApoderadoAdminScreen
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.example.proyectosportpro.features.academia.presentation.InvitacionApoderadoScreen
import com.example.proyectosportpro.features.academia.presentation.MisJugadoresApoderadoScreen
import com.example.proyectosportpro.features.academia.presentation.PlantelScreen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.proyectosportpro.features.academia.presentation.AcademiasScreen
import com.example.proyectosportpro.features.academia.presentation.CategoriasScreen
import com.example.proyectosportpro.features.academia.presentation.EquiposScreen
import com.example.proyectosportpro.features.academia.presentation.MisEquiposScreen
import com.example.proyectosportpro.features.academia.presentation.MisEquiposJugadorScreen
import com.example.proyectosportpro.features.academia.presentation.SolicitudesAdminScreen
import com.example.proyectosportpro.features.auth.domain.RolUsuario
import com.example.proyectosportpro.features.auth.presentation.CompletarFechaScreen
import com.example.proyectosportpro.features.auth.presentation.CompletarPerfilScreen
import com.example.proyectosportpro.features.auth.presentation.LoginScreen
import com.example.proyectosportpro.features.auth.presentation.LoginViewModel
import com.example.proyectosportpro.features.auth.presentation.RegistroScreen
import com.example.proyectosportpro.features.jugador.presentation.PerfilJugadorScreen
import com.example.proyectosportpro.features.partidos.presentation.PartidosScreen
import com.example.proyectosportpro.ui.theme.SportProColors

private object Rutas {
    const val COMPROBANDO = "comprobando"
    const val LOGIN = "login"
    const val REGISTRO = "registro"
    const val COMPLETAR_FECHA = "completar_fecha"
    const val PERFIL_FALTANTE = "perfil_faltante"
    const val INICIO = "inicio"
    const val ACADEMIAS = "academias"
    const val PARTIDOS = "partidos"
    const val COMUNIDAD = "comunidad"
    const val PERFIL = "perfil"
    const val CATEGORIAS = "categorias/{academiaId}"
    const val SOLICITUDES = "solicitudes/{academiaId}"
    const val APODERADOS = "apoderados/{academiaId}"
    const val EQUIPOS = "equipos/{academiaId}"
    const val PLANTEL = "plantel/{academiaId}/{equipoId}"
    const val INVITAR_PAD = "invitar_apoderado/{academiaId}/{jugadorUid}?nombreJugador={nombreJugador}"
}

private data class PestanaPrincipal(
    val ruta: String,
    val titulo: String,
    val icono: ImageVector
)

private val pestanas = listOf(
    PestanaPrincipal(Rutas.INICIO, "Inicio", Icons.Default.Home),
    PestanaPrincipal(Rutas.ACADEMIAS, "Equipos", Icons.Default.List),
    PestanaPrincipal(Rutas.PARTIDOS, "Partidos", Icons.Default.DateRange),
    PestanaPrincipal(Rutas.COMUNIDAD, "Comunidad", Icons.Default.FavoriteBorder),
    PestanaPrincipal(Rutas.PERFIL, "Perfil", Icons.Default.Person)
)

@Composable
fun SportProNavigation(loginViewModel: LoginViewModel = viewModel()) {
    val navController = rememberNavController()
    val estado by loginViewModel.uiState.collectAsStateWithLifecycle()
    val entrada by navController.currentBackStackEntryAsState()
    val rutaActual = entrada?.destination?.route
    val perfil = estado.perfil

    val accesoCompleto = estado.sesionRevisada && !estado.cargando &&
            perfil != null && !estado.requiereCrearPerfil &&
            !estado.requiereFechaNacimiento
    val esAdministrador = accesoCompleto &&
            perfil?.rol == RolUsuario.ADM && perfil.estado == "ACTIVO"
    val puedeVerPlantel = esAdministrador || (accesoCompleto &&
            perfil?.rol == RolUsuario.DT && perfil.estado in listOf("ACTIVO", "PENDIENTE_VINCULACION"))
    val rutasPrincipales = pestanas.map { it.ruta }
    val rutasAdministracion = listOf(Rutas.CATEGORIAS, Rutas.EQUIPOS, Rutas.SOLICITUDES, Rutas.INVITAR_PAD, Rutas.APODERADOS)
    val mostrarBarra = accesoCompleto &&
            (rutaActual in rutasPrincipales ||
                    (esAdministrador && rutaActual in rutasAdministracion) ||
                    (puedeVerPlantel && rutaActual == Rutas.PLANTEL))

    LaunchedEffect(
        estado.sesionRevisada, estado.cargando, estado.perfil,
        estado.requiereCrearPerfil, estado.requiereFechaNacimiento, rutaActual
    ) {
        if (!estado.sesionRevisada || estado.cargando) return@LaunchedEffect
        val destino = when {
            estado.requiereCrearPerfil -> Rutas.PERFIL_FALTANTE
            estado.requiereFechaNacimiento -> Rutas.COMPLETAR_FECHA
            perfil != null -> when {
                rutaActual == Rutas.PLANTEL && puedeVerPlantel -> Rutas.PLANTEL
                rutaActual in rutasPrincipales -> checkNotNull(rutaActual)
                esAdministrador && rutaActual in rutasAdministracion ->
                    checkNotNull(rutaActual)
                else -> Rutas.INICIO
            }
            rutaActual == Rutas.REGISTRO -> Rutas.REGISTRO
            else -> Rutas.LOGIN
        }
        if (rutaActual != destino) {
            navController.navigate(destino) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    fun abrirPlantel(academiaId: String, equipoId: String) {
        navController.navigate("plantel/${Uri.encode(academiaId)}/${Uri.encode(equipoId)}") {
            launchSingleTop = true
        }
    }

    fun abrirPestana(ruta: String) {
        if (rutaActual == ruta) return
        navController.navigate(ruta) {
            popUpTo(Rutas.INICIO) { inclusive = false }
            launchSingleTop = true
        }
    }

    val colores = lightColorScheme(
        primary = SportProColors.Field,
        onPrimary = SportProColors.Surface,
        secondary = SportProColors.Navy,
        secondaryContainer = SportProColors.SelectedGreen,
        onSecondaryContainer = SportProColors.Navy,
        background = SportProColors.Background,
        onBackground = SportProColors.TextPrimary,
        surface = SportProColors.Surface,
        onSurface = SportProColors.TextPrimary,
        error = SportProColors.Alert,
        outline = SportProColors.Border
    )

    MaterialTheme(colorScheme = colores) {
        Scaffold(
            containerColor = SportProColors.Background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (mostrarBarra) {
                    val seleccionada = when (rutaActual) {
                        Rutas.CATEGORIAS, Rutas.EQUIPOS, Rutas.SOLICITUDES, Rutas.PLANTEL, Rutas.INVITAR_PAD, Rutas.APODERADOS -> Rutas.ACADEMIAS
                        else -> rutaActual
                    }
                    Column {
                        HorizontalDivider(color = SportProColors.Border)
                        NavigationBar(
                            containerColor = SportProColors.Surface,
                            tonalElevation = 0.dp
                        ) {
                            pestanas.forEach { pestana ->
                                NavigationBarItem(
                                    selected = seleccionada == pestana.ruta,
                                    onClick = { abrirPestana(pestana.ruta) },
                                    icon = {
                                        Icon(pestana.icono, contentDescription = null,
                                            modifier = Modifier.size(24.dp))
                                    },
                                    label = {
                                        Text(pestana.titulo, fontSize = 10.sp, maxLines = 1)
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = SportProColors.Field,
                                        selectedTextColor = SportProColors.Field,
                                        indicatorColor = SportProColors.SelectedGreen,
                                        unselectedIconColor = SportProColors.TextSecondary,
                                        unselectedTextColor = SportProColors.TextSecondary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Rutas.COMPROBANDO,
                modifier = Modifier.fillMaxSize().padding(padding)
                    .consumeWindowInsets(padding)
            ) {
                composable(Rutas.COMPROBANDO) {
                    PantallaBase("SportPro") {
                        CircularProgressIndicator()
                        Text("Comprobando tu sesión…")
                    }
                }
                composable(Rutas.LOGIN) {
                    LoginScreen(
                        viewModel = loginViewModel,
                        onCrearCuenta = {
                            navController.navigate(Rutas.REGISTRO) { launchSingleTop = true }
                        }
                    )
                }
                composable(Rutas.REGISTRO) {
                    RegistroScreen(onRegistroCompletado = { loginViewModel.revisarSesion() })
                }
                composable(Rutas.COMPLETAR_FECHA) {
                    if (estado.requiereFechaNacimiento) {
                        CompletarFechaScreen(onFechaGuardada = { loginViewModel.revisarSesion() })
                    }
                }
                composable(Rutas.PERFIL_FALTANTE) {
                    if (estado.requiereCrearPerfil) {
                        CompletarPerfilScreen(
                            onPerfilCompletado = { loginViewModel.revisarSesion() },
                            onCerrarSesion = { loginViewModel.cerrarSesion() }
                        )
                    }
                }
                composable(Rutas.INICIO) {
                    if (accesoCompleto && perfil != null) {
                        PantallaBase("Hola, ${perfil.nombre}") {
                            Text(perfil.rol.etiqueta, color = SportProColors.Field,
                                fontWeight = FontWeight.Bold)
                            if (perfil.estado == "ACTIVO") {
                                Text("Bienvenido a SportPro.")
                                if (esAdministrador) {
                                    Text("Gestiona tus academias, categorías y equipos.")
                                    Button(
                                        onClick = { abrirPestana(Rutas.ACADEMIAS) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Ver mis academias y equipos") }
                                } else {
                                    Text("El acceso a equipos se habilitará según tus asignaciones en la academia.")
                                }
                            } else {
                                Text("Tu cuenta todavía no tiene acceso a los datos deportivos de una academia.")
                                Text("Estado: ${perfil.estado}")
                            }
                        }
                    }
                }
                composable(Rutas.ACADEMIAS) {
                    if (accesoCompleto && perfil != null) {
                        if (esAdministrador) {
                            AcademiasScreen(
                                usuarioId = perfil.uid,
                                onVerCategorias = { id ->
                                    navController.navigate("categorias/$id") { launchSingleTop = true }
                                },
                                onVerEquipos = { id ->
                                    navController.navigate("equipos/$id") { launchSingleTop = true }
                                },
                                onVerSolicitudes = { id ->
                                    navController.navigate("solicitudes/$id") { launchSingleTop = true }
                                },
                                onVerApoderados = { id ->
                                    navController.navigate("apoderados/${Uri.encode(id)}") { launchSingleTop = true }
                                },
                                onCerrarSesion = { loginViewModel.cerrarSesion() }
                            )
                        } else if (
                            perfil.rol == RolUsuario.DT &&
                            perfil.estado in listOf("ACTIVO", "PENDIENTE_VINCULACION")
                        ) {
                            MisEquiposScreen(usuarioId = perfil.uid, nombreUsuario = perfil.nombre,
                                onVerPlantel = { academia, equipo -> abrirPlantel(academia, equipo) })
                        } else if (perfil.rol == RolUsuario.JUG &&
                            perfil.estado in listOf("ACTIVO", "PENDIENTE_VINCULACION")) {
                            MisEquiposJugadorScreen(
                                usuarioId = perfil.uid,
                                nombreUsuario = perfil.nombre
                            )
                        } else if (perfil.rol == RolUsuario.PAD &&
                            perfil.estado in listOf("ACTIVO", "PENDIENTE_VINCULACION")) {
                            MisJugadoresApoderadoScreen(
                                usuarioId = perfil.uid,
                                nombreUsuario = perfil.nombre
                            )
                        } else {
                            PantallaBase("Equipos") {
                                Text(if (perfil.estado == "ACTIVO") {
                                    "La vista de equipos asignados a tu cuenta está pendiente de implementar."
                                } else {
                                    "Necesitas una vinculación autorizada con una academia para acceder a sus equipos."
                                })
                            }
                        }
                    }
                }
                composable(Rutas.PARTIDOS) {
                    if (accesoCompleto && perfil != null) {
                        if (esAdministrador || (perfil.rol in listOf(RolUsuario.DT, RolUsuario.JUG) &&
                                    perfil.estado in listOf("ACTIVO", "PENDIENTE_VINCULACION"))) {
                            PartidosScreen(usuarioId = perfil.uid, rol = perfil.rol)
                        } else {
                            PantallaBase("Partidos") {
                                Text("Aquí verás el calendario y los resultados de los equipos de tus jugadores.")
                                Text("Esta vista para apoderados se habilitará en la próxima versión.")
                            }
                        }
                    }
                }
                composable(Rutas.COMUNIDAD) {
                    if (accesoCompleto) {
                        PantallaBase("Comunidad") {
                            Text("Esta sección está pendiente de implementar.")
                            Text("Aquí aparecerán las publicaciones y novedades de la comunidad.")
                        }
                    }
                }
                composable(Rutas.PERFIL) {
                    if (accesoCompleto && perfil != null) {
                        if (perfil.rol == RolUsuario.JUG &&
                            perfil.estado in listOf("ACTIVO", "PENDIENTE_VINCULACION")) {
                            PerfilJugadorScreen(
                                usuarioId = perfil.uid,
                                nombreUsuario = perfil.nombre,
                                onCerrarSesion = { loginViewModel.cerrarSesion() }
                            )
                        } else {
                            PantallaBase("Mi perfil") {
                                Text(perfil.nombre, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text("Rol: ${perfil.rol.etiqueta}")
                                Text("Estado: ${perfil.estado}")
                                if (perfil.rol == RolUsuario.PAD) {
                                    Text("El acceso a los datos de un jugador requiere un vínculo verificado y aprobado por la academia.")
                                    Text("Comparte tu identificador con el administrador para que prepare una invitación privada para tu cuenta.")
                                    val clipboard = LocalClipboardManager.current
                                    var identificadorCopiado by remember(perfil.uid) { mutableStateOf(false) }
                                    SelectionContainer { Text(perfil.uid, fontSize = 13.sp) }
                                    OutlinedButton(onClick = {
                                        clipboard.setText(AnnotatedString(perfil.uid))
                                        identificadorCopiado = true
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (identificadorCopiado) "✓ Identificador copiado" else "Copiar mi identificador")
                                    }
                                    Text("Cuando recibas la invitación, entra en Equipos para enviar tu solicitud y ver a tus jugadores.")
                                }
                                HorizontalDivider(color = SportProColors.Border)
                                Button(
                                    onClick = { loginViewModel.cerrarSesion() },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Cerrar sesión") }
                            }
                        }
                    }
                }
                composable(
                    Rutas.PLANTEL,
                    arguments = listOf(
                        navArgument("academiaId") { type = NavType.StringType },
                        navArgument("equipoId") { type = NavType.StringType }
                    )
                ) { entradaPlantel ->
                    if (puedeVerPlantel && perfil != null) {
                        PlantelScreen(
                            puedeGestionar = esAdministrador,
                            onVolver = { navController.popBackStack() },
                            onInvitarApoderado = { jugadorUid, nombre ->
                                val academiaId = entradaPlantel.arguments?.getString("academiaId")
                                if (esAdministrador && academiaId != null) {
                                    navController.navigate(
                                        "invitar_apoderado/${Uri.encode(academiaId)}/${Uri.encode(jugadorUid)}?nombreJugador=${Uri.encode(nombre)}"
                                    ) { launchSingleTop = true }
                                }
                            },
                            viewModel = viewModel(key = "plantel_${perfil.uid}")
                        )
                    }
                }
                composable(
                    Rutas.INVITAR_PAD,
                    arguments = listOf(
                        navArgument("academiaId") { type = NavType.StringType },
                        navArgument("jugadorUid") { type = NavType.StringType },
                        navArgument("nombreJugador") {
                            type = NavType.StringType
                            defaultValue = "Jugador seleccionado"
                        }
                    )
                ) { entradaInvitacion ->
                    if (esAdministrador && perfil != null) {
                        InvitacionApoderadoScreen(
                            nombreJugador = entradaInvitacion.arguments?.getString("nombreJugador")
                                ?: "Jugador seleccionado",
                            onVolver = { navController.popBackStack() },
                            viewModel = viewModel(key = "invitacion_apoderado_${perfil.uid}")
                        )
                    }
                }
                composable(
                    Rutas.APODERADOS,
                    arguments = listOf(navArgument("academiaId") { type = NavType.StringType })
                ) {
                    if (esAdministrador && perfil != null) {
                        SolicitudesApoderadoAdminScreen(
                            onVolver = { navController.popBackStack() },
                            viewModel = viewModel(key = "revision_apoderados_${perfil.uid}")
                        )
                    }
                }
                composable(
                    Rutas.SOLICITUDES,
                    arguments = listOf(navArgument("academiaId") { type = NavType.StringType })
                ) {
                    if (esAdministrador) {
                        SolicitudesAdminScreen(onVolver = { navController.popBackStack() })
                    }
                }
                composable(
                    Rutas.CATEGORIAS,
                    arguments = listOf(navArgument("academiaId") { type = NavType.StringType })
                ) {
                    if (esAdministrador) {
                        CategoriasScreen(onVolver = { navController.popBackStack() })
                    }
                }
                composable(
                    Rutas.EQUIPOS,
                    arguments = listOf(navArgument("academiaId") { type = NavType.StringType })
                ) { entradaEquipos ->
                    val academiaId = entradaEquipos.arguments?.getString("academiaId")
                    if (esAdministrador && academiaId != null) {
                        EquiposScreen(
                            onVolver = { navController.popBackStack() },
                            onVerCategorias = {
                                navController.navigate("categorias/$academiaId") { launchSingleTop = true }
                            },
                            onVerPlantel = { equipoId -> abrirPlantel(academiaId, equipoId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PantallaBase(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("SPORTPRO", color = SportProColors.Navy,
            fontFamily = FontFamily.Serif, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        Text(titulo, color = SportProColors.Navy,
            fontFamily = FontFamily.Serif, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
        contenido()
    }
}
