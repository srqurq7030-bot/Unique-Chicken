package com.example.ui.daily

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChickenShopRepository
import com.example.model.CustomerSaleDoc
import com.example.model.DailyCalculation
import com.example.model.DailyInputs
import com.example.model.DailyRecordDoc
import com.example.model.LedgerFormatter
import com.example.pdf.DailyPdfGenerator
import com.example.pdf.GeneratedPdfResult
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DailyInputTextState(
    val purchasePcsText: String = "",
    val purchaseWeightText: String = "",
    val purchaseRateText: String = "",
    val actualClosingPcsText: String = "",
    val actualClosingWeightText: String = "",
    val remainingStockValueText: String = "",
    val shopExpensesText: String = "",
    val workerPaymentText: String = "",
    val otherExpensesText: String = "",
) {
    fun toDailyInputs(): DailyInputs = DailyInputs(
        purchasePcs = purchasePcsText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
        purchaseWeightKg = purchaseWeightText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
        purchaseRatePerKg = purchaseRateText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
        actualClosingPcs = actualClosingPcsText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
        actualClosingWeightKg = actualClosingWeightText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
        remainingStockValue = remainingStockValueText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
        shopExpenses = shopExpensesText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
        workerPayment = workerPaymentText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
        otherExpenses = otherExpensesText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
    )

    companion object {
        fun fromRecord(record: DailyRecordDoc?): DailyInputTextState {
            if (record == null) return DailyInputTextState()
            return DailyInputTextState(
                purchasePcsText = if (record.purchasePcs > 0L) record.purchasePcs.toString() else "",
                purchaseWeightText = LedgerFormatter.formatPlainDecimal(record.purchaseWeightKg),
                purchaseRateText = LedgerFormatter.formatPlainDecimal(record.purchaseRatePerKg),
                actualClosingPcsText = if (record.actualClosingPcs > 0L) record.actualClosingPcs.toString() else "",
                actualClosingWeightText = LedgerFormatter.formatPlainDecimal(record.actualClosingWeightKg),
                remainingStockValueText = LedgerFormatter.formatPlainDecimal(record.remainingStockValue),
                shopExpensesText = LedgerFormatter.formatPlainDecimal(record.shopExpenses),
                workerPaymentText = LedgerFormatter.formatPlainDecimal(record.workerPayment),
                otherExpensesText = LedgerFormatter.formatPlainDecimal(record.otherExpenses),
            )
        }
    }
}

enum class SaveStatus {
    IDLE,
    SAVING,
    SAVED,
    ERROR,
}

data class DailyAccountUiState(
    val selectedDateId: String = LedgerFormatter.todayDateId(),
    val isLoading: Boolean = true,
    val inputTexts: DailyInputTextState = DailyInputTextState(),
    val sales: List<CustomerSaleDoc> = emptyList(),
    val calculation: DailyCalculation = DailyCalculation.compute(DailyInputs(), emptyList()),
    val saveStatus: SaveStatus = SaveStatus.IDLE,
    val errorMessage: String? = null,
    val isGeneratingPdf: Boolean = false,
)

