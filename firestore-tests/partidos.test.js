// Pruebas de las reglas de partidos contra el emulador de Firestore.
import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import {
  doc, setDoc, getDoc, updateDoc, collection, query, where, getDocs, serverTimestamp, Timestamp
} from 'firebase/firestore';

const A = 'acad1';
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-sportpro-partidos',
    firestore: { rules: readFileSync('../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 }
  });
});
after(async () => { await env.cleanup(); });

const usuario = (uid, rol) => ({ uid, rol, estado: 'ACTIVO', nombre: `Nombre ${uid}` });
const miembro = (uid, rol) => ({ uid, academiaId: A, rol, activo: true });

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (c) => {
    const db = c.firestore();
    for (const [uid, rol] of [['adm', 'ADM'], ['adm2', 'ADM'], ['dt1', 'DT'], ['dt2', 'DT'], ['jug1', 'JUG'], ['jug2', 'JUG'], ['pad1', 'PAD'], ['pad2', 'PAD']]) {
      await setDoc(doc(db, `users/${uid}`), usuario(uid, rol));
    }
    await setDoc(doc(db, `academias/${A}`), { nombre: 'Academia Sol', activa: true, creadoPor: 'adm' });
    for (const [uid, rol] of [['dt1', 'DT'], ['dt2', 'DT'], ['jug1', 'JUG'], ['jug2', 'JUG']]) {
      await setDoc(doc(db, `academias/${A}/miembros/${uid}`), miembro(uid, rol));
    }
    await setDoc(doc(db, `academias/${A}/equipos/e1`), { academiaId: A, nombre: 'Sub 12', activo: true, dtUid: 'dt1', categoriaId: 'c1' });
    await setDoc(doc(db, `academias/${A}/equipos/e2`), { academiaId: A, nombre: 'Sub 14', activo: true, dtUid: 'dt2', categoriaId: 'c1' });
    await setDoc(doc(db, `academias/${A}/equipos/e1/plantel/jug1`), { jugadorUid: 'jug1', academiaId: A, equipoId: 'e1', activo: true });
    await setDoc(doc(db, `academias/${A}/vinculosApoderados/jug1/apoderados/pad1`),
      { academiaId: A, jugadorUid: 'jug1', apoderadoUid: 'pad1', activo: true });
    await setDoc(doc(db, `academias/${A}/partidos/p1`), {
      academiaId: A, equipoId: 'e1', nombreEquipo: 'Sub 12', rival: 'Leones', lugar: 'Estadio',
      fechaHora: Timestamp.fromMillis(Date.now() + 86400_000), condicion: 'LOCAL', estado: 'PROGRAMADO',
      golesFavor: null, golesContra: null, creadoPor: 'adm', actualizadoPor: 'adm',
      fechaCreacion: Timestamp.now(), fechaActualizacion: Timestamp.now()
    });
  });
});

const como = (uid) => env.authenticatedContext(uid).firestore();
const nuevo = (uid, extra = {}) => ({
  academiaId: A, equipoId: 'e1', nombreEquipo: 'Sub 12', rival: 'Tigres', lugar: 'Cancha 2',
  fechaHora: Timestamp.fromMillis(Date.now() + 2 * 86400_000), condicion: 'VISITA', estado: 'PROGRAMADO',
  golesFavor: null, golesContra: null, creadoPor: uid, actualizadoPor: uid,
  fechaCreacion: serverTimestamp(), fechaActualizacion: serverTimestamp(), ...extra
});
const crear = (uid, extra) => setDoc(doc(como(uid), `academias/${A}/partidos/nuevo`), nuevo(uid, extra));

test('el administrador programa partidos', async () => {
  await assertSucceeds(crear('adm'));
});

test('el DT programa partidos solo de su equipo', async () => {
  await assertSucceeds(crear('dt1'));
  await assertFails(setDoc(doc(como('dt2'), `academias/${A}/partidos/otro`), nuevo('dt2')));
});

test('jugadores y administradores ajenos no programan partidos', async () => {
  await assertFails(crear('jug1'));
  await assertFails(crear('adm2'));
});

test('el partido debe nacer programado, sin goles y con el nombre real del equipo', async () => {
  await assertFails(crear('adm', { estado: 'FINALIZADO', golesFavor: 1, golesContra: 0 }));
  await assertFails(crear('adm', { nombreEquipo: 'Otro' }));
  await assertFails(crear('adm', { rival: 'X' }));
});

