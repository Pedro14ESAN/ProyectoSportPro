package com.example.proyectosportpro.features.auth.data

import com.example.proyectosportpro.features.auth.domain.PerfilUsuario
import com.example.proyectosportpro.features.auth.domain.RolUsuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.example.proyectosportpro.features.auth.domain.PoliticaRegistro
import com.example.proyectosportpro.features.auth.domain.TipoRegistro


import com.google.firebase.firestore.Source
import java.time.LocalDate
import java.time.ZoneId

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun crearCuenta(
        correo: String,
        contrasena: String
    ): String {
        check(auth.currentUser == null) {
            "Ya existe una sesión. Completa el registro o cierra sesión."
        }

        require(contrasena.length >= 8) {
            "La contraseña debe tener al menos 8 caracteres."
        }

        val resultado = auth.createUserWithEmailAndPassword(
            correo.trim(),
            contrasena
        ).await()

        return requireNotNull(resultado.user?.uid) {
            "No se pudo obtener el identificador del usuario."
        }
    }

    suspend fun guardarPerfil(perfil: PerfilUsuario) {
        val usuario = checkNotNull(auth.currentUser) {
            "Debes autenticarte antes de guardar el perfil."
        }

        require(perfil.uid == usuario.uid) {
            "El perfil no corresponde al usuario autenticado."
        }

        val nombre = perfil.nombre.trim()

        require(nombre.length in 2..100) {
            "El nombre debe tener entre 2 y 100 caracteres."
        }

        require(perfil.rol in RolUsuario.rolesDeRegistro) {
            "Este rol no está permitido para el registro."
        }

        require(perfil.aceptoTerminos) {
            "Debes aceptar los términos y la política de privacidad."
        }

        require(perfil.estado == "PENDIENTE_VINCULACION") {
            "El estado inicial del perfil no es válido."
        }

        val correoAutenticado = checkNotNull(usuario.email) {
            "La cuenta no tiene un correo asociado."
        }

        val datos = mapOf(
            "uid" to usuario.uid,
            "nombre" to nombre,
            "correo" to correoAutenticado,
            "rol" to perfil.rol.name,
            "fechaNacimiento" to perfil.fechaNacimiento,
            "edadVerificada" to false,
            "estado" to perfil.estado,
            "aceptoTerminos" to perfil.aceptoTerminos,
            "fechaCreacion" to FieldValue.serverTimestamp()
        )

        val documento = firestore.collection("users")
            .document(usuario.uid)

        // Si el primer intento ya guardó el perfil,
        // un reintento no lo modifica ni lo duplica.
        firestore.runTransaction { transaccion ->
            val existente = transaccion.get(documento)

            if (!existente.exists()) {
                transaccion.set(documento, datos)
            }

            true
        }.await()
    }

    suspend fun iniciarSesion(
        correo: String,
        contrasena: String
    ) {
        require(correo.isNotBlank() && contrasena.isNotBlank()) {
            "Ingresa tu correo y contraseña."
        }

        auth.signInWithEmailAndPassword(
            correo.trim(),
            contrasena
        ).await()
    }

    suspend fun obtenerPerfilActual(): PerfilUsuario? {
        val usuario = checkNotNull(auth.currentUser) {
            "No hay una sesión iniciada."
        }

        val documento = firestore.collection("users")
            .document(usuario.uid)
            .get(Source.SERVER)
            .await()

        // La cuenta puede existir en Authentication y todavía
        // necesitar completar la creación de su perfil.
        if (!documento.exists()) {
            return null
        }

        check(documento.getString("uid") == usuario.uid) {
            "El perfil no corresponde a la cuenta autenticada."
        }

        val nombre = checkNotNull(documento.getString("nombre")) {
            "El perfil no tiene nombre."
        }

        val correo = checkNotNull(documento.getString("correo")) {
            "El perfil no tiene correo."
        }

        val codigoRol = checkNotNull(documento.getString("rol")) {
            "El perfil no tiene un rol registrado."
        }

        val rol = RolUsuario.entries.firstOrNull {
            it.name == codigoRol
        } ?: error("El perfil tiene un rol desconocido.")

        val estado = checkNotNull(documento.getString("estado")) {
            "El perfil no tiene un estado registrado."
        }

        val aceptoTerminos = checkNotNull(
            documento.getBoolean("aceptoTerminos")
        ) {
            "El perfil no tiene registrada la aceptación de términos."
        }

        return PerfilUsuario(
            uid = usuario.uid,
            nombre = nombre,
            correo = correo,
            rol = rol,
            fechaNacimiento = documento.getString("fechaNacimiento")
                .orEmpty(),
            estado = estado,
            aceptoTerminos = aceptoTerminos
        )
    }

    fun obtenerUidActual(): String? {
        return auth.currentUser?.uid
    }

    fun cerrarSesion() {
        auth.signOut()
    }

    suspend fun consultarFechaNacimiento(): String? {
        val usuario = checkNotNull(auth.currentUser) {
            "No hay una sesión iniciada."
        }

        val documento = firestore.collection("users")
            .document(usuario.uid)
            .get(Source.SERVER)
            .await()

        check(documento.exists()) {
            "La cuenta todavía no tiene un perfil guardado."
        }

        return documento.getString("fechaNacimiento")
    }

    suspend fun completarFechaNacimiento(fecha: LocalDate) {
        val usuario = checkNotNull(auth.currentUser) {
            "No hay una sesión iniciada."
        }

        val hoy = LocalDate.now(ZoneId.of("America/Lima"))

        require(!fecha.isAfter(hoy)) {
            "La fecha de nacimiento no puede ser futura."
        }

        val referencia = firestore.collection("users")
            .document(usuario.uid)

        firestore.runTransaction { transaccion ->
            val documento = transaccion.get(referencia)

            check(documento.exists()) {
                "La cuenta todavía no tiene un perfil guardado."
            }

            val fechaExistente = documento.getString("fechaNacimiento")

            if (fechaExistente == null) {
                transaccion.update(
                    referencia,
                    mapOf(
                        "fechaNacimiento" to fecha.toString(),
                        "edadVerificada" to false
                    )
                )
            } else {
                // Permite repetir la misma operación si se perdió
                // la respuesta del primer intento, sin cambiar la fecha.
                check(fechaExistente == fecha.toString()) {
                    "El perfil ya tiene otra fecha registrada."
                }
            }

            true
        }.await()
    }
    // Función para enviar un enlace de recuperación de contraseña
    suspend fun enviarRecuperacionContrasena(correo: String) {
        auth.setLanguageCode("es")
        auth.sendPasswordResetEmail(correo.trim()).await()
    }

    suspend fun completarPerfilPendiente(
        nombre: String,
        fechaNacimiento: LocalDate,
        rol: RolUsuario,
        aceptoTerminos: Boolean
    ): PerfilUsuario {
        val usuario = checkNotNull(auth.currentUser) {
            "Debes iniciar sesión para completar tu perfil."
        }

        // Consulta al servidor. Un error de conexión no significa
        // que el perfil no exista.
        val perfilExistente = obtenerPerfilActual()

        if (perfilExistente != null) {
            return perfilExistente
        }

        val nombreLimpio = nombre.trim()

        require(nombreLimpio.length in 2..100) {
            "El nombre debe tener entre 2 y 100 caracteres."
        }

        require(rol in RolUsuario.rolesDeRegistro) {
            "Selecciona un rol permitido."
        }

        require(aceptoTerminos) {
            "Debes aceptar los términos y la política de privacidad."
        }

        val evaluacion = PoliticaRegistro.evaluar(
            fechaNacimiento = fechaNacimiento,
            rol = rol
        )

        // Conserva la política actual: el registro de menores
        // seguirá bloqueado hasta implementar el flujo de apoderados.
        require(evaluacion.tipo == TipoRegistro.ADULTO) {
            evaluacion.mensaje
        }

        check(auth.currentUser?.uid == usuario.uid) {
            "La sesión cambió. Vuelve a iniciar sesión."
        }

        val correo = checkNotNull(usuario.email) {
            "La cuenta no tiene un correo asociado."
        }

        val perfil = PerfilUsuario(
            uid = usuario.uid,
            nombre = nombreLimpio,
            correo = correo,
            rol = rol,
            fechaNacimiento = fechaNacimiento.toString(),
            estado = "PENDIENTE_VINCULACION",
            aceptoTerminos = aceptoTerminos
        )

        guardarPerfil(perfil)

        check(auth.currentUser?.uid == usuario.uid) {
            "La sesión cambió. Vuelve a iniciar sesión."
        }

        // Devuelve lo que realmente quedó guardado en el servidor.
        return checkNotNull(obtenerPerfilActual()) {
            "No se pudo confirmar el perfil. Vuelve a intentarlo."
        }
    }
}