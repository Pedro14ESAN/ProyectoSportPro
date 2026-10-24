package com.example.proyectosportpro.features.partidos.presentation

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.proyectosportpro.features.auth.domain.RolUsuario
import com.example.proyectosportpro.features.partidos.domain.CondicionPartido
import com.example.proyectosportpro.features.partidos.domain.DatosPartido
import com.example.proyectosportpro.features.partidos.domain.EquipoPartido
import com.example.proyectosportpro.features.partidos.domain.EstadoPartido
import com.example.proyectosportpro.features.partidos.domain.Partido
import com.example.proyectosportpro.ui.theme.SportProColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zonaLima: ZoneId = ZoneId.of("America/Lima")
private val formatoFecha: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE dd/MM/yyyy · HH:mm", Locale.forLanguageTag("es-PE")).withZone(zonaLima)

/** Formulario abierto: sin partido es una programación nueva. */
private data class FormularioPartido(val partido: Partido? = null)

@Composable
fun PartidosScreen(
    usuarioId: String,
    rol: RolUsuario,
    viewModel: PartidosViewModel = viewModel(key = "partidos_$usuarioId")
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var formulario by remember { mutableStateOf<FormularioPartido?>(null) }
    var resultadoDe by remember { mutableStateOf<Partido?>(null) }
    var cancelarDe by remember { mutableStateOf<Partido?>(null) }
    LaunchedEffect(usuarioId, rol) { viewModel.iniciar(rol) }
    val ocupado = estado.cargando || estado.guardando

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SportProColors.Background).safeDrawingPadding(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = SportProColors.Navy, shape = RoundedCornerShape(14.dp)) {
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        Text("⚽", fontSize = 22.sp)
                    }
                }
                Column {
                    Text("SPORTPRO", fontFamily = FontFamily.Serif, fontWeight = FontWeight.ExtraBold,
                        fontSize = 23.sp, color = SportProColors.Navy)
                    Text(rol.etiqueta.uppercase(Locale.forLanguageTag("es-PE")), fontSize = 10.sp,
                        color = SportProColors.TextSecondary)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Partidos", fontFamily = FontFamily.Serif, fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp, color = SportProColors.Navy)
            Text(when (rol) {
                RolUsuario.JUG -> "Calendario y resultados de tus equipos."
                RolUsuario.PAD -> "Calendario y resultados de los equipos de tus jugadores."
                else -> "Programa los partidos de tus equipos y registra sus resultados."
            },
                color = SportProColors.TextSecondary)
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FiltroPartidos.entries.forEach { opcion ->
                    FilterChip(selected = estado.filtro == opcion,
                        onClick = { viewModel.seleccionarFiltro(opcion) },
                        label = { Text(opcion.etiqueta) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SportProColors.Navy,
                            selectedLabelColor = SportProColors.Surface,
                            containerColor = SportProColors.SoftGreen))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = viewModel::actualizar, enabled = !ocupado) { Text("Actualizar") }
                Spacer(Modifier.weight(1f))
                if (estado.equiposGestionables.isNotEmpty()) {
                    Button(onClick = { formulario = FormularioPartido() }, enabled = !ocupado,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SportProColors.Field)) {
                        Text("+ Programar partido")
                    }
                }
            }
            if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth(), color = SportProColors.Field)
            estado.error?.let { Text(it, color = SportProColors.Alert) }
            estado.mensaje?.let { Text(it, color = SportProColors.Field, fontWeight = FontWeight.SemiBold) }
        }
        if (!estado.cargando && estado.error == null && estado.partidosVisibles.isEmpty()) {
            item {
                TarjetaPartido {
                    Text("📅 No hay partidos en este filtro", fontWeight = FontWeight.Bold,
                        color = SportProColors.Navy)
                    Text(when {
                        estado.equipos.isEmpty() && rol == RolUsuario.PAD ->
                            "Tus jugadores vinculados aún no están en un equipo. Revisa Mis jugadores en Equipos."
                        estado.equipos.isEmpty() ->
                            "Aún no tienes equipos asignados. Cuando formes parte de uno, verás aquí sus partidos."
                        estado.equiposGestionables.isNotEmpty() ->
                            "Pulsa Programar partido para agregar el próximo encuentro."
                        else -> "Tu DT o la academia publicarán aquí los próximos encuentros."
                    }, color = SportProColors.TextSecondary)
                }
            }
        }
        items(estado.partidosVisibles, key = { "${it.academiaId}/${it.id}" }) { partido ->
            val academia = estado.equipos.firstOrNull {
                it.academiaId == partido.academiaId && it.equipoId == partido.equipoId
            }?.academiaNombre
            TarjetaPartidoItem(
                partido = partido,
                academia = academia,
                gestionable = estado.puedeGestionar(partido),
                habilitado = !ocupado,
                onEditar = { formulario = FormularioPartido(partido) },
                onResultado = { resultadoDe = partido },
                onCancelar = { cancelarDe = partido }
            )
        }
    }

    formulario?.let { abierto ->
        FormularioPartidoDialog(
            partido = abierto.partido,
            equipos = estado.equiposGestionables,
            validar = viewModel::validar,
            onCerrar = { formulario = null },
            onGuardar = { equipo, datos ->
                formulario = null
                val existente = abierto.partido
                if (existente == null) viewModel.programar(equipo, datos)
                else viewModel.editar(existente, datos)
            }
        )
    }
    resultadoDe?.let { partido ->
        ResultadoDialog(partido = partido, onCerrar = { resultadoDe = null }, onGuardar = { gf, gc ->
            resultadoDe = null
            viewModel.registrarResultado(partido, gf, gc)
        })
    }
    cancelarDe?.let { partido ->
        AlertDialog(
            onDismissRequest = { cancelarDe = null }, containerColor = SportProColors.Surface,
            title = { Text("¿Cancelar partido?", color = SportProColors.Navy) },
            text = { Text("${partido.nombreEquipo} vs ${partido.rival}\n${formatoFecha.format(Instant.ofEpochMilli(partido.fechaHoraMillis))}\n\n" +
                    "El partido quedará como cancelado y se conservará en el historial.") },
            confirmButton = {
                TextButton(onClick = { cancelarDe = null; viewModel.cancelar(partido) }) {
                    Text("Cancelar partido", color = SportProColors.Alert)
                }
            },
            dismissButton = { TextButton(onClick = { cancelarDe = null }) { Text("Volver") } }
        )
    }
}

