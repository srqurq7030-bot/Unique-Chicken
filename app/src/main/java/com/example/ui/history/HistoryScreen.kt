package com.example.ui.history

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.DailyCalculation
import com.example.model.DailyRecordDoc
import com.example.model.LedgerFormatter
import com.example.pdf.DailyPdfGenerator
import com.example.ui.theme.DarkInk
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.LossBg
import com.example.ui.theme.LossRed
import com.example.ui.theme.MintBorder
import com.example.ui.theme.MutedInk
import com.example.ui.theme.NumberBodyStyle
import com.example.ui.theme.NumberMediumStyle
import com.example.ui.theme.ProfitBg
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.SoftGreenSurface
import com.example.ui.theme.SubtleDivider

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenDate: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.historyState.collectAsStateWithLifecycle()
    val busyPdfDate by viewModel.busyPdfDate.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler {
        onNavigateBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureWhite),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SoftGreenSurface)
                    .border(1.dp, MintBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = ForestGreen,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "DAILY RECORD HISTORY",
                        style = MaterialTheme.typography.titleLarge,
                        color = ForestGreen,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Tap any date to view, edit, or export PDF",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryInk,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (val current = state) {
                is HistoryUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = ForestGreen)
                    }
                }
                is HistoryUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(LossBg)
                            .padding(16.dp),
                    ) {
                        Text(
                            text = current.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LossRed,
                        )
                    }
                }
                is HistoryUiState.Success -> {
                    if (current.records.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CalendarMonth,
                                    contentDescription = null,
                                    tint = MintBorder,
                                    modifier = Modifier.size(56.dp),
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No saved dates yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DarkInk,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Your daily purchase, sales, stock, and profit records will appear here automatically.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SecondaryInk,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(
                                items = current.records,
                                key = { it.date.ifBlank { it.id } },
                            ) { record ->
                                val dateKey = record.date.ifBlank { record.id }
                                HistoryRecordCard(
                                    record = record,
                                    isPdfBusy = busyPdfDate == dateKey,
                                    onOpenClick = { onOpenDate(dateKey) },
                                    onPdfClick = {
                                        viewModel.downloadOrSharePdfForDate(
                                            context = context,
                                            dateId = dateKey,
                                            onSuccess = { pdfResult ->
                                                val msg = if (pdfResult.savedToDownloads) {
                                                    "Saved to Downloads/UniqueChicken/${pdfResult.fileName}"
                                                } else {
                                                    "PDF ready to share"
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                DailyPdfGenerator.launchShareIntent(
                                                    context = context,
                                                    uri = pdfResult.shareUri,
                                                    dateId = dateKey,
                                                )
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordCard(
    record: DailyRecordDoc,
    isPdfBusy: Boolean,
    onOpenClick: () -> Unit,
    onPdfClick: () -> Unit,
) {
    val calc = DailyCalculation.computeFromRecord(record)
    val dateKey = record.date.ifBlank { record.id }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintBorder, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenClick)
            .testTag("history_card_$dateKey"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = LedgerFormatter.formatDisplayDate(dateKey),
                        style = MaterialTheme.typography.titleMedium,
                        color = DarkInk,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${record.customerCount} Customers • Closing: ${record.actualClosingPcs} PCS (${LedgerFormatter.formatKg(record.actualClosingWeightKg)})",
                        style = MaterialTheme.typography.labelMedium,
                        color = SecondaryInk,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("history_edit_$dateKey"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Open and edit $dateKey",
                            tint = ForestGreen,
                        )
                    }
                    IconButton(
                        onClick = onPdfClick,
                        enabled = !isPdfBusy,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("history_pdf_$dateKey"),
                    ) {
                        if (isPdfBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = ForestGreen,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PictureAsPdf,
                                contentDescription = "Download or share PDF for $dateKey",
                                tint = ForestGreen,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = SubtleDivider)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Purchase", style = MaterialTheme.typography.labelMedium, color = MutedInk)
                    Text(
                        text = LedgerFormatter.formatInr(calc.totalPurchaseAmount),
                        style = NumberBodyStyle,
                        color = DarkInk,
                    )
                }
                Column {
                    Text("Sales", style = MaterialTheme.typography.labelMedium, color = MutedInk)
                    Text(
                        text = LedgerFormatter.formatInr(calc.totalSalesAmount),
                        style = NumberBodyStyle,
                        color = DarkInk,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (calc.isProfit) "Profit" else "Loss",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (calc.isProfit) ProfitGreen else LossRed,
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (calc.isProfit) ProfitBg else LossBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = LedgerFormatter.formatInr(calc.finalDailyProfit),
                            style = NumberMediumStyle,
                            color = if (calc.isProfit) ProfitGreen else LossRed,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
