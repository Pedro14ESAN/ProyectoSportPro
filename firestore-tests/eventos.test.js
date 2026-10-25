// Pruebas de las reglas de eventos en vivo y del resumen narrativo contra el emulador de Firestore.
import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import {
  doc, setDoc, getDoc, updateDoc, deleteDoc, collection, getDocs, writeBatch, serverTimestamp, Timestamp
} from 'firebase/firestore';

const A = 'acad1';
const P = `academias/${A}/partidos/p1`;
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-sportpro-eventos',
    firestore: { rules: readFileSync('../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 }
  });
});
after(async () => { await env.cleanup(); });

const partidoBase = (estado, extra = {}) => ({
  academiaId: A, equipoId: 'e1', nombreEquipo: 'Sub 12', rival: 'Leones', lugar: 'Estadio',
  fechaHora: Timestamp.now(), condicion: 'LOCAL', estado, golesFavor: null, golesContra: null,
  creadoPor: 'adm', actualizadoPor: 'adm', fechaCreacion: Timestamp.now(), fechaActualizacion: Timestamp.now(), ...extra
});

const ponerPartido = (estado, extra) => env.withSecurityRulesDisabled((c) =>
  setDoc(doc(c.firestore(), P), partidoBase(estado, extra)));

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (c) => {
    const db = c.firestore();
    for (const [uid, rol] of [['adm', 'ADM'], ['dt1', 'DT'], ['dt2', 'DT'], ['jug1', 'JUG'], ['jug2', 'JUG'], ['pad1', 'PAD']]) {
      await setDoc(doc(db, `users/${uid}`), { uid, rol, estado: 'ACTIVO', nombre: `Nombre ${uid}` });
    }
    await setDoc(doc(db, `academias/${A}`), { nombre: 'Academia Sol', activa: true, creadoPor: 'adm' });
    for (const [uid, rol] of [['dt1', 'DT'], ['dt2', 'DT'], ['jug1', 'JUG'], ['jug2', 'JUG']]) {
      await setDoc(doc(db, `academias/${A}/miembros/${uid}`), { uid, academiaId: A, rol, activo: true });
    }
    await setDoc(doc(db, `academias/${A}/equipos/e1`), { academiaId: A, nombre: 'Sub 12', activo: true, dtUid: 'dt1' });
    await setDoc(doc(db, `academias/${A}/equipos/e2`), { academiaId: A, nombre: 'Sub 14', activo: true, dtUid: 'dt2' });
    await setDoc(doc(db, `academias/${A}/equipos/e1/plantel/jug1`),
      { jugadorUid: 'jug1', academiaId: A, equipoId: 'e1', nombre: 'Ana Pérez', activo: true });
    await setDoc(doc(db, `academias/${A}/vinculosApoderados/jug1/apoderados/pad1`),
      { academiaId: A, jugadorUid: 'jug1', apoderadoUid: 'pad1', activo: true });
    await setDoc(doc(db, P), partidoBase('EN_JUEGO'));
  });
});

const como = (uid) => env.authenticatedContext(uid).firestore();

const evento = (uid, extra = {}) => ({
  partidoId: 'p1', tipo: 'GOL', lado: 'PROPIO', minuto: 12, jugadorUid: 'jug1', jugadorNombre: 'Ana Pérez',
  detalle: 'Remate cruzado', anulado: false, motivoAnulacion: null, version: 1,
  creadoPor: uid, actualizadoPor: uid, fechaCreacion: serverTimestamp(), fechaActualizacion: serverTimestamp(), ...extra
});
const registrar = (uid, id = 'ev1', extra) => setDoc(doc(como(uid), `${P}/eventos/${id}`), evento(uid, extra));

const historial = (uid, previo, extra = {}) => ({
  tipo: previo.tipo, lado: previo.lado, minuto: previo.minuto, jugadorUid: previo.jugadorUid,
  jugadorNombre: previo.jugadorNombre, detalle: previo.detalle, version: previo.version,
  anulado: previo.anulado, motivoAnulacion: previo.motivoAnulacion,
  reemplazadoPor: uid, fechaCambio: serverTimestamp(), ...extra
});

/** Corrección como la hace la app: historial y evento en la misma escritura. */
const corregir = (uid, cambios, { conHistorial = true, historialExtra = {}, partido } = {}) => {
  const db = como(uid);
  const previo = evento('dt1');
  const batch = writeBatch(db);
  if (conHistorial) batch.set(doc(db, `${P}/eventos/ev1/historial/1`), historial(uid, previo, historialExtra));
  batch.update(doc(db, `${P}/eventos/ev1`), { ...cambios, version: 2, actualizadoPor: uid, fechaActualizacion: serverTimestamp() });
  if (partido) batch.update(doc(db, P), { ...partido, actualizadoPor: uid, fechaActualizacion: serverTimestamp() });
  return batch.commit();
};

test('solo el administrador inicia el partido', async () => {
  await ponerPartido('PROGRAMADO');
  const iniciar = (uid) => updateDoc(doc(como(uid), P),
    { estado: 'EN_JUEGO', actualizadoPor: uid, fechaActualizacion: serverTimestamp() });
  await assertFails(iniciar('dt1'));
  await assertSucceeds(iniciar('adm'));
});