test('cada rol consulta solo los partidos que le corresponden', async () => {
  const porEquipo = (uid, equipo) => getDocs(query(collection(como(uid), `academias/${A}/partidos`), where('equipoId', '==', equipo)));
  await assertSucceeds(getDocs(collection(como('adm'), `academias/${A}/partidos`)));
  await assertFails(getDocs(collection(como('adm2'), `academias/${A}/partidos`)));
  await assertSucceeds(porEquipo('dt1', 'e1'));
  await assertFails(porEquipo('dt2', 'e1'));
  await assertSucceeds(porEquipo('jug1', 'e1'));
  await assertFails(porEquipo('jug2', 'e1'));
  await assertFails(getDocs(collection(como('jug1'), `academias/${A}/partidos`)));
  await assertSucceeds(getDoc(doc(como('jug1'), `academias/${A}/partidos/p1`)));
});

test('el DT registra el resultado con goles válidos', async () => {
  const ref = (uid) => doc(como(uid), `academias/${A}/partidos/p1`);
  const resultado = (uid, gf, gc) => updateDoc(ref(uid), {
    estado: 'FINALIZADO', golesFavor: gf, golesContra: gc, actualizadoPor: uid, fechaActualizacion: serverTimestamp()
  });
  await assertFails(resultado('dt1', -1, 2));
  await assertFails(resultado('jug1', 2, 1));
  await assertSucceeds(resultado('dt1', 2, 1));
});

test('no se puede mover un partido a otro equipo', async () => {
  await assertFails(updateDoc(doc(como('adm'), `academias/${A}/partidos/p1`), {
    equipoId: 'e2', nombreEquipo: 'Sub 14', actualizadoPor: 'adm', fechaActualizacion: serverTimestamp()
  }));
});

const registrarAcceso = (uid, jugador = 'jug1') => setDoc(doc(como(uid), `academias/${A}/accesosApoderado/${uid}`),
  { apoderadoUid: uid, academiaId: A, jugadorUid: jugador, fechaActualizacion: serverTimestamp() });

test('el apoderado registra su acceso solo con un vínculo activo', async () => {
  await assertFails(registrarAcceso('pad1', 'jug2'));
  await assertFails(registrarAcceso('pad2'));
  await assertSucceeds(registrarAcceso('pad1'));
});

test('el apoderado con acceso ve el calendario y el equipo de su jugador', async () => {
  const db = como('pad1');
  await assertFails(getDocs(collection(db, `academias/${A}/partidos`)));
  await registrarAcceso('pad1');
  await assertSucceeds(getDocs(collection(db, `academias/${A}/equipos`)));
  await assertSucceeds(getDoc(doc(db, `academias/${A}/equipos/e1/plantel/jug1`)));
  await assertFails(getDoc(doc(db, `academias/${A}/equipos/e1/plantel/jug2`)));
  await assertSucceeds(getDocs(query(collection(db, `academias/${A}/partidos`), where('equipoId', '==', 'e1'))));
  await assertFails(updateDoc(doc(db, `academias/${A}/partidos/p1`),
    { rival: 'Cambio', actualizadoPor: 'pad1', fechaActualizacion: serverTimestamp() }));
});

test('al revocar el vínculo el apoderado pierde el acceso', async () => {
  await registrarAcceso('pad1');
  await env.withSecurityRulesDisabled(async (c) => {
    await updateDoc(doc(c.firestore(), `academias/${A}/vinculosApoderados/jug1/apoderados/pad1`), { activo: false });
  });
  await assertFails(getDocs(collection(como('pad1'), `academias/${A}/partidos`)));
});

test('el DT comprueba un partido nuevo antes de programarlo, sin leer partidos ajenos', async () => {
  // La app usa una transacción que lee el documento nuevo antes de crearlo.
  await assertSucceeds(getDoc(doc(como('dt1'), `academias/${A}/partidos/nuevo`)));
  await assertSucceeds(getDoc(doc(como('adm'), `academias/${A}/partidos/nuevo`)));
  await assertFails(getDoc(doc(como('jug1'), `academias/${A}/partidos/nuevo`)));
  await assertFails(getDoc(doc(como('dt2'), `academias/${A}/partidos/p1`)));
});
