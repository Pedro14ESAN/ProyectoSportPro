package com.example.proyectosportpro.features.partidos.presentation

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.proyectosportpro.features.partidos.domain.DatosEvento
import com.example.proyectosportpro.features.partidos.domain.EstadoPartido
import com.example.proyectosportpro.features.partidos.domain.EstadoResumen
import com.example.proyectosportpro.features.partidos.domain.EventoPartido
import com.example.proyectosportpro.features.partidos.domain.JugadorEvento
import com.example.proyectosportpro.features.partidos.domain.LadoEvento
import com.example.proyectosportpro.features.partidos.domain.Partido
import com.example.proyectosportpro.features.partidos.domain.TipoEvento
import com.example.proyectosportpro.features.partidos.domain.ValidacionEvento
import com.example.proyectosportpro.ui.theme.SportProColors

/** Evento abierto en el formulario: sin evento es un registro nuevo. */
private data class FormularioEvento(val evento: EventoPartido? = null)

@Composable
fun PartidoEnVivoScreen(
    partido: Partido,
    puedeGestionar: Boolean,
    esAdministrador: Boolean,
    onVolver: () -> Unit,
    viewModel: PartidoEnVivoViewModel = viewModel(key = "envivo_${partido.academiaId}_${partido.id}")
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var formulario by remember { mutableStateOf<FormularioEvento?>(null) }
    var anularDe by remember { mutableStateOf<EventoPartido?>(null) }
    var confirmarFinal by remember { mutableStateOf(false) }
    LaunchedEffect(partido.academiaId, partido.id) {
        viewModel.iniciar(partido, puedeGestionar, esAdministrador)
    }
    BackHandler(onBack = onVolver)
    val actual = estado.partido ?: partido

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SportProColors.Background).safeDrawingPadding(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TextButton(onClick = onVolver, contentPadding = PaddingValues(0.dp)) { Text("← Partidos") }
            if (estado.sinConexion) {
                Aviso(SportProColors.InactiveBackground,
                    "📡 Sin conexión. Ves lo guardado en este teléfono y tus cambios se enviarán al volver la conexión.")
            }
            if (estado.pendientes > 0) {
                Aviso(SportProColors.SoftGreen, "⏳ ${estado.pendientes} cambio(s) pendientes de sincronizar.")
            }
        }
        item { TarjetaMarcador(actual, estado) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (estado.puedeIniciar) {
                    Button(onClick = viewModel::iniciarPartido, shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SportProColors.Field)) {
                        Text("▶ Iniciar partido")
                    }
                }
                if (estado.puedeRegistrar) {
                    Button(onClick = { viewModel.limpiarAvisos(); formulario = FormularioEvento() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SportProColors.Field)) {
                        Text("+ Registrar evento")
                    }
                }
                if (estado.puedeFinalizar) {
                    OutlinedButton(onClick = { confirmarFinal = true }) { Text("Finalizar partido") }
                }
            }
            if (puedeGestionar && actual.estado == EstadoPartido.PROGRAMADO && !esAdministrador) {
                Text("El administrador inicia el partido; después podrás registrar los eventos.",
                    color = SportProColors.TextSecondary, fontSize = 13.sp)
            }
            estado.error?.let { Text(it, color = SportProColors.Alert) }
            estado.mensaje?.let { Text(it, color = SportProColors.Field, fontWeight = FontWeight.SemiBold) }
        }
        item {
            Text("Cronología", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                fontSize = 22.sp, color = SportProColors.Navy)
            if (estado.incompletos > 0 && puedeGestionar) {
                Text("⚠ ${estado.incompletos} evento(s) con datos incompletos. Pulsa Corregir para completarlos.",
                    color = SportProColors.Alert, fontSize = 13.sp)
            }
            if (estado.cargando) LinearProgressIndicator(Modifier.fillMaxWidth(), color = SportProColors.Field)
        }
        if (!estado.cargando && estado.eventos.isEmpty()) {
            item {
                Tarjeta {
                    Text(when (actual.estado) {
                        EstadoPartido.PROGRAMADO -> "Los eventos aparecerán aquí cuando empiece el partido."
                        EstadoPartido.CANCELADO -> "El partido fue cancelado."
                        else -> "Todavía no hay eventos registrados."
                    }, color = SportProColors.TextSecondary)
                }
            }
        }
        items(estado.eventos, key = { it.id }) { evento ->
            TarjetaEvento(
                evento = evento,
                partido = actual,
                gestionable = estado.puedeRegistrar,
                onCorregir = { viewModel.limpiarAvisos(); formulario = FormularioEvento(evento) },
                onAnular = { viewModel.limpiarAvisos(); anularDe = evento },
                onRestaurar = { viewModel.corregir(evento, evento.datos) }
            )
        }
        if (actual.estado == EstadoPartido.FINALIZADO) {
            item { SeccionResumen(estado, viewModel) }
        }
    }

    formulario?.let { abierto ->
        EventoDialog(
            evento = abierto.evento,
            partido = actual,
            jugadores = estado.jugadores,
            jugadoresNoDisponibles = estado.jugadoresNoDisponibles,
            validar = viewModel::validar,
            onReintentarJugadores = viewModel::cargarJugadores,
            onCerrar = { formulario = null },
            onGuardar = { datos ->
                formulario = null
                val existente = abierto.evento
                if (existente == null) viewModel.registrar(datos) else viewModel.corregir(existente, datos)
            }
        )
    }
    anularDe?.let { evento ->
        AnularDialog(evento, viewModel::validarMotivo, onCerrar = { anularDe = null }) { motivo ->
            anularDe = null
            viewModel.anular(evento, motivo)
        }
    }
    if (confirmarFinal) {
        AlertDialog(
            onDismissRequest = { confirmarFinal = false }, containerColor = SportProColors.Surface,
            title = { Text("¿Finalizar partido?", color = SportProColors.Navy) },
            text = { Text("El resultado quedará ${actual.nombreEquipo} ${estado.marcador} ${actual.rival}, " +
                    "calculado con los eventos registrados. Podrás corregir eventos después.") },
            confirmButton = {
                TextButton(onClick = { confirmarFinal = false; viewModel.finalizarPartido() }) {
                    Text("Finalizar", color = SportProColors.Field)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarFinal = false }) { Text("Volver") } }
        )
    }
}

