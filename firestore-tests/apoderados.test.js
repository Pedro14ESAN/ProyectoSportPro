// Pruebas de las reglas de apoderados contra el emulador de Firestore.
// Ejecutar con: npm install && npm test
import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import {
  doc, setDoc, getDoc, updateDoc, writeBatch, collection, collectionGroup,
  query, where, getDocs, serverTimestamp, Timestamp
} from 'firebase/firestore';

const ACADEMIA = 'acad1';
const CODIGO = '11111111-2222-3333-4444-555555555555';
const VINCULO = `academias/${ACADEMIA}/vinculosApoderados/jug1/apoderados/pad1`;
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-sportpro',
    firestore: { rules: readFileSync('../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 }
  });
});
after(async () => { await env.cleanup(); });

const usuario = (uid, rol, estado = 'ACTIVO') => ({
  uid, rol, estado, nombre: `Nombre ${uid}`, correo: `${uid}@x.pe`,
  fechaNacimiento: '2000-01-01', edadVerificada: false, aceptoTerminos: true
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (c) => {
    const db = c.firestore();
    await setDoc(doc(db, 'users/adm'), usuario('adm', 'ADM'));
    await setDoc(doc(db, 'users/adm2'), usuario('adm2', 'ADM'));
    await setDoc(doc(db, 'users/jug1'), usuario('jug1', 'JUG', 'PENDIENTE_VINCULACION'));
    await setDoc(doc(db, 'users/jug2'), usuario('jug2', 'JUG', 'PENDIENTE_VINCULACION'));
    await setDoc(doc(db, 'users/pad1'), usuario('pad1', 'PAD', 'PENDIENTE_VINCULACION'));
    await setDoc(doc(db, 'users/pad2'), usuario('pad2', 'PAD', 'PENDIENTE_VINCULACION'));
    await setDoc(doc(db, `academias/${ACADEMIA}`), { nombre: 'Academia Sol', activa: true, creadoPor: 'adm' });
    await setDoc(doc(db, `academias/${ACADEMIA}/miembros/jug1`),
      { uid: 'jug1', academiaId: ACADEMIA, rol: 'JUG', activo: true, creadoPor: 'adm', fechaCreacion: Timestamp.now() });
    await setDoc(doc(db, `academias/${ACADEMIA}/solicitudesVinculacion/jug1`),
      { academiaId: ACADEMIA, solicitanteUid: 'jug1', nombreSolicitante: 'Nombre jug1', rolSolicitante: 'JUG', estado: 'APROBADA' });
    await setDoc(doc(db, `invitacionesApoderado/${CODIGO}`), {
      academiaId: ACADEMIA, jugadorUid: 'jug1', apoderadoUid: 'pad1', creadoPor: 'adm',
      estado: 'UTILIZADA', solicitudId: CODIGO, utilizadaPor: 'pad1'
    });
    await setDoc(doc(db, `solicitudesApoderado/${CODIGO}`), {
      academiaId: ACADEMIA, jugadorUid: 'jug1', apoderadoUid: 'pad1', nombreApoderado: 'Nombre pad1',
      relacionDeclarada: 'MADRE', estado: 'PENDIENTE', fechaCreacion: Timestamp.now()
    });
  });
});

const como = (uid) => env.authenticatedContext(uid).firestore();

function aprobar(db, extra = {}) {
  const batch = writeBatch(db);
  batch.update(doc(db, `solicitudesApoderado/${CODIGO}`),
    { estado: 'APROBADA', revisadoPor: 'adm', fechaRevision: serverTimestamp() });
  batch.set(doc(db, VINCULO), {
    academiaId: ACADEMIA, jugadorUid: 'jug1', apoderadoUid: 'pad1', solicitudId: CODIGO,
    relacionDeclarada: 'MADRE', activo: true, aprobadoPor: 'adm', fechaAprobacion: serverTimestamp(),
    nombreJugador: 'Nombre jug1', nombreAcademia: 'Academia Sol', ...extra
  });
  return batch.commit();
}

