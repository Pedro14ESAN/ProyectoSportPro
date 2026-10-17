package com.example.proyectosportpro.features.auth.presentation

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

@Composable
fun FechaNacimientoField(
    fecha: LocalDate?,
    habilitado: Boolean,
    onFechaSeleccionada: (LocalDate) -> Unit
) {
    val context = LocalContext.current

    OutlinedButton(
        enabled = habilitado,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            val hoy = LocalDate.now(ZoneId.of("America/Lima"))
            val inicial = fecha ?: hoy.minusYears(18)

            val dialogo = DatePickerDialog(
                context,
                { _, anio, mes, dia ->
                    onFechaSeleccionada(
                        LocalDate.of(anio, mes + 1, dia)
                    )
                },
                inicial.year,
                inicial.monthValue - 1,
                inicial.dayOfMonth
            )

            // Impide seleccionar días posteriores a hoy.
            val limite = Calendar.getInstance().apply {
                set(
                    hoy.year,
                    hoy.monthValue - 1,
                    hoy.dayOfMonth,
                    23,
                    59,
                    59
                )
            }

            dialogo.datePicker.maxDate = limite.timeInMillis
            dialogo.show()
        }
    ) {
        Text(
            text = fecha?.format(
                DateTimeFormatter.ofPattern("dd/MM/yyyy")
            ) ?: "Seleccionar fecha de nacimiento"
        )
    }
}