@Composable
private fun TarjetaMarcador(partido: Partido, estado: PartidoEnVivoUiState) {
    Tarjeta {
        Surface(shape = RoundedCornerShape(8.dp), color = if (partido.estado == EstadoPartido.EN_JUEGO)
            SportProColors.Signal else SportProColors.SoftGreen) {
            Text(if (partido.estado == EstadoPartido.EN_JUEGO) "● EN VIVO" else partido.estado.etiqueta,
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = SportProColors.Navy,
                fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(partido.nombreEquipo, Modifier.weight(1f), fontWeight = FontWeight.Bold,
                color = SportProColors.Navy, textAlign = TextAlign.Center)
            Text(if (partido.estado == EstadoPartido.PROGRAMADO) "vs" else estado.marcador.toString(),
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp,
                color = SportProColors.Navy)
            Text(partido.rival, Modifier.weight(1f), fontWeight = FontWeight.Bold,
                color = SportProColors.Navy, textAlign = TextAlign.Center)
        }
        Text("📍 ${partido.lugar} · ${partido.condicion.etiqueta}", color = SportProColors.TextSecondary)
    }
}

@Composable
private fun TarjetaEvento(
    evento: EventoPartido,
    partido: Partido,
    gestionable: Boolean,
    onCorregir: () -> Unit,
    onAnular: () -> Unit,
    onRestaurar: () -> Unit
) {
    val d = evento.datos
    val tachado = if (evento.anulado) TextDecoration.LineThrough else null
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(d.minuto?.let { "$it'" } ?: "–'", fontWeight = FontWeight.ExtraBold,
                color = SportProColors.Navy, fontSize = 18.sp, modifier = Modifier.width(44.dp))
            Column(Modifier.weight(1f)) {
                Text("${d.tipo.icono} ${d.tipo.etiqueta}", fontWeight = FontWeight.Bold,
                    color = SportProColors.Navy, textDecoration = tachado)
                Text(listOfNotNull(
                    if (d.lado == LadoEvento.PROPIO) partido.nombreEquipo else partido.rival,
                    d.jugador?.nombre
                ).joinToString(" · "), color = SportProColors.TextSecondary, textDecoration = tachado)
                if (d.detalle.isNotBlank()) Text(d.detalle, color = SportProColors.Navy, fontSize = 13.sp)
            }
        }
        val etiquetas = buildList {
            if (evento.pendiente) add("⏳ Pendiente de sincronizar")
            if (evento.anulado) add("🚫 Anulado: ${evento.motivoAnulacion.orEmpty()}")
            else if (evento.faltantes.isNotEmpty()) add("⚠ Falta ${evento.faltantes.joinToString(" y ")}")
            if (evento.version > 1) add("✎ Corregido (versión ${evento.version})")
        }
        etiquetas.forEach {
            Text(it, fontSize = 12.sp, color = if (it.startsWith("🚫") || it.startsWith("⚠"))
                SportProColors.Alert else SportProColors.TextSecondary)
        }
        if (gestionable) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (evento.anulado) {
                    TextButton(onClick = onRestaurar) { Text("Restaurar") }
                } else {
                    TextButton(onClick = onCorregir) { Text("Corregir") }
                    TextButton(onClick = onAnular) { Text("Anular", color = SportProColors.Alert) }
                }
            }
        }
    }
}

