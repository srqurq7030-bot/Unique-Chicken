package com.example.data

import android.content.Context
import com.example.R
import com.example.model.CustomerSaleDoc
import com.example.model.DailyInputs
import com.example.model.DailyRecordDoc
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ChickenShopRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun dailyRecordsCol(userId: String) =
        db.collection("users").document(userId).collection("dailyRecords")

    private fun customerSalesCol(userId: String, dateId: String) =
        dailyRecordsCol(userId).document(dateId).collection("customerSales")

    fun observeDailyRecord(userId: String, dateId: String): Flow<DailyRecordDoc?> = flow {
        val path = "users/$userId/dailyRecords/$dateId"
        emitAll(
            dailyRecordsCol(userId).document(dateId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            DailyRecordDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeCustomerSales(userId: String, dateId: String): Flow<List<CustomerSaleDoc>> = flow {
        val path = "users/$userId/dailyRecords/$dateId/customerSales"
        emitAll(
            customerSalesCol(userId, dateId)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CustomerSaleDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeHistoryRecords(userId: String): Flow<List<DailyRecordDoc>> = flow {
        val path = "users/$userId/dailyRecords"
        emitAll(
            dailyRecordsCol(userId)
                .orderBy("date", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DailyRecordDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveDailyInputs(
        userId: String,
        dateId: String,
        inputs: DailyInputs,
        sales: List<CustomerSaleDoc>,
    ): Result<Unit> {
        var docPath = "users/$userId/dailyRecords/$dateId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            require(DATE_ID_REGEX.matches(dateId)) { "Invalid date format." }

            val docRef = dailyRecordsCol(uid).document(dateId)
            docPath = docRef.path
            val exists = docRef.get().await().exists()

            if (!exists) {
                val payload = buildFullDailyRecordCreateMap(uid, dateId, inputs, sales)
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "purchasePcs" to inputs.purchasePcs.coerceAtLeast(0L),
                    "purchaseWeightKg" to inputs.purchaseWeightKg.coerceAtLeast(0.0),
                    "purchaseRatePerKg" to inputs.purchaseRatePerKg.coerceAtLeast(0.0),
                    "actualClosingPcs" to inputs.actualClosingPcs.coerceAtLeast(0L),
                    "actualClosingWeightKg" to inputs.actualClosingWeightKg.coerceAtLeast(0.0),
                    "remainingStockValue" to inputs.remainingStockValue.coerceAtLeast(0.0),
                    "shopExpenses" to inputs.shopExpenses.coerceAtLeast(0.0),
                    "workerPayment" to inputs.workerPayment.coerceAtLeast(0.0),
                    "otherExpenses" to inputs.otherExpenses.coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertCustomerSale(
        userId: String,
        dateId: String,
        saleId: String?,
        customerName: String,
        pcs: Long,
        weightKg: Double,
        ratePerKg: Double,
        depositAmount: Double,
        currentInputs: DailyInputs,
        existingSales: List<CustomerSaleDoc>,
    ): Result<Unit> {
        var docPath = "users/$userId/dailyRecords/$dateId/customerSales"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            require(DATE_ID_REGEX.matches(dateId)) { "Invalid date format." }
            val trimmedName = customerName.trim()
            require(trimmedName.isNotEmpty() && trimmedName.length <= 100) {
                "Customer name must be between 1 and 100 characters."
            }

            val sanitizedPcs = pcs.coerceAtLeast(0L)
            val sanitizedWeight = weightKg.coerceAtLeast(0.0)
            val sanitizedRate = ratePerKg.coerceAtLeast(0.0)
            val sanitizedDeposit = depositAmount.coerceAtLeast(0.0)

            val salesCol = customerSalesCol(uid, dateId)
            val saleRef = if (saleId.isNullOrBlank()) salesCol.document() else salesCol.document(saleId)
            docPath = saleRef.path

            val newSale = CustomerSaleDoc(
                id = saleRef.id,
                userId = uid,
                recordDate = dateId,
                customerName = trimmedName,
                pcs = sanitizedPcs,
                weightKg = sanitizedWeight,
                ratePerKg = sanitizedRate,
                depositAmount = sanitizedDeposit,
            )

            val updatedSales = if (saleId.isNullOrBlank()) {
                existingSales + newSale
            } else {
                val found = existingSales.any { it.id == saleId }
                if (found) {
                    existingSales.map { if (it.id == saleId) newSale else it }
                } else {
                    existingSales + newSale
                }
            }

            val parentRef = dailyRecordsCol(uid).document(dateId)
            val parentExists = parentRef.get().await().exists()

            val batch = db.batch()
            if (saleId.isNullOrBlank()) {
                val saleCreateMap = mapOf(
                    "userId" to uid,
                    "recordDate" to dateId,
                    "customerName" to trimmedName,
                    "pcs" to sanitizedPcs,
                    "weightKg" to sanitizedWeight,
                    "ratePerKg" to sanitizedRate,
                    "depositAmount" to sanitizedDeposit,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                batch.set(saleRef, saleCreateMap)
            } else {
                val saleUpdateMap = mapOf(
                    "customerName" to trimmedName,
                    "pcs" to sanitizedPcs,
                    "weightKg" to sanitizedWeight,
                    "ratePerKg" to sanitizedRate,
                    "depositAmount" to sanitizedDeposit,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                batch.update(saleRef, saleUpdateMap)
            }

            if (!parentExists) {
                batch.set(parentRef, buildFullDailyRecordCreateMap(uid, dateId, currentInputs, updatedSales))
            } else {
                batch.update(parentRef, buildSummaryUpdateMap(updatedSales))
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomerSale(
        userId: String,
        dateId: String,
        saleId: String,
        currentInputs: DailyInputs,
        existingSales: List<CustomerSaleDoc>,
    ): Result<Unit> {
        var docPath = "users/$userId/dailyRecords/$dateId/customerSales/$saleId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            require(saleId.isNotBlank()) { "Sale ID must not be blank." }

            val saleRef = customerSalesCol(uid, dateId).document(saleId)
            docPath = saleRef.path

            val remainingSales = existingSales.filterNot { it.id == saleId }
            val parentRef = dailyRecordsCol(uid).document(dateId)
            val parentExists = parentRef.get().await().exists()

            val batch = db.batch()
            batch.delete(saleRef)
            if (parentExists) {
                batch.update(parentRef, buildSummaryUpdateMap(remainingSales))
            } else if (currentInputs.hasAnyValue() || remainingSales.isNotEmpty()) {
                batch.set(parentRef, buildFullDailyRecordCreateMap(uid, dateId, currentInputs, remainingSales))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun getRecordSnapshotForPdf(
        userId: String,
        dateId: String,
    ): Result<Pair<DailyRecordDoc, List<CustomerSaleDoc>>> {
        val docPath = "users/$userId/dailyRecords/$dateId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user read." }

            val recordSnap = dailyRecordsCol(uid).document(dateId).get().await()
            val record = recordSnap.toObject(
                DailyRecordDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            ) ?: DailyRecordDoc(id = dateId, userId = uid, date = dateId)

            val salesSnap = customerSalesCol(uid, dateId)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get()
                .await()
            val sales = salesSnap.toObjects(
                CustomerSaleDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )

            Result.success(record to sales)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    private fun buildFullDailyRecordCreateMap(
        uid: String,
        dateId: String,
        inputs: DailyInputs,
        sales: List<CustomerSaleDoc>,
    ): Map<String, Any> = mapOf(
        "userId" to uid,
        "date" to dateId,
        "purchasePcs" to inputs.purchasePcs.coerceAtLeast(0L),
        "purchaseWeightKg" to inputs.purchaseWeightKg.coerceAtLeast(0.0),
        "purchaseRatePerKg" to inputs.purchaseRatePerKg.coerceAtLeast(0.0),
        "actualClosingPcs" to inputs.actualClosingPcs.coerceAtLeast(0L),
        "actualClosingWeightKg" to inputs.actualClosingWeightKg.coerceAtLeast(0.0),
        "remainingStockValue" to inputs.remainingStockValue.coerceAtLeast(0.0),
        "shopExpenses" to inputs.shopExpenses.coerceAtLeast(0.0),
        "workerPayment" to inputs.workerPayment.coerceAtLeast(0.0),
        "otherExpenses" to inputs.otherExpenses.coerceAtLeast(0.0),
        "customerCount" to sales.size.toLong(),
        "totalSoldPcs" to sales.sumOf { it.pcs }.coerceAtLeast(0L),
        "totalSoldWeightKg" to sales.sumOf { it.weightKg }.coerceAtLeast(0.0),
        "totalSalesAmount" to sales.sumOf { it.totalAmount }.coerceAtLeast(0.0),
        "totalDepositAmount" to sales.sumOf { it.depositAmount }.coerceAtLeast(0.0),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    private fun buildSummaryUpdateMap(sales: List<CustomerSaleDoc>): Map<String, Any> = mapOf(
        "customerCount" to sales.size.toLong(),
        "totalSoldPcs" to sales.sumOf { it.pcs }.coerceAtLeast(0L),
        "totalSoldWeightKg" to sales.sumOf { it.weightKg }.coerceAtLeast(0.0),
        "totalSalesAmount" to sales.sumOf { it.totalAmount }.coerceAtLeast(0.0),
        "totalDepositAmount" to sales.sumOf { it.depositAmount }.coerceAtLeast(0.0),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    private companion object {
        val DATE_ID_REGEX = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    }
}
