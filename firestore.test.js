const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_shop_1";
const BOB_UID = "bob_shop_2";
const DATE_ID = "2026-11-21";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validDailyRecordPayload(uid, dateId, timestamp) {
  return {
    userId: uid,
    date: dateId,
    purchasePcs: 100,
    purchaseWeightKg: 278.9,
    purchaseRatePerKg: 100.0,
    actualClosingPcs: 10,
    actualClosingWeightKg: 24.5,
    remainingStockValue: 2450.0,
    shopExpenses: 350.0,
    workerPayment: 500.0,
    otherExpenses: 150.0,
    customerCount: 1,
    totalSoldPcs: 15,
    totalSoldWeightKg: 42.5,
    totalSalesAmount: 4675.0,
    totalDepositAmount: 3000.0,
    createdAt: timestamp,
    updatedAt: timestamp,
  };
}

function validCustomerSalePayload(uid, dateId, timestamp) {
  return {
    userId: uid,
    recordDate: dateId,
    customerName: "Ahmed Chicken Shop",
    pcs: 15,
    weightKg: 42.5,
    ratePerKg: 110.0,
    depositAmount: 3000.0,
    createdAt: timestamp,
    updatedAt: timestamp,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write dailyRecords or customerSales", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("dailyRecords").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("dailyRecords")
      .doc(DATE_ID)
      .set(validDailyRecordPayload(ALICE_UID, DATE_ID, now))
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("dailyRecords")
      .doc(DATE_ID)
      .collection("customerSales")
      .doc("sale_1")
      .set(validCustomerSalePayload(ALICE_UID, DATE_ID, now))
  );
});

test("Authenticated owner: valid dailyRecord create, partial updates, and list succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const recordRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("dailyRecords")
    .doc(DATE_ID);

  await assertSucceeds(
    recordRef.set(validDailyRecordPayload(ALICE_UID, DATE_ID, created))
  );

  // List user's own daily records (including empty/non-empty collections)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("dailyRecords").get()
  );

  // Partial update: shop inputs only
  await assertSucceeds(
    recordRef.update({
      purchasePcs: 110,
      purchaseWeightKg: 300.5,
      purchaseRatePerKg: 102.0,
      actualClosingPcs: 12,
      actualClosingWeightKg: 28.0,
      remainingStockValue: 2800.0,
      shopExpenses: 400.0,
      workerPayment: 600.0,
      otherExpenses: 200.0,
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Partial update: denormalized sales summary only
  await assertSucceeds(
    recordRef.update({
      customerCount: 2,
      totalSoldPcs: 30,
      totalSoldWeightKg: 85.0,
      totalSalesAmount: 9350.0,
      totalDepositAmount: 6000.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner: valid customerSale create, update, list, delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const salesCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("dailyRecords")
    .doc(DATE_ID)
    .collection("customerSales");

  await assertSucceeds(
    salesCol.doc("sale_1").set(validCustomerSalePayload(ALICE_UID, DATE_ID, created))
  );

  await assertSucceeds(salesCol.get());

  await assertSucceeds(
    salesCol.doc("sale_1").update({
      customerName: "Ahmed Poultry Traders",
      pcs: 18,
      weightKg: 48.25,
      ratePerKg: 112.0,
      depositAmount: 4000.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(salesCol.doc("sale_1").delete());
});

test("Security bounds: reject cross-user access, ghost fields, immutable edits, future timestamps, and invalid IDs", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("dailyRecords")
      .doc(DATE_ID)
      .set(validDailyRecordPayload(BOB_UID, DATE_ID, created));
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("dailyRecords")
      .doc(DATE_ID)
      .collection("customerSales")
      .doc("bob_sale")
      .set(validCustomerSalePayload(BOB_UID, DATE_ID, created));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const aliceRecord = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("dailyRecords")
    .doc(DATE_ID);

  // Cross-user denial
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("dailyRecords").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("dailyRecords")
      .doc(DATE_ID)
      .get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("dailyRecords")
      .doc(DATE_ID)
      .collection("customerSales")
      .doc("bob_sale")
      .update({ customerName: "Hijacked" })
  );

  // Ghost field / @DocumentId 'id' rejection
  const payloadWithGhost = validDailyRecordPayload(ALICE_UID, DATE_ID, created);
  payloadWithGhost.id = DATE_ID;
  await assertFails(aliceRecord.set(payloadWithGhost));

  // Invalid dateId format rejection
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("dailyRecords")
      .doc("bad-date")
      .set(validDailyRecordPayload(ALICE_UID, "bad-date", created))
  );

  // Future timestamp rejection
  await assertFails(
    aliceRecord.set(
      validDailyRecordPayload(ALICE_UID, DATE_ID, new Date(Date.now() + 60000))
    )
  );

  // Create valid record then test immutable createdAt & negative values
  await assertSucceeds(
    aliceRecord.set(validDailyRecordPayload(ALICE_UID, DATE_ID, created))
  );
  await assertFails(
    aliceRecord.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceRecord.update({
      shopExpenses: -50.0,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject blank customer name
  const badSale = validCustomerSalePayload(ALICE_UID, DATE_ID, created);
  badSale.customerName = "";
  await assertFails(
    aliceRecord.collection("customerSales").doc("sale_bad").set(badSale)
  );
});
