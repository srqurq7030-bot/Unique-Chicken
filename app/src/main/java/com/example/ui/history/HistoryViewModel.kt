package com.example.ui.history

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChickenShopRepository
import com.example.model.DailyRecordDoc
import com.example.pdf.DailyPdfGenerator
import com.example.pdf.GeneratedPdfResult
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Success(val records: List<DailyRecordDoc>) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}

class HistoryViewModel(
    private val repository: ChickenShopRepository,
    private val currentUserId: String,
) : ViewModel() {

    val historyState: StateFlow<HistoryUiState> = repository.observeHistoryRecords(currentUserId)
        .map<List<DailyRecordDoc>, HistoryUiState> { records ->
            HistoryUiState.Success(records)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing history records", rootError)
            emit(HistoryUiState.Error(rootError.localizedMessage ?: "Failed to load history"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = HistoryUiState.Loading,
        )

    private val _busyPdfDate = MutableStateFlow<String?>(null)
    val busyPdfDate: StateFlow<String?> = _busyPdfDate.asStateFlow()

    fun downloadOrSharePdfForDate(
        context: Context,
        dateId: String,
        onSuccess: (GeneratedPdfResult) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _busyPdfDate.value = dateId
            val snapshotResult = repository.getRecordSnapshotForPdf(currentUserId, dateId)
            snapshotResult.fold(
                onSuccess = { (record, sales) ->
                    val pdfResult = DailyPdfGenerator.generateAndSavePdf(
                        context = context,
                        dateId = dateId,
                        record = record,
                        sales = sales,
                    )
                    _busyPdfDate.value = null
                    pdfResult.fold(
                        onSuccess = onSuccess,
                        onFailure = { e -> onError(e.localizedMessage ?: "PDF generation failed") },
                    )
                },
                onFailure = { e ->
                    _busyPdfDate.value = null
                    onError(e.localizedMessage ?: "Could not load record for PDF")
                },
            )
        }
    }

    private companion object {
        const val TAG = "HistoryVM"
    }
}
