package com.example.proyectosportpro.features.jugador.data

import com.example.proyectosportpro.features.jugador.domain.PerfilJugador
import com.example.proyectosportpro.features.jugador.domain.PosicionJugador
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

class PerfilJugadorRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun uidActual(): String {
        return checkNotNull(auth.currentUser?.uid) {
            "Debes iniciar sesión."
        }
    }

    private fun comprobarSesion(uid: String) {
        check(auth.currentUser?.uid == uid) {
            "La sesión cambió. Vuelve a iniciar sesión."
        }
    }

    private fun referencia(uid: String) =
        firestore.collection("perfilesJugadores").document(uid)

    suspend fun obtenerMiPerfil(): PerfilJugador? {
        val uid = uidActual()

        val documento = referencia(uid)
            .get(Source.SERVER)
            .await()

        comprobarSesion(uid)

        if (!documento.exists()) return null

        check(documento.getString("uid") == uid) {
            "El perfil no corresponde a tu cuenta."
        }

        val codigoPosicion = documento.getString("posicion")

        val posicion = codigoPosicion?.let { codigo ->
            checkNotNull(
                PosicionJugador.entries.firstOrNull {
                    it.name == codigo
                }
            ) {
                "La posición guardada no es válida."
            }
        }

        val altura = documento.getLong("alturaCm")?.let {
            check(it in 50L..250L) {
                "La altura guardada no es válida."
            }
            it.toInt()
        }

        val peso = documento.getDouble("pesoKg")?.also {
            check(it.isFinite() && it in 10.0..300.0) {
                "El peso guardado no es válido."
            }
        }

        return PerfilJugador(
            uid = uid,
            posicion = posicion,
            alturaCm = altura,
            pesoKg = peso,
            fotoPath = documento.getString("fotoPath"),
            telefonoContacto =
                documento.getString("telefonoContacto").orEmpty(),
            nombreContactoEmergencia =
                documento.getString("nombreContactoEmergencia").orEmpty(),
            telefonoContactoEmergencia =
                documento.getString("telefonoContactoEmergencia").orEmpty()
        )
    }

    suspend fun guardarMiPerfil(perfil: PerfilJugador) {
        val uid = uidActual()

        require(perfil.uid == uid) {
            "Solo puedes editar tu propio perfil."
        }

        val telefono = normalizarTelefono(perfil.telefonoContacto)
        val nombreEmergencia = perfil.nombreContactoEmergencia
            .trim()
            .replace(Regex("\\s+"), " ")
        val telefonoEmergencia =
            normalizarTelefono(perfil.telefonoContactoEmergencia)

        require(perfil.alturaCm == null || perfil.alturaCm in 50..250) {
            "Ingresa una altura entre 50 y 250 cm, o deja el campo vacío."
        }

        require(
            perfil.pesoKg == null ||
                    (perfil.pesoKg.isFinite() && perfil.pesoKg in 10.0..300.0)
        ) {
            "Ingresa un peso entre 10 y 300 kg, o deja el campo vacío."
        }

        validarTelefono(telefono, "El teléfono de contacto")
        validarTelefono(telefonoEmergencia, "El teléfono de emergencia")

        require(
            nombreEmergencia.isEmpty() ||
                    nombreEmergencia.length in 2..100
        ) {
            "El nombre del contacto de emergencia debe tener entre 2 y 100 caracteres."
        }

        require(
            nombreEmergencia.isEmpty() == telefonoEmergencia.isEmpty()
        ) {
            "Completa el nombre y el teléfono del contacto de emergencia."
        }

        val cuentaRef = firestore.collection("users").document(uid)
        val perfilRef = referencia(uid)

        firestore.runTransaction { transaccion ->
            comprobarSesion(uid)

            val cuenta = transaccion.get(cuentaRef)
            val existente = transaccion.get(perfilRef)

            check(
                cuenta.exists() &&
                        cuenta.getString("uid") == uid &&
                        cuenta.getString("rol") == "JUG"
            ) {
                "Esta función está disponible para cuentas de jugador."
            }

            check(
                cuenta.getString("estado") in
                        listOf("ACTIVO", "PENDIENTE_VINCULACION")
            ) {
                "Tu cuenta no está habilitada para editar el perfil."
            }

            if (existente.exists()) {
                check(existente.getString("uid") == uid) {
                    "El perfil no corresponde a tu cuenta."
                }
            }

            val datos = mutableMapOf<String, Any?>(
                "uid" to uid,
                "posicion" to perfil.posicion?.name,
                "alturaCm" to perfil.alturaCm,
                "pesoKg" to perfil.pesoKg,
                "telefonoContacto" to telefono,
                "nombreContactoEmergencia" to nombreEmergencia,
                "telefonoContactoEmergencia" to telefonoEmergencia,
                "actualizadoPor" to uid,
                "fechaActualizacion" to FieldValue.serverTimestamp()
            )

            if (existente.exists()) {
                // La foto tendrá su propio flujo de carga.
                // Esta edición conserva la foto que ya exista.
                transaccion.update(perfilRef, datos)
            } else {
                datos["fotoPath"] = null
                datos["fechaCreacion"] = FieldValue.serverTimestamp()
                transaccion.set(perfilRef, datos)
            }

            true
        }.await()

        comprobarSesion(uid)
    }

    private fun normalizarTelefono(valor: String): String {
        return valor.trim().replace(Regex("[\\s()\\-]"), "")
    }

    private fun validarTelefono(valor: String, etiqueta: String) {
        require(
            valor.isEmpty() ||
                    Regex("^\\+?[0-9]{7,15}$").matches(valor)
        ) {
            "$etiqueta debe tener entre 7 y 15 dígitos; puede comenzar con +."
        }
    }
}