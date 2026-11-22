package com.example.ui.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.CustomerSaleDoc
import com.example.model.LedgerFormatter
import com.example.ui.theme.DarkInk
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.LossRed
import com.example.ui.theme.MintBorder
import com.example.ui.theme.NumberMediumStyle
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.SoftGreenSurface
import com.example.ui.theme.SubtleDivider

@Composable
fun CustomerSaleDialog(
    initialSale: CustomerSaleDoc?,
    defaultRateText: String,
    onDismiss: () -> Unit,
    onSave: (
        customerName: String,
        pcs: Long,
        weightKg: Double,
        ratePerKg: Double,
        depositAmount: Double,
        onComplete: (Boolean, String?) -> Unit,
    ) -> Unit,
) {
    var name by remember(initialSale) { mutableStateOf(initialSale?.customerName ?: "") }
    var pcsText by remember(initialSale) {
        mutableStateOf(if (initialSale != null && initialSale.pcs > 0L) initialSale.pcs.toString() else "")
    }
    var weightText by remember(initialSale) {
        mutableStateOf(
            if (initialSale != null) LedgerFormatter.formatPlainDecimal(initialSale.weightKg) else "",
        )
    }
    var rateText by remember(initialSale) {
        mutableStateOf(
            if (initialSale != null) {
                LedgerFormatter.formatPlainDecimal(initialSale.ratePerKg)
            } else {
                defaultRateText
            },
        )
    }
    var depositText by remember(initialSale) {
        mutableStateOf(
            if (initialSale != null) LedgerFormatter.formatPlainDecimal(initialSale.depositAmount) else "",
        )
    }

    var validationError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val weightVal = weightText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val rateVal = rateText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val depositVal = depositText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val totalAmount = weightVal * rateVal
    val remainingAmount = totalAmount - depositVal

    fun sanitizeInt(input: String): String = input.filter { it.isDigit() }.take(7)

    fun sanitizeDec(input: String): String {
        val cleaned = buildString {
            var hasDot = false
            for (ch in input) {
                if (ch.isDigit()) {
                    append(ch)
                } else if (ch == '.' && !hasDot) {
                    hasDot = true
                    if (isEmpty()) append('0')
                    append(ch)
                }
            }
        }
        val dotIdx = cleaned.indexOf('.')
        return if (dotIdx >= 0 && cleaned.length - dotIdx - 1 > 2) {
            cleaned.substring(0, dotIdx + 3)
        } else {
            cleaned.take(10)
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ForestGreen,
        unfocusedBorderColor = MintBorder,
        focusedLabelColor = ForestGreen,
        cursorColor = ForestGreen,
    )

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = PureWhite,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = if (initialSale == null) "Add Customer Sale" else "Edit Customer Sale",
                    style = MaterialTheme.typography.titleLarge,
                    color = ForestGreen,
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        if (it.length <= 100) {
                            name = it
                            validationError = null
                        }
                    },
                    label = { Text("Customer Name") },
                    placeholder = { Text("e.g. Ahmed Chicken Shop") },
                    singleLine = true,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_name_input"),
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = pcsText,
                        onValueChange = { pcsText = sanitizeInt(it) },
                        label = { Text("PCS") },
                        placeholder = { Text("0") },
                        singleLine = true,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("customer_pcs_input"),
                    )
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = {
                            weightText = sanitizeDec(it)
                            validationError = null
                        },
                        label = { Text("Weight (KG)") },
                        placeholder = { Text("0.00") },
                        singleLine = true,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("customer_weight_input"),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = {
                            rateText = sanitizeDec(it)
                            validationError = null
                        },
                        label = { Text("Rate (₹/KG)") },
                        placeholder = { Text("0") },
                        singleLine = true,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("customer_rate_input"),
                    )
                    OutlinedTextField(
                        value = depositText,
                        onValueChange = { depositText = sanitizeDec(it) },
                        label = { Text("Deposit (₹)") },
                        placeholder = { Text("0") },
                        singleLine = true,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("customer_deposit_input"),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live Auto-Calculation Preview
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftGreenSurface)
                        .border(1.dp, MintBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Total Amount",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryInk,
                        )
                        Text(
                            text = LedgerFormatter.formatInr(totalAmount),
                            style = NumberMediumStyle,
                            color = DarkInk,
                        )
                    }
                    HorizontalDivider(color = SubtleDivider)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Remaining Balance",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkInk,
                        )
                        Text(
                            text = LedgerFormatter.formatInr(remainingAmount),
                            style = NumberMediumStyle,
                            color = if (remainingAmount > 0.01) LossRed else ForestGreen,
                        )
                    }
                }

                if (!validationError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LossRed,
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier.testTag("cancel_customer_button"),
                    ) {
                        Text("Cancel", color = SecondaryInk)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmed = name.trim()
                            if (trimmed.isEmpty()) {
                                validationError = "Please enter customer name."
                                return@Button
                            }
                            if (weightVal <= 0.0 && (pcsText.toLongOrNull() ?: 0L) <= 0L) {
                                validationError = "Please enter PCS or Weight (KG)."
                                return@Button
                            }
                            if (rateVal <= 0.0) {
                                validationError = "Please enter selling rate per KG."
                                return@Button
                            }
                            isSaving = true
                            validationError = null
                            onSave(
                                trimmed,
                                pcsText.toLongOrNull() ?: 0L,
                                weightVal,
                                rateVal,
                                depositVal,
                            ) { success, err ->
                                isSaving = false
                                if (success) {
                                    onDismiss()
                                } else {
                                    validationError = err ?: "Could not save customer."
                                }
                            }
                        },
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        modifier = Modifier.testTag("save_customer_button"),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(if (initialSale == null) "Add Customer" else "Save Changes")
                        }
                    }
                }
            }
        }
    }
}