@Composable
private fun TarjetaPartidoItem(
    partido: Partido,
    academia: String?,
    gestionable: Boolean,
    habilitado: Boolean,
    onEditar: () -> Unit,
    onResultado: () -> Unit,
    onCancelar: () -> Unit
) {
    TarjetaPartido {
        Text(listOfNotNull(academia, partido.nombreEquipo).joinToString(" · "),
            color = SportProColors.Field, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("vs ${partido.rival}", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                fontSize = 22.sp, color = SportProColors.Navy, modifier = Modifier.weight(1f))
            partido.resultado?.let {
                Text(it, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = SportProColors.Navy)
            }
        }
        Text("🗓 ${formatoFecha.format(Instant.ofEpochMilli(partido.fechaHoraMillis))}",
            color = SportProColors.Navy)
        Text("📍 ${partido.lugar} · ${partido.condicion.etiqueta}", color = SportProColors.TextSecondary)
        Surface(shape = RoundedCornerShape(8.dp), color = when (partido.estado) {
            EstadoPartido.PROGRAMADO -> SportProColors.SoftGreen
            EstadoPartido.FINALIZADO -> SportProColors.SelectedGreen
            EstadoPartido.CANCELADO -> SportProColors.InactiveBackground
        }) {
            Text(partido.estado.etiqueta, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = if (partido.estado == EstadoPartido.CANCELADO) SportProColors.InactiveText
                else SportProColors.Navy, fontSize = 13.sp)
        }
        if (gestionable && partido.estado != EstadoPartido.CANCELADO) {
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onResultado, enabled = habilitado) {
                    Text(if (partido.estado == EstadoPartido.FINALIZADO) "Corregir resultado" else "Registrar resultado")
                }
                if (partido.estado == EstadoPartido.PROGRAMADO) {
                    OutlinedButton(onClick = onEditar, enabled = habilitado) { Text("Editar") }
                    TextButton(onClick = onCancelar, enabled = habilitado) {
                        Text("Cancelar", color = SportProColors.Alert)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormularioPartidoDialog(
    partido: Partido?,
    equipos: List<EquipoPartido>,
    validar: (String, String, Long?) -> String?,
    onCerrar: () -> Unit,
    onGuardar: (EquipoPartido, DatosPartido) -> Unit
) {
    val context = LocalContext.current
    val inicial = partido?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.fechaHoraMillis), zonaLima) }
    var equipo by remember {
        mutableStateOf(equipos.firstOrNull {
            partido != null && it.academiaId == partido.academiaId && it.equipoId == partido.equipoId
        } ?: equipos.singleOrNull())
    }
    var rival by remember { mutableStateOf(partido?.rival.orEmpty()) }
    var lugar by remember { mutableStateOf(partido?.lugar.orEmpty()) }
    var fecha by remember { mutableStateOf(inicial?.toLocalDate()) }
    var hora by remember { mutableStateOf(inicial?.toLocalTime()) }
    var condicion by remember { mutableStateOf(partido?.condicion ?: CondicionPartido.LOCAL) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = SportProColors.Surface,
        title = {
            Text(if (partido == null) "Programar partido" else "Editar partido",
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = SportProColors.Navy)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (partido == null) {
                    Text("Equipo", fontWeight = FontWeight.SemiBold, color = SportProColors.Navy)
                    Column(Modifier.selectableGroup()) {
                        equipos.forEach { opcion ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                .selectable(selected = equipo == opcion, role = Role.RadioButton,
                                    onClick = { equipo = opcion }),
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = equipo == opcion, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text("${opcion.nombre} · ${opcion.academiaNombre}")
                            }
                        }
                    }
                } else {
                    Text("Equipo: ${partido.nombreEquipo}", color = SportProColors.Navy)
                }
                OutlinedTextField(value = rival, onValueChange = { rival = it; error = null },
                    label = { Text("Rival") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = lugar, onValueChange = { lugar = it; error = null },
                    label = { Text("Lugar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                        val base = fecha ?: LocalDate.now(zonaLima)
                        DatePickerDialog(context, { _, anio, mes, dia ->
                            fecha = LocalDate.of(anio, mes + 1, dia); error = null
                        }, base.year, base.monthValue - 1, base.dayOfMonth).show()
                    }) { Text(fecha?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: "Fecha") }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                        val base = hora ?: LocalTime.of(10, 0)
                        TimePickerDialog(context, { _, h, m ->
                            hora = LocalTime.of(h, m); error = null
                        }, base.hour, base.minute, true).show()
                    }) { Text(hora?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "Hora") }
                }
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CondicionPartido.entries.forEach { opcion ->
                        Row(Modifier.selectable(selected = condicion == opcion, role = Role.RadioButton,
                            onClick = { condicion = opcion }), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = condicion == opcion, onClick = null)
                            Spacer(Modifier.width(6.dp))
                            Text(opcion.etiqueta)
                        }
                    }
                }
                error?.let { Text(it, color = SportProColors.Alert) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val f = fecha
                val h = hora
                val millis = if (f != null && h != null)
                    f.atTime(h).atZone(zonaLima).toInstant().toEpochMilli() else null
                val elegido = equipo
                val problema = if (elegido == null) "Selecciona el equipo." else validar(rival, lugar, millis)
                if (problema != null || elegido == null || millis == null) {
                    error = problema
                } else {
                    onGuardar(elegido, DatosPartido(rival, lugar, millis, condicion))
                }
            }) { Text("Guardar", color = SportProColors.Field) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } }
    )
}

