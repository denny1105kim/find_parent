const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, updateDoc, deleteDoc, serverTimestamp } = require('firebase/firestore');
const fs = require('node:fs');
(async () => {
  const env = await initializeTestEnvironment({ projectId: 'demo-find-parent', firestore: { rules: fs.readFileSync('firestore.rules', 'utf8') } });
  try {
    await env.clearFirestore();
    const guardian = env.authenticatedContext('guardian').firestore();
    const patient = env.authenticatedContext('patient').firestore();
    const outsider = env.authenticatedContext('outsider').firestore();
    const pair = db => doc(db, 'pairs/test-pair');
    const record = db => doc(db, 'pairs/test-pair/requests/location');
    await assertSucceeds(setDoc(pair(guardian), {guardianUid:'guardian', patientUid:null}));
    await assertSucceeds(getDoc(pair(patient)));
    await assertSucceeds(updateDoc(pair(patient), {patientUid:'patient'}));
    await assertFails(getDoc(pair(outsider)));
    await assertSucceeds(setDoc(record(guardian), {type:'location', status:'pending'}));
    await assertSucceeds(updateDoc(record(patient), {status:'ready', lat:37, lng:127}));
    await assertFails(getDoc(record(outsider)));
    await assertFails(updateDoc(record(guardian), {status:'delivered', lat:0}));
    await assertSucceeds(updateDoc(record(guardian), {status:'delivered'}));
    await assertSucceeds(updateDoc(pair(patient), {checkIn:'okay', checkInAt:serverTimestamp()}));
    await assertSucceeds(updateDoc(pair(patient), {checkIn:'help', checkInAt:serverTimestamp()}));
    await assertFails(updateDoc(pair(guardian), {checkIn:'okay', checkInAt:serverTimestamp()}));
    await assertFails(updateDoc(pair(outsider), {checkIn:'help', checkInAt:serverTimestamp()}));
    await assertFails(updateDoc(pair(patient), {checkIn:'invalid', checkInAt:serverTimestamp()}));
    await assertSucceeds(updateDoc(pair(patient), {batteryPercent:42, charging:true, lastSeenAt:serverTimestamp()}));
    await assertFails(updateDoc(pair(patient), {batteryPercent:101, charging:false, lastSeenAt:serverTimestamp()}));
    await assertFails(updateDoc(pair(guardian), {batteryPercent:42, charging:true, lastSeenAt:serverTimestamp()}));
    await assertSucceeds(updateDoc(pair(patient), {revoked:true}));
    await assertFails(getDoc(record(guardian)));
    await assertFails(getDoc(record(patient)));
    await assertFails(updateDoc(pair(patient), {checkIn:'help', checkInAt:serverTimestamp()}));
    await assertFails(updateDoc(pair(guardian), {revoked:false}));
    await assertFails(deleteDoc(pair(guardian)));
    await assertFails(setDoc(pair(outsider), {guardianUid:'outsider', patientUid:null}));
    console.log('PASS: member isolation, immutable results, revocation, ID reuse prevention');
  } finally { await env.cleanup(); }
})().catch(e => { console.error(e); process.exitCode=1; });
