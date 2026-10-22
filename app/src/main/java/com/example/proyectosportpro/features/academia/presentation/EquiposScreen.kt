package com.example.proyectosportpro.features.academia.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.proyectosportpro.features.academia.domain.Equipo
import com.example.proyectosportpro.ui.theme.SportProColors

@Composable
fun EquiposScreen(
    onVolver: () -> Unit,
    onVerCategorias: () -> Unit,
    onVerPlantel: (String) -> Unit,
    viewModel: EquiposViewModel = viewModel()
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var detalle by remember { mutableStateOf<Equipo?>(null) }

    // Actualiza también al regresar desde categorías.
    LaunchedEffect(Unit) {
        viewModel.actualizar()
    }

    val colores = lightColorScheme(
        primary = SportProColors.Field,
        onPrimary = SportProColors.Surface,
        secondary = SportProColors.Navy,
        background = SportProColors.Background,
        surface = SportProColors.Surface,
        onSurface = SportProColors.TextPrimary,
        onBackground = SportProColors.TextPrimary,
        outline = SportProColors.Border,
        error = SportProColors.Alert
    )

    MaterialTheme(colorScheme = colores) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SportProColors.Background
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "⚽ SPORTPRO",
                        color = SportProColors.Navy,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp
                    )

                    TextButton(onClick = onVolver) {
                        Text("← Volver a academias")
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Equipos",
                            color = SportProColors.Navy,
                            fontSize = 32.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Button(
                            onClick = viewModel::abrirFormulario,
                            enabled = !estado.cargando && !estado.guardando,
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Text("+", fontSize = 28.sp)
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.horizontalScroll(
                            rememberScrollState()
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FiltroEquipos.entries.forEach { filtro ->
                            FilterChip(
                                selected = estado.filtroEstado == filtro,
                                onClick = {
                                    viewModel.cambiarFiltroEstado(filtro)
                                },
                                label = { Text(filtro.etiqueta) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SportProColors.Navy,
                                    selectedLabelColor = SportProColors.Surface,
                                    containerColor = SportProColors.SoftGreen
                                )
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.horizontalScroll(
                            rememberScrollState()
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = estado.filtroCategoriaId == null,
                            onClick = {
                                viewModel.cambiarFiltroCategoria(null)
                            },
                            label = { Text("Todas") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor =
                                    SportProColors.SelectedGreen
                            )
                        )

                        estado.categorias.forEach { categoria ->
                            FilterChip(
                                selected =
                                    estado.filtroCategoriaId == categoria.id,
                                onClick = {
                                    viewModel.cambiarFiltroCategoria(categoria.id)
                                },
                                label = { Text(categoria.nombre) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor =
                                        SportProColors.SelectedGreen
                                )
                            )
                        }
                    }
                }

                item {
                    if (estado.cargando) {
                        CircularProgressIndicator()
                    }

                    if (!estado.formularioAbierto) {
                        estado.error?.let {
                            Text(it, color = SportProColors.Alert)
                        }
                    }

                    estado.mensaje?.let {
                        Text(it, color = SportProColors.Field)
                    }

                    TextButton(
                        onClick = viewModel::actualizar,
                        enabled = !estado.cargando && !estado.guardando
                    ) {
                        Text("Actualizar")
                    }
                }

                if (
                    !estado.cargando &&
                    estado.error == null &&
                    estado.equiposVisibles.isEmpty()
                ) {
                    item {
                        Text(
                            text = "No hay equipos para estos filtros.",
                            color = SportProColors.TextSecondary
                        )
                    }
                }

                items(
                    items = estado.equiposVisibles,
                    key = { it.id }
                ) { equipo ->
                    val categoria = estado.categorias.firstOrNull {
                        it.id == equipo.categoriaId
                    }

                    EquipoCard(
                        nombre = equipo.nombre,
                        categoria = categoria?.nombre ?: "Categoría no disponible",
                        activo = equipo.activo,
                        onClick = { viewModel.limpiarAvisos(); detalle = equipo }
                    )
                }

                item {
                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CATEGORÍAS",
                            fontWeight = FontWeight.Bold,
                            color = SportProColors.TextSecondary,
                            fontSize = 12.sp
                        )

                        TextButton(
                            onClick = onVerCategorias,
                            enabled = !estado.guardando
                        ) {
                            Text("+ Nueva / Gestionar")
                        }
                    }

                    Text(
                        text = estado.categorias.joinToString(" · ") {
                            if (it.activa) it.nombre
                            else "${it.nombre} (inactiva)"
                        }.ifBlank {
                            "Crea una categoría antes de registrar equipos."
                        },
                        color = SportProColors.TextSecondary
                    )
                }
            }
        }

        if (estado.formularioAbierto) {
            AlertDialog(
                onDismissRequest = viewModel::cerrarFormulario,
                containerColor = SportProColors.Surface,
                titleContentColor = SportProColors.Navy,
                textContentColor = SportProColors.TextSecondary,
                title = { Text("Crear equipo") },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val editable = !estado.guardando &&
                                !estado.solicitudPendiente

                        OutlinedTextField(
                            value = estado.nombre,
                            onValueChange = viewModel::cambiarNombre,
                            label = { Text("Nombre del equipo") },
                            placeholder = { Text("Ejemplo: SportPro Norte A") },
                            enabled = editable,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Categoría",
                            fontWeight = FontWeight.Bold
                        )

                        val activas = estado.categorias.filter { it.activa }

                        if (activas.isEmpty()) {
                            Text(
                                "No hay categorías activas. " +
                                        "Cierra este formulario y crea una categoría."
                            )
                        }

                        activas.forEach { categoria ->
                            FilterChip(
                                selected = estado.categoriaSeleccionadaId ==
                                        categoria.id,
                                onClick = {
                                    viewModel.seleccionarCategoria(categoria.id)
                                },
                                enabled = editable,
                                label = { Text(categoria.nombre) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = SportProColors.SoftGreen,
                                    labelColor = SportProColors.Navy,
                                    selectedContainerColor = SportProColors.SelectedGreen,
                                    selectedLabelColor = SportProColors.Navy
                                )
                            )
                        }

                        if (estado.solicitudPendiente) {
                            Text(
                                "Conservamos los datos del envío. " +
                                        "Al reintentar comprobaremos si ya se guardó."
                            )
                        }

                        estado.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }

                        if (estado.guardando) {
                            CircularProgressIndicator()
                            Text("Guardando equipo…")
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = viewModel::guardar,
                        enabled = !estado.guardando && !estado.cargando
                    ) {
                        Text(
                            if (estado.solicitudPendiente) "Reintentar"
                            else "Guardar"
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = viewModel::cerrarFormulario,
                        enabled = !estado.guardando &&
                                !estado.solicitudPendiente
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        detalle?.let { seleccionado ->
            val equipo = estado.equipos.firstOrNull { it.id == seleccionado.id }
            if (equipo != null) {
                val categoria = estado.categorias.firstOrNull { it.id == equipo.categoriaId }
                var nombreEditado by remember(equipo.id, equipo.nombre) {
                    mutableStateOf(equipo.nombre)
                }
                var dtSeleccionadoUid by remember(equipo.id, equipo.dtUid) {
                    mutableStateOf(equipo.dtUid)
                }
                var menuDtAbierto by remember(equipo.id) { mutableStateOf(false) }
                val cambioDt = dtSeleccionadoUid != equipo.dtUid
                val normalizado = nombreEditado.trim().replace(Regex(" +"), " ")
                val hayCambios = normalizado != equipo.nombre
                val ocupado = estado.guardando || estado.cargando || estado.solicitudPendiente
                AlertDialog(
                    onDismissRequest = { if (!estado.guardando) detalle = null },
                    containerColor = SportProColors.Surface,
                    titleContentColor = SportProColors.Navy,
                    textContentColor = SportProColors.TextSecondary,
                    title = {
                        Text("Gestionar equipo", fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Column(
                            modifier = Modifier.heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = nombreEditado,
                                onValueChange = { nombreEditado = it; viewModel.limpiarAvisos() },
                                label = { Text("Nombre del equipo") },
                                enabled = !ocupado && !cambioDt,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Categoría: " + (categoria?.nombre ?: "No disponible"))
                            Text("Estado: " + if (equipo.activo) "Activo" else "Inactivo")
                            OutlinedButton(
                                onClick = { detalle = null; onVerPlantel(equipo.id) },
                                enabled = !ocupado && !hayCambios && !cambioDt,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("⚽ Ver plantel") }
                            HorizontalDivider(color = SportProColors.Border)
                            Text("Director técnico", color = SportProColors.Navy,
                                fontWeight = FontWeight.Bold)
                            val nombreDt = estado.directoresTecnicos.firstOrNull {
                                it.uid == dtSeleccionadoUid
                            }?.nombre ?: if (dtSeleccionadoUid == null) "Sin DT asignado"
                            else "DT no disponible en la lista"
                            Box(Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { menuDtAbierto = true },
                                    enabled = !ocupado && !hayCambios,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text("$nombreDt ▾") }
                                DropdownMenu(expanded = menuDtAbierto,
                                    onDismissRequest = { menuDtAbierto = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Sin DT asignado") },
                                        enabled = !ocupado,
                                        onClick = {
                                            dtSeleccionadoUid = null
                                            menuDtAbierto = false
                                            viewModel.limpiarAvisos()
                                        }
                                    )
                                    estado.directoresTecnicos.forEach { dt ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(dt.nombre)
                                                    if (estado.directoresTecnicos.count {
                                                            it.nombre == dt.nombre
                                                        } > 1) {
                                                        Text("ID: ${dt.uid}", fontSize = 10.sp)
                                                    }
                                                }
                                            },
                                            enabled = !ocupado && equipo.activo,
                                            onClick = {
                                                dtSeleccionadoUid = dt.uid
                                                menuDtAbierto = false
                                                viewModel.limpiarAvisos()
                                            }
                                        )
                                    }
                                }
                            }
                            if (estado.directoresTecnicos.isEmpty()) {
                                Text("No hay DT aprobados con vínculo activo. Revisa las solicitudes " +
                                        "de la academia y actualiza la lista.", fontSize = 12.sp)
                            }
                            if (!equipo.activo) {
                                Text("Reactiva el equipo para asignarle un DT. Puedes retirar " +
                                        "la asignación actual.", fontSize = 12.sp)
                            }
                            Button(
                                enabled = !ocupado && !hayCambios && cambioDt &&
                                        (dtSeleccionadoUid == null || equipo.activo),
                                onClick = {
                                    viewModel.asignarDirectorTecnico(
                                        equipo.id, equipo.dtUid, dtSeleccionadoUid
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SportProColors.Field,
                                    contentColor = SportProColors.Surface
                                )
                            ) { Text("Guardar asignación") }
                            if (cambioDt) {
                                Text("Tienes una asignación sin guardar.", fontSize = 12.sp,
                                    color = SportProColors.Navy)
                            }
                            HorizontalDivider()
                            Text(if (equipo.activo) {
                                "Puedes desactivar el equipo conservando su información e historial."
                            } else {
                                "Puedes reactivarlo para que vuelva a aparecer en Activos."
                            })
                            OutlinedButton(
                                enabled = !ocupado && !hayCambios && !cambioDt,
                                onClick = {
                                    viewModel.cambiarEstadoEquipo(equipo.id, !equipo.activo)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (equipo.activo) "Desactivar equipo" else "Reactivar equipo",
                                    color = if (equipo.activo) SportProColors.Alert else SportProColors.Field
                                )
                            }
                            if (hayCambios) {
                                Text("Guarda el nombre o restaura el anterior antes de cambiar el estado.",
                                    fontSize = 12.sp)
                            }
                            estado.error?.let { Text(it, color = SportProColors.Alert) }
                            estado.mensaje?.let { Text(it, color = SportProColors.Field) }
                            if (estado.guardando) {
                                CircularProgressIndicator()
                                Text("Guardando cambios…")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !ocupado && hayCambios && !cambioDt,
                            onClick = {
                                viewModel.editarNombreEquipo(equipo.id, equipo.nombre, nombreEditado)
                            }
                        ) { Text("Guardar nombre") }
                    },
                    dismissButton = {
                        TextButton(enabled = !estado.guardando, onClick = { detalle = null }) {
                            Text("Cerrar")
                        }
                    }
                )
            }
        }
    }
}
