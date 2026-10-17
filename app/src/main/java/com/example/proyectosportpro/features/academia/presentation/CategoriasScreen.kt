package com.example.proyectosportpro.features.academia.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CategoriasScreen(
    onVolver: () -> Unit,
    viewModel: CategoriasViewModel = viewModel()
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    val azul = Color(0xFF102E3C)
    val verde = Color(0xFFB1F568)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(azul)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SPORTPRO",
            color = verde,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Text(
            text = "Categorías de la academia",
            color = Color.White,
            fontSize = 24.sp,
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
                    text = "Crear categoría",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = estado.nombre,
                    onValueChange = viewModel::cambiarNombre,
                    label = { Text("Nombre de la categoría") },
                    placeholder = { Text("Ejemplo: Sub-15") },
                    singleLine = true,
                    enabled = !estado.cargando && !estado.pendiente,
                    modifier = Modifier.fillMaxWidth()
                )

                if (estado.pendiente) {
                    Text(
                        "Hay una creación pendiente de confirmar. " +
                                "Comprobaremos el resultado antes de repetirla."
                    )
                }

                estado.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                estado.mensaje?.let {
                    Text(it)
                }

                if (estado.cargando) {
                    CircularProgressIndicator()
                    Text("Consultando el servidor…")
                }

                Button(
                    onClick = viewModel::guardar,
                    enabled = !estado.cargando &&
                            estado.nombre.trim().length in 2..60,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = verde,
                        contentColor = azul
                    )
                ) {
                    Text(
                        if (estado.pendiente) {
                            "Comprobar y reintentar"
                        } else {
                            "Guardar categoría"
                        }
                    )
                }

                OutlinedButton(
                    onClick = viewModel::actualizar,
                    enabled = !estado.cargando,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Actualizar")
                }

                HorizontalDivider()

                Text(
                    text = "Categorías registradas",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                if (
                    estado.categorias.isEmpty() &&
                    !estado.cargando &&
                    estado.error == null
                ) {
                    Text("Esta academia todavía no tiene categorías.")
                }

                estado.categorias.forEach { categoria ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = categoria.nombre,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                if (categoria.activa) "Activa" else "Inactiva"
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onVolver,
                    enabled = !estado.cargando,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Volver a academias")
                }
            }
        }
    }
}