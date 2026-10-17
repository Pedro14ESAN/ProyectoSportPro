package com.example.proyectosportpro.features.academia.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.proyectosportpro.ui.theme.SportProColors

@Composable
fun EquipoCard(
    nombre: String,
    categoria: String,
    activo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    entrenador: String? = null
) {
    val distintivo = categoria
        .filter { it.isDigit() }
        .take(2)
        .ifBlank {
            categoria.trim().take(1).uppercase().ifBlank { "E" }
        }

    val colorTexto = if (activo) {
        SportProColors.TextPrimary
    } else {
        SportProColors.TextSecondary
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = SportProColors.Border
        ),
        colors = CardDefaults.cardColors(
            containerColor = SportProColors.Surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = if (activo) {
                            SportProColors.SelectedGreen
                        } else {
                            SportProColors.SoftGreen
                        },
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = distintivo,
                    color = colorTexto,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = nombre,
                    color = colorTexto,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = if (entrenador.isNullOrBlank()) {
                        categoria
                    } else {
                        "$categoria · DT $entrenador"
                    },
                    color = SportProColors.TextSecondary,
                    fontSize = 12.sp
                )

                if (!activo) {
                    Surface(
                        color = SportProColors.InactiveBackground,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "INACTIVO",
                            modifier = Modifier.padding(
                                horizontal = 8.dp,
                                vertical = 4.dp
                            ),
                            color = SportProColors.InactiveText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = "→",
                color = SportProColors.Field,
                fontSize = 22.sp
            )
        }
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFFFAFBF8,
    widthDp = 390
)
@Composable
private fun EquipoCardPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EquipoCard(
                nombre = "SportPro Norte A",
                categoria = "Sub-15",
                activo = true,
                entrenador = "Daniel Mendoza",
                onClick = {}
            )

            EquipoCard(
                nombre = "SportPro Femenino",
                categoria = "Femenino",
                activo = false,
                onClick = {}
            )
        }
    }
}