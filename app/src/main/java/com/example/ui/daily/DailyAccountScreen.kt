package com.example.ui.daily

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.CustomerSaleDoc
import com.example.model.DailyCalculation
import com.example.model.LedgerFormatter
import com.example.model.StockStatus
import com.example.pdf.DailyPdfGenerator
import com.example.ui.theme.DarkInk
import com.example.ui.theme.DeepGreenContainer
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.LeafGreen
import com.example.ui.theme.LossBg
import com.example.ui.theme.LossRed
import com.example.ui.theme.MintBorder
import com.example.ui.theme.MutedInk
import com.example.ui.theme.NumberBodyStyle
import com.example.ui.theme.NumberDisplayStyle
import com.example.ui.theme.NumberMediumStyle
import com.example.ui.theme.ProfitBg
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.SoftGreenSurface
import com.example.ui.theme.SubtleDivider
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningBg
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyAccountScreen(
    viewModel: DailyAccountViewModel,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showDatePicker by remember { mutableStateOf(false) }
    var showCustomerDialog by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<CustomerSaleDoc?>(null) }
    var deletingCustomer by remember { mutableStateOf<CustomerSaleDoc?>(null) }

    val todayId = remember { LedgerFormatter.todayDateId() }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = LedgerFormatter.dateIdToUtcMillis(uiState.selectedDateId),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.selectDate(LedgerFormatter.epochMillisToDateId(millis))
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("Select", color = ForestGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = SecondaryInk)
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showCustomerDialog) {
        CustomerSaleDialog(
            initialSale = editingCustomer,
            defaultRateText = uiState.inputTexts.purchaseRateText.let { rateStr ->
                val rateVal = rateStr.toDoubleOrNull()
                if (rateVal != null && rateVal > 0.0) {
                    LedgerFormatter.formatPlainDecimal(rateVal + 10.0)
                } else {
                    ""
                }
            },
            onDismiss = {
                showCustomerDialog = false
                editingCustomer = null
            },
            onSave = { name, pcs, weight, rate, deposit, onComplete ->
                viewModel.saveCustomerSale(
                    saleId = editingCustomer?.id,
                    customerName = name,
                    pcs = pcs,
                    weightKg = weight,
                    ratePerKg = rate,
                    depositAmount = deposit,
                    onComplete = onComplete,
                )
            },
        )
    }

    deletingCustomer?.let { saleToDelete ->
        AlertDialog(
            onDismissRequest = { deletingCustomer = null },
            containerColor = PureWhite,
            title = {
                Text("Delete Customer Sale?", color = DarkInk)
            },
            text = {
                Text(
                    "Remove ${saleToDelete.customerName} (${LedgerFormatter.formatInr(saleToDelete.totalAmount)}) from this day's account?",
                    color = SecondaryInk,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomerSale(saleToDelete.id)
                        deletingCustomer = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LossRed),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCustomer = null }) {
                    Text("Cancel", color = SecondaryInk)
                }
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureWhite),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. APP HEADER & DATE BOX
            item(key = "header") {
                ShopHeaderCard(
                    selectedDateId = uiState.selectedDateId,
                    isToday = uiState.selectedDateId == todayId,
                    saveStatus = uiState.saveStatus,
                    onPreviousDay = { viewModel.shiftDate(-1) },
                    onNextDay = { viewModel.shiftDate(1) },
                    onOpenDatePicker = { showDatePicker = true },
                    onJumpToToday = { viewModel.selectDate(todayId) },
                    onSignOutClick = onSignOutClick,
                )
            }

            if (!uiState.errorMessage.isNullOrBlank()) {
                item(key = "error_banner") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(LossBg)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = uiState.errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LossRed,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss", color = LossRed)
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                item(key = "loading_indicator") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = ForestGreen)
                    }
                }
            }

            // 2. PURCHASE DETAILS
            item(key = "purchase_section") {
                PurchaseDetailsCard(
                    texts = uiState.inputTexts,
                    totalPurchaseAmount = uiState.calculation.totalPurchaseAmount,
                    onPcsChange = viewModel::updatePurchasePcs,
                    onWeightChange = viewModel::updatePurchaseWeight,
                    onRateChange = viewModel::updatePurchaseRate,
                )
            }

            // 3 & 4. CUSTOMER SALES + TOTAL SALES SUMMARY
            item(key = "customer_sales_section") {
                CustomerSalesCard(
                    sales = uiState.sales,
                    calculation = uiState.calculation,
                    onAddCustomer = {
                        editingCustomer = null
                        showCustomerDialog = true
                    },
                    onEditCustomer = { sale ->
                        editingCustomer = sale
                        showCustomerDialog = true
                    },
                    onDeleteCustomer = { sale ->
                        deletingCustomer = sale
                    },
                )
            }

            // 5. CLOSING STOCK & WEIGHT LOSS
            item(key = "stock_section") {
                StockCalculationCard(
                    texts = uiState.inputTexts,
                    calculation = uiState.calculation,
                    onClosingPcsChange = viewModel::updateActualClosingPcs,
                    onClosingWeightChange = viewModel::updateActualClosingWeight,
                )
            }

            // 6. DAILY INCOME
            item(key = "income_section") {
                DailyIncomeCard(
                    remainingStockValueText = uiState.inputTexts.remainingStockValueText,
                    onRemainingStockValueChange = viewModel::updateRemainingStockValue,
                    calculation = uiState.calculation,
                )
            }

            // 7. DAILY EXPENSES & FINAL PROFIT
            item(key = "expenses_section") {
                ExpensesAndProfitCard(
                    texts = uiState.inputTexts,
                    calculation = uiState.calculation,
                    onShopExpensesChange = viewModel::updateShopExpenses,
                    onWorkerPaymentChange = viewModel::updateWorkerPayment,
                    onOtherExpensesChange = viewModel::updateOtherExpenses,
                )
            }

            // 8. FINAL DAILY REPORT
            item(key = "final_report_section") {
                FinalDailyReportCard(
                    selectedDateId = uiState.selectedDateId,
                    calculation = uiState.calculation,
                )
            }

            // 9. DOWNLOAD / SHARE DAILY PDF BUTTON
            item(key = "pdf_action") {
                Button(
                    onClick = {
                        viewModel.generateAndShareDailyPdf(
                            context = context,
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
                                    dateId = uiState.selectedDateId,
                                )
                            },
                            onError = { err ->
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            },
                        )
                    },
                    enabled = !uiState.isGeneratingPdf,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForestGreen,
                        contentColor = PureWhite,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("download_share_pdf_button"),
                ) {
                    if (uiState.isGeneratingPdf) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = PureWhite,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Generating PDF...", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.download_share_pdf),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ShopHeaderCard(
    selectedDateId: String,
    isToday: Boolean,
    saveStatus: SaveStatus,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onOpenDatePicker: () -> Unit,
    onJumpToToday: () -> Unit,
    onSignOutClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SoftGreenSurface)
            .border(1.dp, MintBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.shop_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = ForestGreen,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.shop_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryInk,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SaveStatusPill(saveStatus = saveStatus)
                IconButton(
                    onClick = onSignOutClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("sign_out_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Sign out",
                        tint = SecondaryInk,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Clearly visible editable Date Box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PureWhite)
                .border(1.5.dp, ForestGreen, RoundedCornerShape(12.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPreviousDay,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("prev_date_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous day",
                    tint = ForestGreen,
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenDatePicker)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
                    .testTag("date_picker_box"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarToday,
                    contentDescription = "Select date",
                    tint = ForestGreen,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = LedgerFormatter.formatDisplayDate(selectedDateId),
                    style = MaterialTheme.typography.titleMedium,
                    color = DarkInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = onNextDay,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("next_date_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next day",
                    tint = ForestGreen,
                )
            }
        }

        if (!isToday) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Editing past/selected date record",
                    style = MaterialTheme.typography.labelMedium,
                    color = WarningAmber,
                )
                TextButton(
                    onClick = onJumpToToday,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("jump_today_button"),
                ) {
                    Text("Go to Today", color = ForestGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SaveStatusPill(saveStatus: SaveStatus) {
    val (label, bg, fg) = when (saveStatus) {
        SaveStatus.IDLE -> return
        SaveStatus.SAVING -> Triple("Saving...", WarningBg, WarningAmber)
        SaveStatus.SAVED -> Triple("Saved", DeepGreenContainer, ForestGreen)
        SaveStatus.ERROR -> Triple("Sync Error", LossBg, LossRed)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = ForestGreen,
                    fontWeight = FontWeight.Bold,
                )
                trailing?.invoke()
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun LedgerNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isDecimal: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = { Text(if (isDecimal) "0.00" else "0", color = MutedInk) },
        singleLine = true,
        textStyle = NumberMediumStyle.copy(color = DarkInk),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ForestGreen,
            unfocusedBorderColor = MintBorder,
            focusedLabelColor = ForestGreen,
            unfocusedLabelColor = SecondaryInk,
            cursorColor = ForestGreen,
            focusedContainerColor = PureWhite,
            unfocusedContainerColor = PureWhite,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        modifier = modifier.testTag(tag),
    )
}

@Composable
private fun PurchaseDetailsCard(
    texts: DailyInputTextState,
    totalPurchaseAmount: Double,
    onPcsChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onRateChange: (String) -> Unit,
) {
    SectionCard(title = "PURCHASE DETAILS") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LedgerNumberField(
                value = texts.purchasePcsText,
                onValueChange = onPcsChange,
                label = "Purchase PCS",
                isDecimal = false,
                tag = "purchase_pcs_input",
                modifier = Modifier.weight(1f),
            )
            LedgerNumberField(
                value = texts.purchaseWeightText,
                onValueChange = onWeightChange,
                label = "Weight (KG)",
                isDecimal = true,
                tag = "purchase_weight_input",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LedgerNumberField(
            value = texts.purchaseRateText,
            onValueChange = onRateChange,
            label = "Purchase Rate Per KG (₹)",
            isDecimal = true,
            tag = "purchase_rate_input",
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        HighlightTotalBox(
            label = "Total Purchase Amount",
            value = LedgerFormatter.formatInr(totalPurchaseAmount),
            valueColor = ForestGreen,
            bgColor = SoftGreenSurface,
        )
    }
}

@Composable
private fun CustomerSalesCard(
    sales: List<CustomerSaleDoc>,
    calculation: DailyCalculation,
    onAddCustomer: () -> Unit,
    onEditCustomer: (CustomerSaleDoc) -> Unit,
    onDeleteCustomer: (CustomerSaleDoc) -> Unit,
) {
    SectionCard(
        title = "CUSTOMER SALES (${sales.size})",
        trailing = {
            Button(
                onClick = onAddCustomer,
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("add_customer_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.add_customer), style = MaterialTheme.typography.labelLarge)
            }
        },
    ) {
        if (sales.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoftGreenSurface)
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No customer sales added yet.\nTap \"Add Customer\" to record a sale.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryInk,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                sales.forEachIndexed { index, sale ->
                    CustomerSaleRowCard(
                        index = index + 1,
                        sale = sale,
                        onEdit = { onEditCustomer(sale) },
                        onDelete = { onDeleteCustomer(sale) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. TOTAL SALES SUMMARY
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SoftGreenSurface)
                .border(1.dp, MintBorder, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "TOTAL SALES SUMMARY",
                style = MaterialTheme.typography.labelLarge,
                color = ForestGreen,
                fontWeight = FontWeight.Bold,
            )
            HorizontalDivider(color = MintBorder)
            SummaryRow("Total Sold PCS", "${calculation.totalSoldPcs} PCS")
            SummaryRow("Total Sold Weight", LedgerFormatter.formatKg(calculation.totalSoldWeightKg))
            SummaryRow(
                "Total Sales Amount",
                LedgerFormatter.formatInr(calculation.totalSalesAmount),
                valueColor = ForestGreen,
                bold = true,
            )
            SummaryRow(
                "Total Deposit Received",
                LedgerFormatter.formatInr(calculation.totalDepositReceived),
            )
            SummaryRow(
                "Total Remaining Amount",
                LedgerFormatter.formatInr(calculation.totalRemainingAmount),
                valueColor = if (calculation.totalRemainingAmount > 0.01) LossRed else ForestGreen,
                bold = true,
            )
        }
    }
}

@Composable
private fun CustomerSaleRowCard(
    index: Int,
    sale: CustomerSaleDoc,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PureWhite)
            .border(1.dp, SubtleDivider, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$index. ${sale.customerName}",
                style = MaterialTheme.typography.titleMedium,
                color = DarkInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("edit_customer_${sale.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit ${sale.customerName}",
                        tint = ForestGreen,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_customer_${sale.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete ${sale.customerName}",
                        tint = LossRed,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricMiniColumn(label = "PCS", value = "${sale.pcs}")
            MetricMiniColumn(label = "Weight", value = LedgerFormatter.formatKg(sale.weightKg))
            MetricMiniColumn(label = "Rate", value = "${LedgerFormatter.formatInr(sale.ratePerKg)}/KG")
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = SubtleDivider)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricMiniColumn(
                label = "Total",
                value = LedgerFormatter.formatInr(sale.totalAmount),
                valueColor = DarkInk,
            )
            MetricMiniColumn(
                label = "Deposit",
                value = LedgerFormatter.formatInr(sale.depositAmount),
                valueColor = LeafGreen,
            )
            MetricMiniColumn(
                label = "Remaining",
                value = LedgerFormatter.formatInr(sale.remainingAmount),
                valueColor = if (sale.remainingAmount > 0.01) LossRed else ForestGreen,
            )
        }
    }
}

@Composable
private fun MetricMiniColumn(
    label: String,
    value: String,
    valueColor: Color = DarkInk,
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MutedInk)
        Text(text = value, style = NumberBodyStyle, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StockCalculationCard(
    texts: DailyInputTextState,
    calculation: DailyCalculation,
    onClosingPcsChange: (String) -> Unit,
    onClosingWeightChange: (String) -> Unit,
) {
    SectionCard(title = "STOCK CALCULATION") {
        // Expected / Available Stock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SoftGreenSurface)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Available Stock (PCS)", style = MaterialTheme.typography.labelMedium, color = SecondaryInk)
                Text(
                    text = "${calculation.availableStockPcs} PCS",
                    style = NumberMediumStyle,
                    color = if (calculation.availableStockPcs < 0L) LossRed else DarkInk,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Available Weight", style = MaterialTheme.typography.labelMedium, color = SecondaryInk)
                Text(
                    text = LedgerFormatter.formatKg(calculation.availableWeightKg),
                    style = NumberMediumStyle,
                    color = if (calculation.availableWeightKg < -0.01) LossRed else DarkInk,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LedgerNumberField(
                value = texts.actualClosingPcsText,
                onValueChange = onClosingPcsChange,
                label = "Actual Closing PCS",
                isDecimal = false,
                tag = "closing_pcs_input",
                modifier = Modifier.weight(1f),
            )
            LedgerNumberField(
                value = texts.actualClosingWeightText,
                onValueChange = onClosingWeightChange,
                label = "Actual Closing (KG)",
                isDecimal = true,
                tag = "closing_weight_input",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // PCS Difference & Status
        val (pcsBadge, pcsColor, pcsBg) = when (calculation.pcsStatus) {
            StockStatus.MATCHING -> Triple("Matching", ForestGreen, ProfitBg)
            StockStatus.SHORTAGE -> Triple("Shortage: ${calculation.pcsDifference} PCS", LossRed, LossBg)
            StockStatus.EXCESS -> Triple("Excess: ${-calculation.pcsDifference} PCS", WarningAmber, WarningBg)
        }
        StatusRow(
            label = "PCS Difference (Expected − Actual)",
            value = "${calculation.pcsDifference} PCS",
            badgeText = pcsBadge,
            badgeColor = pcsColor,
            badgeBg = pcsBg,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Weight Difference / Weight Loss
        val (wtBadge, wtColor, wtBg) = when (calculation.weightStatus) {
            StockStatus.MATCHING -> Triple("Matching", ForestGreen, ProfitBg)
            StockStatus.SHORTAGE -> Triple(
                "Weight Loss: ${LedgerFormatter.formatKg(calculation.weightLossKg)}",
                WarningAmber,
                WarningBg,
            )
            StockStatus.EXCESS -> Triple(
                "Excess: ${LedgerFormatter.formatKg(abs(calculation.weightLossKg))}",
                ForestGreen,
                ProfitBg,
            )
        }
        StatusRow(
            label = "Weight Difference / Weight Loss",
            value = LedgerFormatter.formatKg(calculation.weightLossKg),
            badgeText = wtBadge,
            badgeColor = wtColor,
            badgeBg = wtBg,
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    badgeText: String,
    badgeColor: Color,
    badgeBg: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, SubtleDivider, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = SecondaryInk, modifier = Modifier.weight(1f))
            Text(text = value, style = NumberMediumStyle, color = DarkInk)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(badgeBg)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelMedium,
                color = badgeColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DailyIncomeCard(
    remainingStockValueText: String,
    onRemainingStockValueChange: (String) -> Unit,
    calculation: DailyCalculation,
) {
    SectionCard(title = "DAILY INCOME") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryRow("Total Sales Amount", LedgerFormatter.formatInr(calculation.totalSalesAmount))
            LedgerNumberField(
                value = remainingStockValueText,
                onValueChange = onRemainingStockValueChange,
                label = "Remaining Stock Value (₹)",
                isDecimal = true,
                tag = "remaining_stock_value_input",
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider(color = SubtleDivider)
            SummaryRow(
                "Total Business Value",
                LedgerFormatter.formatInr(calculation.totalBusinessValue),
                bold = true,
            )
            SummaryRow(
                "Less: Total Purchase Cost",
                LedgerFormatter.formatInr(calculation.totalPurchaseAmount),
            )
            Spacer(modifier = Modifier.height(2.dp))
            HighlightTotalBox(
                label = "Gross Income",
                value = LedgerFormatter.formatInr(calculation.grossIncome),
                valueColor = if (calculation.grossIncome >= -0.005) ForestGreen else LossRed,
                bgColor = if (calculation.grossIncome >= -0.005) SoftGreenSurface else LossBg,
            )
        }
    }
}

@Composable
private fun ExpensesAndProfitCard(
    texts: DailyInputTextState,
    calculation: DailyCalculation,
    onShopExpensesChange: (String) -> Unit,
    onWorkerPaymentChange: (String) -> Unit,
    onOtherExpensesChange: (String) -> Unit,
) {
    SectionCard(title = "EXPENSES") {
        LedgerNumberField(
            value = texts.shopExpensesText,
            onValueChange = onShopExpensesChange,
            label = "Shop Daily Expenses (₹)",
            isDecimal = true,
            tag = "shop_expenses_input",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LedgerNumberField(
                value = texts.workerPaymentText,
                onValueChange = onWorkerPaymentChange,
                label = "Worker Payment (₹)",
                isDecimal = true,
                tag = "worker_payment_input",
                modifier = Modifier.weight(1f),
            )
            LedgerNumberField(
                value = texts.otherExpensesText,
                onValueChange = onOtherExpensesChange,
                label = "Other Expenses (₹)",
                isDecimal = true,
                tag = "other_expenses_input",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        SummaryRow(
            label = "Total Expenses",
            value = LedgerFormatter.formatInr(calculation.totalExpenses),
            bold = true,
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Large, Bold FINAL DAILY PROFIT / LOSS banner
        val isProfit = calculation.isProfit
        val containerColor = if (isProfit) ForestGreen else LossRed
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(containerColor)
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .testTag("final_profit_banner"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isProfit) "FINAL DAILY PROFIT" else "FINAL DAILY LOSS",
                style = MaterialTheme.typography.titleMedium,
                color = PureWhite.copy(alpha = 0.92f),
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = LedgerFormatter.formatInr(calculation.finalDailyProfit),
                style = NumberDisplayStyle.copy(fontSize = 30.sp, lineHeight = 36.sp),
                color = PureWhite,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FinalDailyReportCard(
    selectedDateId: String,
    calculation: DailyCalculation,
) {
    SectionCard(
        title = "FINAL DAILY REPORT",
        trailing = {
            Text(
                text = LedgerFormatter.formatShortDate(selectedDateId),
                style = MaterialTheme.typography.labelMedium,
                color = SecondaryInk,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ReportGroupHeader("PURCHASE")
            SummaryRow("Total Purchase PCS", "${calculation.purchasePcs} PCS")
            SummaryRow("Total Purchase Weight", LedgerFormatter.formatKg(calculation.purchaseWeightKg))
            SummaryRow("Purchase Rate", "${LedgerFormatter.formatInr(calculation.purchaseRatePerKg)}/KG")
            SummaryRow("Purchase Amount", LedgerFormatter.formatInr(calculation.totalPurchaseAmount), bold = true)

            HorizontalDivider(color = SubtleDivider)
            ReportGroupHeader("SALES")
            SummaryRow("Total Sold PCS", "${calculation.totalSoldPcs} PCS")
            SummaryRow("Total Sold Weight", LedgerFormatter.formatKg(calculation.totalSoldWeightKg))
            SummaryRow("Total Sales Amount", LedgerFormatter.formatInr(calculation.totalSalesAmount), bold = true)
            SummaryRow("Total Deposit", LedgerFormatter.formatInr(calculation.totalDepositReceived))
            SummaryRow(
                "Total Customer Pending Amount",
                LedgerFormatter.formatInr(calculation.totalRemainingAmount),
                valueColor = if (calculation.totalRemainingAmount > 0.01) LossRed else ForestGreen,
                bold = true,
            )

            HorizontalDivider(color = SubtleDivider)
            ReportGroupHeader("CLOSING STOCK")
            SummaryRow("Remaining PCS (Expected)", "${calculation.availableStockPcs} PCS")
            SummaryRow("Remaining Weight (Expected)", LedgerFormatter.formatKg(calculation.availableWeightKg))
            SummaryRow(
                "Actual Closing Stock",
                "${calculation.actualClosingPcs} PCS / ${LedgerFormatter.formatKg(calculation.actualClosingWeightKg)}",
            )
            SummaryRow("Weight Loss", LedgerFormatter.formatKg(calculation.weightLossKg))
            SummaryRow(
                "Stock Difference (PCS)",
                when (calculation.pcsStatus) {
                    StockStatus.MATCHING -> "Matching (0)"
                    StockStatus.SHORTAGE -> "Shortage (${calculation.pcsDifference})"
                    StockStatus.EXCESS -> "Excess (${-calculation.pcsDifference})"
                },
            )

            HorizontalDivider(color = SubtleDivider)
            ReportGroupHeader("INCOME")
            SummaryRow("Total Sales Value", LedgerFormatter.formatInr(calculation.totalSalesAmount))
            SummaryRow("Remaining Stock Value", LedgerFormatter.formatInr(calculation.closingStockValue))
            SummaryRow("Total Business Value", LedgerFormatter.formatInr(calculation.totalBusinessValue))
            SummaryRow("Gross Income", LedgerFormatter.formatInr(calculation.grossIncome), bold = true)
            SummaryRow("Total Expenses", LedgerFormatter.formatInr(calculation.totalExpenses))
            HighlightTotalBox(
                label = if (calculation.isProfit) "FINAL DAILY PROFIT" else "FINAL DAILY LOSS",
                value = LedgerFormatter.formatInr(calculation.finalDailyProfit),
                valueColor = if (calculation.isProfit) ProfitGreen else LossRed,
                bgColor = if (calculation.isProfit) ProfitBg else LossBg,
            )
        }
    }
}

@Composable
private fun ReportGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = ForestGreen,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: Color = DarkInk,
    bold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = if (bold) DarkInk else SecondaryInk,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = if (bold) NumberMediumStyle else NumberBodyStyle,
            color = valueColor,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun HighlightTotalBox(
    label: String,
    value: String,
    valueColor: Color,
    bgColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(BorderStroke(1.dp, valueColor.copy(alpha = 0.25f)), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = DarkInk,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = NumberMediumStyle.copy(fontSize = 18.sp),
            color = valueColor,
            fontWeight = FontWeight.Bold,
        )
    }
}