async function dejarAprobada() {
  await env.withSecurityRulesDisabled(async (c) => {
    const db = c.firestore();
    await updateDoc(doc(db, `solicitudesApoderado/${CODIGO}`), { estado: 'APROBADA', revisadoPor: 'adm' });
    await setDoc(doc(db, VINCULO), {
      academiaId: ACADEMIA, jugadorUid: 'jug1', apoderadoUid: 'pad1', solicitudId: CODIGO,
      relacionDeclarada: 'MADRE', activo: true, aprobadoPor: 'adm', fechaAprobacion: Timestamp.now(),
      nombreJugador: 'Nombre jug1', nombreAcademia: 'Academia Sol'
    });
  });
}

test('el administrador aprueba guardando los nombres del jugador y la academia', async () => {
  await assertSucceeds(aprobar(como('adm')));
});

test('la aprobación rechaza nombres que no coinciden con la academia', async () => {
  await assertFails(aprobar(como('adm'), { nombreJugador: 'Otro nombre' }));
});

test('el administrador lista las solicitudes de su academia, pero no las de otra', async () => {
  const q = (db) => query(collection(db, 'solicitudesApoderado'), where('academiaId', '==', ACADEMIA));
  await assertSucceeds(getDocs(q(como('adm'))));
  await assertFails(getDocs(q(como('adm2'))));
});

test('el apoderado lista solo sus propias solicitudes', async () => {
  const q = (db, uid) => query(collection(db, 'solicitudesApoderado'), where('apoderadoUid', '==', uid));
  const propias = await assertSucceeds(getDocs(q(como('pad1'), 'pad1')));
  if (propias.size !== 1) throw new Error('Se esperaba una solicitud propia');
  await assertFails(getDocs(q(como('pad2'), 'pad1')));
});

test('el apoderado lee su propio vínculo y otro apoderado no', async () => {
  await dejarAprobada();
  await assertSucceeds(getDoc(doc(como('pad1'), VINCULO)));
  await assertFails(getDoc(doc(como('pad2'), VINCULO)));
  await assertFails(getDoc(doc(como('jug1'), VINCULO)));
});

test('el administrador anula una invitación disponible', async () => {
  const codigo = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), `invitacionesApoderado/${codigo}`), {
      academiaId: ACADEMIA, jugadorUid: 'jug1', apoderadoUid: 'pad2', creadoPor: 'adm',
      estado: 'DISPONIBLE', fechaCreacion: Timestamp.now(),
      fechaVencimiento: Timestamp.fromMillis(Date.now() + 3600_000)
    });
  });
  const anular = (db, uid) => updateDoc(doc(db, `invitacionesApoderado/${codigo}`),
    { estado: 'REVOCADA', revocadoPor: uid, fechaRevocacion: serverTimestamp() });
  await assertFails(anular(como('pad2'), 'pad2'));
  await assertFails(anular(como('adm2'), 'adm2'));
  await assertSucceeds(anular(como('adm'), 'adm'));
});

test('no se puede anular una invitación ya utilizada', async () => {
  await assertFails(updateDoc(doc(como('adm'), `invitacionesApoderado/${CODIGO}`),
    { estado: 'REVOCADA', revocadoPor: 'adm', fechaRevocacion: serverTimestamp() }));
});

test('el jugador activa su cuenta con un vínculo activo', async () => {
  const activar = (uid) => updateDoc(doc(como(uid), `users/${uid}`),
    { estado: 'ACTIVO', vinculoActivacion: ACADEMIA });
  await assertSucceeds(activar('jug1'));
  await assertFails(activar('jug2'));
});

test('el jugador encuentra sus vínculos con academias', async () => {
  await assertSucceeds(getDocs(query(collectionGroup(como('jug1'), 'miembros'), where('uid', '==', 'jug1'))));
  await assertFails(getDocs(query(collectionGroup(como('jug2'), 'miembros'), where('uid', '==', 'jug1'))));
});

test('el apoderado activa su cuenta solo con una solicitud aprobada', async () => {
  const activar = () => updateDoc(doc(como('pad1'), 'users/pad1'),
    { estado: 'ACTIVO', vinculoActivacion: CODIGO });
  await assertFails(activar());
  await dejarAprobada();
  await assertSucceeds(activar());
});

test('la activación no permite cambiar otros datos de la cuenta', async () => {
  await assertFails(updateDoc(doc(como('jug1'), 'users/jug1'),
    { estado: 'ACTIVO', vinculoActivacion: ACADEMIA, rol: 'DT' }));
  await assertFails(updateDoc(doc(como('pad2'), 'users/pad2'),
    { estado: 'ACTIVO', vinculoActivacion: CODIGO }));
});