@Composable
private fun ResultadoDialog(
    partido: Partido,
    onCerrar: () -> Unit,
    onGuardar: (Int, Int) -> Unit
) {
    var favor by remember { mutableStateOf(partido.golesFavor?.toString().orEmpty()) }
    var contra by remember { mutableStateOf(partido.golesContra?.toString().orEmpty()) }
    val gf = favor.toIntOrNull()?.takeIf { it in 0..99 }
    val gc = contra.toIntOrNull()?.takeIf { it in 0..99 }
    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = SportProColors.Surface,
        title = { Text("Resultado", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            color = SportProColors.Navy) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${partido.nombreEquipo} vs ${partido.rival}", color = SportProColors.Navy)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = favor, onValueChange = { favor = it.filter(Char::isDigit).take(2) },
                        label = { Text(partido.nombreEquipo) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f))
                    OutlinedTextField(value = contra, onValueChange = { contra = it.filter(Char::isDigit).take(2) },
                        label = { Text(partido.rival) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = gf != null && gc != null, onClick = {
                if (gf != null && gc != null) onGuardar(gf, gc)
            }) { Text("Guardar", color = SportProColors.Field) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } }
    )
}

@Composable
private fun TarjetaPartido(contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SportProColors.Surface),
        border = BorderStroke(1.dp, SportProColors.Border)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            contenido()
        }
    }
}