test('el DT finaliza el partido en juego y un partido cancelado no vuelve atrás', async () => {
  await assertSucceeds(updateDoc(doc(como('dt1'), P),
    { estado: 'FINALIZADO', golesFavor: 1, golesContra: 0, actualizadoPor: 'dt1', fechaActualizacion: serverTimestamp() }));
  await ponerPartido('CANCELADO');
  await assertFails(updateDoc(doc(como('adm'), P),
    { estado: 'PROGRAMADO', actualizadoPor: 'adm', fechaActualizacion: serverTimestamp() }));
});

test('el DT del equipo registra eventos solo con el partido en juego', async () => {
  await assertFails(registrar('dt2'));
  await assertFails(registrar('jug1'));
  await assertSucceeds(registrar('dt1'));
  await ponerPartido('PROGRAMADO');
  await assertFails(registrar('dt1', 'ev2'));
});

test('un evento puede quedar incompleto pero no con datos inválidos', async () => {
  await assertSucceeds(registrar('dt1', 'sinDatos', { minuto: null, jugadorUid: null, jugadorNombre: null }));
  await assertFails(registrar('dt1', 'minuto', { minuto: 200 }));
  await assertFails(registrar('dt1', 'ajeno', { jugadorUid: 'jug2', jugadorNombre: 'Otro' }));
  await assertFails(registrar('dt1', 'nombre', { jugadorNombre: 'Nombre falso' }));
  await assertFails(registrar('dt1', 'rival', { lado: 'RIVAL' }));
  await assertSucceeds(registrar('dt1', 'golRival', { lado: 'RIVAL', jugadorUid: null, jugadorNombre: null }));
  await assertFails(registrar('dt1', 'anulado', { anulado: true, motivoAnulacion: 'Error' }));
});

test('corregir exige guardar la versión anterior en el historial', async () => {
  await registrar('dt1');
  await assertFails(corregir('dt1', { minuto: 15 }, { conHistorial: false }));
  await assertFails(corregir('dt1', { minuto: 15 }, { historialExtra: { minuto: 99 } }));
  await assertSucceeds(corregir('dt1', { minuto: 15 }));
  const guardado = await getDoc(doc(como('dt1'), `${P}/eventos/ev1/historial/1`));
  if (guardado.data().minuto !== 12) throw new Error('El historial no conserva el minuto original');
  await assertFails(updateDoc(doc(como('dt1'), `${P}/eventos/ev1/historial/1`), { minuto: 1 }));
});

test('anular exige motivo y nadie borra eventos', async () => {
  await registrar('dt1');
  await assertFails(corregir('dt1', { anulado: true, motivoAnulacion: 'x' }));
  await assertSucceeds(corregir('dt1', { anulado: true, motivoAnulacion: 'Fuera de juego' }));
  await assertFails(deleteDoc(doc(como('adm'), `${P}/eventos/ev1`)));
});

test('con el partido finalizado, la corrección actualiza también el resultado', async () => {
  await registrar('dt1');
  await ponerPartido('FINALIZADO', { golesFavor: 1, golesContra: 0 });
  await assertSucceeds(corregir('dt1', { anulado: true, motivoAnulacion: 'Gol anulado por el árbitro' },
    { partido: { golesFavor: 0, golesContra: 0 } }));
});

test('jugadores y apoderados con acceso siguen la cronología en tiempo real', async () => {
  await registrar('dt1');
  const cronologia = (uid) => getDocs(collection(como(uid), `${P}/eventos`));
  await assertSucceeds(cronologia('jug1'));
  await assertFails(cronologia('jug2'));
  await assertFails(cronologia('pad1'));
  await setDoc(doc(como('pad1'), `academias/${A}/accesosApoderado/pad1`),
    { apoderadoUid: 'pad1', academiaId: A, jugadorUid: 'jug1', fechaActualizacion: serverTimestamp() });
  await assertSucceeds(cronologia('pad1'));
});

const resumen = (uid, estado, extra = {}) => ({
  academiaId: A, partidoId: 'p1', texto: 'Sub 12 ganó un partido muy disputado ante Leones.',
  estado, generadoConIA: true, modelo: 'gemini', actualizadoPor: uid, fechaActualizacion: serverTimestamp(), ...extra
});
const guardarResumen = (uid, estado, extra) =>
  setDoc(doc(como(uid), `${P}/resumen/actual`), resumen(uid, estado, extra));

test('el resumen se escribe solo con el partido finalizado y por el cuerpo técnico', async () => {
  await assertFails(guardarResumen('dt1', 'BORRADOR'));
  await ponerPartido('FINALIZADO', { golesFavor: 1, golesContra: 0 });
  await assertFails(guardarResumen('jug1', 'BORRADOR'));
  await assertFails(guardarResumen('dt2', 'BORRADOR'));
  await assertFails(guardarResumen('dt1', 'BORRADOR', { texto: 'corto' }));
  await assertSucceeds(guardarResumen('dt1', 'BORRADOR'));
});

test('el jugador no ve el borrador, pero sí el resumen aprobado', async () => {
  await ponerPartido('FINALIZADO', { golesFavor: 1, golesContra: 0 });
  const leer = (uid) => getDoc(doc(como(uid), `${P}/resumen/actual`));
  await assertSucceeds(leer('jug1'));
  await guardarResumen('dt1', 'BORRADOR');
  await assertSucceeds(leer('dt1'));
  await assertFails(leer('jug1'));
  await guardarResumen('dt1', 'APROBADO');
  await assertSucceeds(leer('jug1'));
  await assertFails(leer('jug2'));
});