@Composable
private fun SeccionResumen(estado: PartidoEnVivoUiState, viewModel: PartidoEnVivoViewModel) {
    Tarjeta {
        Text("✨ Resumen del partido", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = 20.sp, color = SportProColors.Navy)
        val borrador = estado.borrador
        val resumen = estado.resumenVisible
        when {
            estado.puedeGestionar && borrador != null -> {
                Text("Revisa y corrige el texto. Nadie más lo verá hasta que lo apruebes.",
                    color = SportProColors.TextSecondary, fontSize = 13.sp)
                OutlinedTextField(value = borrador, onValueChange = viewModel::cambiarBorrador,
                    minLines = 6, modifier = Modifier.fillMaxWidth(), enabled = !estado.guardandoResumen)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.guardarResumen(aprobar = true) }, enabled = !estado.guardandoResumen,
                        colors = ButtonDefaults.buttonColors(containerColor = SportProColors.Field)) {
                        Text("Aprobar y publicar")
                    }
                    OutlinedButton(onClick = { viewModel.guardarResumen(aprobar = false) },
                        enabled = !estado.guardandoResumen) { Text("Guardar borrador") }
                    TextButton(onClick = viewModel::descartarBorrador, enabled = !estado.guardandoResumen) {
                        Text("Descartar")
                    }
                }
            }
            resumen != null -> {
                Text(if (resumen.estado == EstadoResumen.APROBADO) "Aprobado por el cuerpo técnico"
                    else "Borrador: solo lo ve el cuerpo técnico",
                    color = SportProColors.Field, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(resumen.texto, color = SportProColors.Navy)
                if (resumen.generadoConIA) {
                    Text("Redactado con IA a partir de los eventos registrados.",
                        color = SportProColors.TextSecondary, fontSize = 12.sp)
                }
            }
            estado.puedeGestionar -> Text("Genera un borrador con IA a partir de los eventos registrados.",
                color = SportProColors.TextSecondary)
            else -> Text("El cuerpo técnico aún no publica el resumen de este partido.",
                color = SportProColors.TextSecondary)
        }
        if (estado.puedeGestionar && borrador == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::generarResumen, enabled = !estado.generando,
                    colors = ButtonDefaults.buttonColors(containerColor = SportProColors.Navy)) {
                    Text(if (estado.resumen == null) "Generar con IA" else "Volver a generar")
                }
                if (estado.resumen != null) {
                    OutlinedButton(onClick = viewModel::editarResumen) { Text("Editar") }
                }
            }
            if (estado.generando) LinearProgressIndicator(Modifier.fillMaxWidth(), color = SportProColors.Field)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventoDialog(
    evento: EventoPartido?,
    partido: Partido,
    jugadores: List<JugadorEvento>,
    jugadoresNoDisponibles: Boolean,
    validar: (DatosEvento) -> String?,
    onReintentarJugadores: () -> Unit,
    onCerrar: () -> Unit,
    onGuardar: (DatosEvento) -> Unit
) {
    val inicial = evento?.datos
    var tipo by remember { mutableStateOf(inicial?.tipo ?: TipoEvento.GOL) }
    var lado by remember { mutableStateOf(inicial?.lado ?: LadoEvento.PROPIO) }
    var minuto by remember { mutableStateOf(inicial?.minuto?.toString().orEmpty()) }
    var jugador by remember { mutableStateOf(inicial?.jugador) }
    var detalle by remember { mutableStateOf(inicial?.detalle.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    // El jugador ya guardado sigue disponible aunque ahora no esté en el plantel cargado.
    val opciones = (listOfNotNull(inicial?.jugador) + jugadores).distinctBy { it.uid }

    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = SportProColors.Surface,
        title = { Text(if (evento == null) "Registrar evento" else "Corregir evento",
            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = SportProColors.Navy) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TipoEvento.entries.forEach { opcion ->
                        FilterChip(selected = tipo == opcion, onClick = { tipo = opcion; error = null },
                            label = { Text("${opcion.icono} ${opcion.etiqueta}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SportProColors.Navy,
                                selectedLabelColor = SportProColors.Surface))
                    }
                }
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LadoEvento.entries.forEach { opcion ->
                        Row(Modifier.selectable(selected = lado == opcion, role = Role.RadioButton, onClick = {
                            lado = opcion; if (opcion == LadoEvento.RIVAL) jugador = null; error = null
                        }), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = lado == opcion, onClick = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (opcion == LadoEvento.PROPIO) partido.nombreEquipo else partido.rival)
                        }
                    }
                }
                OutlinedTextField(value = minuto, onValueChange = { minuto = it.filter(Char::isDigit).take(3); error = null },
                    label = { Text("Minuto (puedes dejarlo vacío)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                if (lado == LadoEvento.PROPIO) {
                    Text("Jugador", fontWeight = FontWeight.SemiBold, color = SportProColors.Navy)
                    Column(Modifier.selectableGroup()) {
                        (listOf<JugadorEvento?>(null) + opciones).forEach { opcion ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp)
                                .selectable(selected = jugador?.uid == opcion?.uid, role = Role.RadioButton,
                                    onClick = { jugador = opcion; error = null }),
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = jugador?.uid == opcion?.uid, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(opcion?.nombre ?: "Sin jugador (completar después)")
                            }
                        }
                    }
                    if (jugadoresNoDisponibles) {
                        Text("No pudimos cargar el plantel. Puedes guardar sin jugador y completarlo luego.",
                            color = SportProColors.TextSecondary, fontSize = 12.sp)
                        TextButton(onClick = onReintentarJugadores) { Text("Reintentar plantel") }
                    }
                }
                OutlinedTextField(value = detalle,
                    onValueChange = { detalle = it.take(ValidacionEvento.MAX_DETALLE); error = null },
                    label = { Text("Detalle (opcional)") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = SportProColors.Alert) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val valorMinuto = ValidacionEvento.minuto(minuto)
                val datos = DatosEvento(tipo, lado, valorMinuto,
                    if (lado == LadoEvento.PROPIO) jugador else null, detalle)
                val problema = validar(datos)
                if (problema != null) error = problema else onGuardar(datos)
            }) { Text("Guardar", color = SportProColors.Field) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } }
    )
}

@Composable
private fun AnularDialog(
    evento: EventoPartido,
    validar: (String) -> String?,
    onCerrar: () -> Unit,
    onAnular: (String) -> Unit
) {
    var motivo by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onCerrar, containerColor = SportProColors.Surface,
        title = { Text("Anular ${evento.datos.tipo.etiqueta.lowercase()}", color = SportProColors.Navy) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("El evento seguirá visible, tachado, con el motivo y quién lo anuló.")
                OutlinedTextField(value = motivo, onValueChange = { motivo = it.take(140); error = null },
                    label = { Text("Motivo") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = SportProColors.Alert) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val problema = validar(motivo)
                if (problema != null) error = problema else onAnular(motivo)
            }) { Text("Anular", color = SportProColors.Alert) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Volver") } }
    )
}

@Composable
private fun Aviso(fondo: androidx.compose.ui.graphics.Color, texto: String) {
    Surface(color = fondo, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(texto, Modifier.padding(12.dp), color = SportProColors.Navy, fontSize = 13.sp)
    }
}

@Composable
private fun Tarjeta(contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SportProColors.Surface),
        border = BorderStroke(1.dp, SportProColors.Border)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            contenido()
        }
    }
}
