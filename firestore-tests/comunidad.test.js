// Pruebas de las reglas de avisos de la comunidad contra el emulador de Firestore.
import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import {
  doc, setDoc, updateDoc, collection, query, where, getDocs, serverTimestamp, Timestamp
} from 'firebase/firestore';

const A = 'acad1';
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-sportpro-comunidad',
    firestore: { rules: readFileSync('../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 }
  });
});
after(async () => { await env.cleanup(); });

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
    await setDoc(doc(db, `academias/${A}/equipos/e1/plantel/jug1`), { jugadorUid: 'jug1', academiaId: A, equipoId: 'e1', activo: true });
    await setDoc(doc(db, `academias/${A}/vinculosApoderados/jug1/apoderados/pad1`), { activo: true });
    await setDoc(doc(db, `academias/${A}/accesosApoderado/pad1`), { apoderadoUid: 'pad1', academiaId: A, jugadorUid: 'jug1' });
    await setDoc(doc(db, `academias/${A}/avisos/a1`), {
      academiaId: A, equipoId: 'e1', nombreEquipo: 'Sub 12', titulo: 'Entrenamiento', mensaje: 'Mañana 4pm',
      autorUid: 'dt1', autorNombre: 'Nombre dt1', activo: true, fechaCreacion: Timestamp.now()
    });
  });
});

const como = (uid) => env.authenticatedContext(uid).firestore();
const publicar = (uid, extra = {}) => setDoc(doc(como(uid), `academias/${A}/avisos/nuevo`), {
  academiaId: A, equipoId: 'e1', nombreEquipo: 'Sub 12', titulo: 'Partido', mensaje: 'Llevar uniforme',
  autorUid: uid, autorNombre: `Nombre ${uid}`, activo: true, fechaCreacion: serverTimestamp(), ...extra
});

test('el DT del equipo y el administrador publican avisos', async () => {
  await assertSucceeds(publicar('dt1'));
  await assertSucceeds(setDoc(doc(como('adm'), `academias/${A}/avisos/otro`), {
    academiaId: A, equipoId: 'e1', nombreEquipo: 'Sub 12', titulo: 'Aviso', mensaje: 'Texto',
    autorUid: 'adm', autorNombre: 'Nombre adm', activo: true, fechaCreacion: serverTimestamp()
  }));
});

test('otros roles no publican y el autor no se puede suplantar', async () => {
  await assertFails(publicar('dt2'));
  await assertFails(publicar('jug1'));
  await assertFails(publicar('pad1'));
  await assertFails(publicar('dt1', { autorNombre: 'Otro' }));
});

test('jugadores y apoderados del equipo leen los avisos', async () => {
  const avisos = (uid) => getDocs(query(collection(como(uid), `academias/${A}/avisos`), where('equipoId', '==', 'e1')));
  await assertSucceeds(avisos('jug1'));
  await assertSucceeds(avisos('pad1'));
  await assertFails(avisos('jug2'));
  await assertFails(avisos('dt2'));
});

test('el DT retira un aviso sin poder editar su texto', async () => {
  const ref = doc(como('dt1'), `academias/${A}/avisos/a1`);
  await assertFails(updateDoc(ref, { mensaje: 'Cambiado' }));
  await assertFails(updateDoc(doc(como('jug1'), `academias/${A}/avisos/a1`), { activo: false }));
  await assertSucceeds(updateDoc(ref, { activo: false }));
});