class DailyAccountViewModel(
    private val repository: ChickenShopRepository,
    private val currentUserId: String,
    initialDateId: String = LedgerFormatter.todayDateId(),
) : ViewModel() {

    private val _selectedDateId = MutableStateFlow(initialDateId)
    private val _inputTexts = MutableStateFlow(DailyInputTextState())
    private val _sales = MutableStateFlow<List<CustomerSaleDoc>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _saveStatus = MutableStateFlow(SaveStatus.IDLE)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isGeneratingPdf = MutableStateFlow(false)

    private var hydratedDateId: String? = null
    private var currentRecordExistsInDb: Boolean = false
    private var autoSaveJob: Job? = null

    val uiState: StateFlow<DailyAccountUiState> = combine(
        _selectedDateId,
        _isLoading,
        _inputTexts,
        _sales,
        _saveStatus,
        _errorMessage,
        _isGeneratingPdf,
    ) { values ->
        val dateId = values[0] as String
        val loading = values[1] as Boolean
        val texts = values[2] as DailyInputTextState
        @Suppress("UNCHECKED_CAST")
        val salesList = values[3] as List<CustomerSaleDoc>
        val saveStatus = values[4] as SaveStatus
        val err = values[5] as String?
        val pdfBusy = values[6] as Boolean

        DailyAccountUiState(
            selectedDateId = dateId,
            isLoading = loading,
            inputTexts = texts,
            sales = salesList,
            calculation = DailyCalculation.compute(texts.toDailyInputs(), salesList),
            saveStatus = saveStatus,
            errorMessage = err,
            isGeneratingPdf = pdfBusy,
        )
    }.let { flow ->
        val initial = DailyAccountUiState(selectedDateId = initialDateId)
        val state = MutableStateFlow(initial)
        viewModelScope.launch {
            flow.collect { state.value = it }
        }
        state.asStateFlow()
    }

    init {
        observeSelectedDate()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeSelectedDate() {
        viewModelScope.launch {
            _selectedDateId.flatMapLatest { dateId ->
                _isLoading.value = true
                hydratedDateId = null
                currentRecordExistsInDb = false
                _saveStatus.value = SaveStatus.IDLE
                _errorMessage.value = null

                combine(
                    repository.observeDailyRecord(currentUserId, dateId),
                    repository.observeCustomerSales(currentUserId, dateId),
                ) { record, sales ->
                    Triple(dateId, record, sales)
                }.map<Triple<String, DailyRecordDoc?, List<CustomerSaleDoc>>, DailyStreamEvent> { (d, rec, s) ->
                    DailyStreamEvent.Data(d, rec, s)
                }.catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing daily record for $dateId", rootError)
                    emit(DailyStreamEvent.Error(rootError.localizedMessage ?: "Failed to load daily record"))
                }
            }.collect { event ->
                when (event) {
                    is DailyStreamEvent.Data -> {
                        if (event.dateId == _selectedDateId.value) {
                            currentRecordExistsInDb = event.record != null
                            if (hydratedDateId != event.dateId) {
                                _inputTexts.value = DailyInputTextState.fromRecord(event.record)
                                hydratedDateId = event.dateId
                            }
                            _sales.value = event.sales
                            _isLoading.value = false
                            _errorMessage.value = null
                        }
                    }
                    is DailyStreamEvent.Error -> {
                        _isLoading.value = false
                        _errorMessage.value = event.message
                    }
                }
            }
        }
    }

    fun selectDate(newDateId: String) {
        if (newDateId == _selectedDateId.value) return
        flushSaveAndSwitch(newDateId)
    }

    fun shiftDate(daysDelta: Int) {
        val nextDate = LedgerFormatter.shiftDateId(_selectedDateId.value, daysDelta)
        selectDate(nextDate)
    }

    private fun flushSaveAndSwitch(newDateId: String) {
        val pendingJob = autoSaveJob
        autoSaveJob = null
        pendingJob?.cancel()

        val oldDateId = _selectedDateId.value
        val oldInputs = _inputTexts.value.toDailyInputs()
        val oldSales = _sales.value

        if (hydratedDateId == oldDateId && (oldInputs.hasAnyValue() || currentRecordExistsInDb)) {
            viewModelScope.launch {
                repository.saveDailyInputs(currentUserId, oldDateId, oldInputs, oldSales)
            }
        }

        _inputTexts.value = DailyInputTextState()
        _sales.value = emptyList()
        _selectedDateId.value = newDateId
    }

    fun updatePurchasePcs(raw: String) {
        val sanitized = sanitizeIntInput(raw)
        _inputTexts.update { it.copy(purchasePcsText = sanitized) }
        scheduleAutoSave()
    }

    fun updatePurchaseWeight(raw: String) {
        val sanitized = sanitizeDecimalInput(raw)
        _inputTexts.update { it.copy(purchaseWeightText = sanitized) }
        scheduleAutoSave()
    }

    fun updatePurchaseRate(raw: String) {
        val sanitized = sanitizeDecimalInput(raw)
        _inputTexts.update { it.copy(purchaseRateText = sanitized) }
        scheduleAutoSave()
    }

    fun updateActualClosingPcs(raw: String) {
        val sanitized = sanitizeIntInput(raw)
        _inputTexts.update { it.copy(actualClosingPcsText = sanitized) }
        scheduleAutoSave()
    }

    fun updateActualClosingWeight(raw: String) {
        val sanitized = sanitizeDecimalInput(raw)
        _inputTexts.update { it.copy(actualClosingWeightText = sanitized) }
        scheduleAutoSave()
    }

    fun updateRemainingStockValue(raw: String) {
        val sanitized = sanitizeDecimalInput(raw)
        _inputTexts.update { it.copy(remainingStockValueText = sanitized) }
        scheduleAutoSave()
    }

    fun updateShopExpenses(raw: String) {
        val sanitized = sanitizeDecimalInput(raw)
        _inputTexts.update { it.copy(shopExpensesText = sanitized) }
        scheduleAutoSave()
    }

    fun updateWorkerPayment(raw: String) {
        val sanitized = sanitizeDecimalInput(raw)
        _inputTexts.update { it.copy(workerPaymentText = sanitized) }
        scheduleAutoSave()
    }

    fun updateOtherExpenses(raw: String) {
        val sanitized = sanitizeDecimalInput(raw)
        _inputTexts.update { it.copy(otherExpensesText = sanitized) }
        scheduleAutoSave()
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        val targetDate = _selectedDateId.value
        _saveStatus.value = SaveStatus.SAVING
        autoSaveJob = viewModelScope.launch {
            delay(550L)
            val inputs = _inputTexts.value.toDailyInputs()
            val sales = _sales.value
            if (!inputs.hasAnyValue() && !currentRecordExistsInDb && sales.isEmpty()) {
                _saveStatus.value = SaveStatus.IDLE
                return@launch
            }
            val result = repository.saveDailyInputs(currentUserId, targetDate, inputs, sales)
            result.fold(
                onSuccess = {
                    currentRecordExistsInDb = true
                    _saveStatus.value = SaveStatus.SAVED
                    _errorMessage.value = null
                },
                onFailure = { err ->
                    _saveStatus.value = SaveStatus.ERROR
                    _errorMessage.value = err.localizedMessage ?: "Failed to save record"
                },
            )
        }
    }

    fun saveCustomerSale(
        saleId: String?,
        customerName: String,
        pcs: Long,
        weightKg: Double,
        ratePerKg: Double,
        depositAmount: Double,
        onComplete: (Boolean, String?) -> Unit,
    ) {
        val dateId = _selectedDateId.value
        val currentInputs = _inputTexts.value.toDailyInputs()
        val currentSales = _sales.value
        viewModelScope.launch {
            _saveStatus.value = SaveStatus.SAVING
            val result = repository.upsertCustomerSale(
                userId = currentUserId,
                dateId = dateId,
                saleId = saleId,
                customerName = customerName,
                pcs = pcs,
                weightKg = weightKg,
                ratePerKg = ratePerKg,
                depositAmount = depositAmount,
                currentInputs = currentInputs,
                existingSales = currentSales,
            )
            result.fold(
                onSuccess = {
                    currentRecordExistsInDb = true
                    _saveStatus.value = SaveStatus.SAVED
                    _errorMessage.value = null
                    onComplete(true, null)
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Could not save customer sale"
                    _saveStatus.value = SaveStatus.ERROR
                    _errorMessage.value = msg
                    onComplete(false, msg)
                },
            )
        }
    }

    fun deleteCustomerSale(saleId: String) {
        val dateId = _selectedDateId.value
        val currentInputs = _inputTexts.value.toDailyInputs()
        val currentSales = _sales.value
        viewModelScope.launch {
            _saveStatus.value = SaveStatus.SAVING
            val result = repository.deleteCustomerSale(
                userId = currentUserId,
                dateId = dateId,
                saleId = saleId,
                currentInputs = currentInputs,
                existingSales = currentSales,
            )
            result.fold(
                onSuccess = {
                    _saveStatus.value = SaveStatus.SAVED
                    _errorMessage.value = null
                },
                onFailure = { err ->
                    _saveStatus.value = SaveStatus.ERROR
                    _errorMessage.value = err.localizedMessage ?: "Could not delete customer"
                },
            )
        }
    }

    fun generateAndShareDailyPdf(
        context: Context,
        onSuccess: (GeneratedPdfResult) -> Unit,
        onError: (String) -> Unit,
    ) {
        val dateId = _selectedDateId.value
        val inputs = _inputTexts.value.toDailyInputs()
        val sales = _sales.value
        viewModelScope.launch {
            _isGeneratingPdf.value = true
            // Ensure latest inputs are saved before generating report
            if (inputs.hasAnyValue() || currentRecordExistsInDb || sales.isNotEmpty()) {
                repository.saveDailyInputs(currentUserId, dateId, inputs, sales)
            }
            val syntheticRecord = DailyRecordDoc(
                id = dateId,
                userId = currentUserId,
                date = dateId,
                purchasePcs = inputs.purchasePcs,
                purchaseWeightKg = inputs.purchaseWeightKg,
                purchaseRatePerKg = inputs.purchaseRatePerKg,
                actualClosingPcs = inputs.actualClosingPcs,
                actualClosingWeightKg = inputs.actualClosingWeightKg,
                remainingStockValue = inputs.remainingStockValue,
                shopExpenses = inputs.shopExpenses,
                workerPayment = inputs.workerPayment,
                otherExpenses = inputs.otherExpenses,
                customerCount = sales.size.toLong(),
                totalSoldPcs = sales.sumOf { it.pcs },
                totalSoldWeightKg = sales.sumOf { it.weightKg },
                totalSalesAmount = sales.sumOf { it.totalAmount },
                totalDepositAmount = sales.sumOf { it.depositAmount },
            )
            val result = DailyPdfGenerator.generateAndSavePdf(
                context = context,
                dateId = dateId,
                record = syntheticRecord,
                sales = sales,
            )
            _isGeneratingPdf.value = false
            result.fold(
                onSuccess = { pdfResult ->
                    onSuccess(pdfResult)
                },
                onFailure = { err ->
                    onError(err.localizedMessage ?: "Failed to create PDF report")
                },
            )
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun sanitizeIntInput(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length > 7) digits.take(7) else digits
    }

    private fun sanitizeDecimalInput(raw: String): String {
        val filtered = buildString {
            var hasDot = false
            for (ch in raw) {
                if (ch.isDigit()) {
                    append(ch)
                } else if (ch == '.' && !hasDot) {
                    hasDot = true
                    if (isEmpty()) append('0')
                    append(ch)
                }
            }
        }
        val dotIdx = filtered.indexOf('.')
        return if (dotIdx >= 0 && filtered.length - dotIdx - 1 > 2) {
            filtered.substring(0, dotIdx + 3)
        } else {
            filtered.take(11)
        }
    }

    private sealed interface DailyStreamEvent {
        data class Data(
            val dateId: String,
            val record: DailyRecordDoc?,
            val sales: List<CustomerSaleDoc>,
        ) : DailyStreamEvent

        data class Error(val message: String) : DailyStreamEvent
    }

    private companion object {
        const val TAG = "DailyAccountVM"
    }
}
