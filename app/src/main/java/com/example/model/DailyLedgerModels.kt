package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

@Keep
@IgnoreExtraProperties
data class DailyRecordDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val date: String = "",
    val purchasePcs: Long = 0L,
    val purchaseWeightKg: Double = 0.0,
    val purchaseRatePerKg: Double = 0.0,
    val actualClosingPcs: Long = 0L,
    val actualClosingWeightKg: Double = 0.0,
    val remainingStockValue: Double = 0.0,
    val shopExpenses: Double = 0.0,
    val workerPayment: Double = 0.0,
    val otherExpenses: Double = 0.0,
    val customerCount: Long = 0L,
    val totalSoldPcs: Long = 0L,
    val totalSoldWeightKg: Double = 0.0,
    val totalSalesAmount: Double = 0.0,
    val totalDepositAmount: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toInputs(): DailyInputs = DailyInputs(
        purchasePcs = purchasePcs,
        purchaseWeightKg = purchaseWeightKg,
        purchaseRatePerKg = purchaseRatePerKg,
        actualClosingPcs = actualClosingPcs,
        actualClosingWeightKg = actualClosingWeightKg,
        remainingStockValue = remainingStockValue,
        shopExpenses = shopExpenses,
        workerPayment = workerPayment,
        otherExpenses = otherExpenses,
    )
}

@Keep
@IgnoreExtraProperties
data class CustomerSaleDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val recordDate: String = "",
    val customerName: String = "",
    val pcs: Long = 0L,
    val weightKg: Double = 0.0,
    val ratePerKg: Double = 0.0,
    val depositAmount: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val totalAmount: Double
        get() = weightKg * ratePerKg

    val remainingAmount: Double
        get() = totalAmount - depositAmount
}

data class DailyInputs(
    val purchasePcs: Long = 0L,
    val purchaseWeightKg: Double = 0.0,
    val purchaseRatePerKg: Double = 0.0,
    val actualClosingPcs: Long = 0L,
    val actualClosingWeightKg: Double = 0.0,
    val remainingStockValue: Double = 0.0,
    val shopExpenses: Double = 0.0,
    val workerPayment: Double = 0.0,
    val otherExpenses: Double = 0.0,
) {
    fun hasAnyValue(): Boolean =
        purchasePcs > 0L ||
            purchaseWeightKg > 0.0 ||
            purchaseRatePerKg > 0.0 ||
            actualClosingPcs > 0L ||
            actualClosingWeightKg > 0.0 ||
            remainingStockValue > 0.0 ||
            shopExpenses > 0.0 ||
            workerPayment > 0.0 ||
            otherExpenses > 0.0
}

enum class StockStatus {
    MATCHING,
    SHORTAGE,
    EXCESS,
}

data class DailyCalculation(
    val purchasePcs: Long,
    val purchaseWeightKg: Double,
    val purchaseRatePerKg: Double,
    val totalPurchaseAmount: Double,
    val totalSoldPcs: Long,
    val totalSoldWeightKg: Double,
    val totalSalesAmount: Double,
    val totalDepositReceived: Double,
    val totalRemainingAmount: Double,
    val availableStockPcs: Long,
    val availableWeightKg: Double,
    val actualClosingPcs: Long,
    val actualClosingWeightKg: Double,
    val pcsDifference: Long,
    val weightDifferenceKg: Double,
    val weightLossKg: Double,
    val pcsStatus: StockStatus,
    val weightStatus: StockStatus,
    val closingStockValue: Double,
    val totalBusinessValue: Double,
    val grossIncome: Double,
    val shopExpenses: Double,
    val workerPayment: Double,
    val otherExpenses: Double,
    val totalExpenses: Double,
    val finalDailyProfit: Double,
) {
    val isProfit: Boolean
        get() = finalDailyProfit >= -0.005

    companion object {
        fun compute(inputs: DailyInputs, sales: List<CustomerSaleDoc>): DailyCalculation {
            val totalPurchaseAmount = inputs.purchaseWeightKg * inputs.purchaseRatePerKg

            val totalSoldPcs = sales.sumOf { it.pcs }
            val totalSoldWeightKg = sales.sumOf { it.weightKg }
            val totalSalesAmount = sales.sumOf { it.totalAmount }
            val totalDepositReceived = sales.sumOf { it.depositAmount }
            val totalRemainingAmount = sales.sumOf { it.remainingAmount }

            val availableStockPcs = inputs.purchasePcs - totalSoldPcs
            val availableWeightKg = inputs.purchaseWeightKg - totalSoldWeightKg

            val pcsDifference = availableStockPcs - inputs.actualClosingPcs
            val weightDifferenceKg = availableWeightKg - inputs.actualClosingWeightKg
            val weightLossKg = availableWeightKg - inputs.actualClosingWeightKg

            val pcsStatus = when {
                pcsDifference == 0L -> StockStatus.MATCHING
                pcsDifference > 0L -> StockStatus.SHORTAGE
                else -> StockStatus.EXCESS
            }
            val weightStatus = when {
                abs(weightDifferenceKg) < 0.01 -> StockStatus.MATCHING
                weightDifferenceKg > 0.0 -> StockStatus.SHORTAGE
                else -> StockStatus.EXCESS
            }

            val closingStockValue = inputs.remainingStockValue
            val totalBusinessValue = totalSalesAmount + closingStockValue
            val grossIncome = totalBusinessValue - totalPurchaseAmount

            val totalExpenses = inputs.shopExpenses + inputs.workerPayment + inputs.otherExpenses
            val finalDailyProfit = grossIncome - totalExpenses

            return DailyCalculation(
                purchasePcs = inputs.purchasePcs,
                purchaseWeightKg = inputs.purchaseWeightKg,
                purchaseRatePerKg = inputs.purchaseRatePerKg,
                totalPurchaseAmount = totalPurchaseAmount,
                totalSoldPcs = totalSoldPcs,
                totalSoldWeightKg = totalSoldWeightKg,
                totalSalesAmount = totalSalesAmount,
                totalDepositReceived = totalDepositReceived,
                totalRemainingAmount = totalRemainingAmount,
                availableStockPcs = availableStockPcs,
                availableWeightKg = availableWeightKg,
                actualClosingPcs = inputs.actualClosingPcs,
                actualClosingWeightKg = inputs.actualClosingWeightKg,
                pcsDifference = pcsDifference,
                weightDifferenceKg = weightDifferenceKg,
                weightLossKg = weightLossKg,
                pcsStatus = pcsStatus,
                weightStatus = weightStatus,
                closingStockValue = closingStockValue,
                totalBusinessValue = totalBusinessValue,
                grossIncome = grossIncome,
                shopExpenses = inputs.shopExpenses,
                workerPayment = inputs.workerPayment,
                otherExpenses = inputs.otherExpenses,
                totalExpenses = totalExpenses,
                finalDailyProfit = finalDailyProfit,
            )
        }

        fun computeFromRecord(record: DailyRecordDoc): DailyCalculation {
            val totalPurchaseAmount = record.purchaseWeightKg * record.purchaseRatePerKg
            val totalRemainingAmount = record.totalSalesAmount - record.totalDepositAmount
            val availableStockPcs = record.purchasePcs - record.totalSoldPcs
            val availableWeightKg = record.purchaseWeightKg - record.totalSoldWeightKg
            val pcsDifference = availableStockPcs - record.actualClosingPcs
            val weightDifferenceKg = availableWeightKg - record.actualClosingWeightKg
            val closingStockValue = record.remainingStockValue
            val totalBusinessValue = record.totalSalesAmount + closingStockValue
            val grossIncome = totalBusinessValue - totalPurchaseAmount
            val totalExpenses = record.shopExpenses + record.workerPayment + record.otherExpenses
            val finalDailyProfit = grossIncome - totalExpenses

            return DailyCalculation(
                purchasePcs = record.purchasePcs,
                purchaseWeightKg = record.purchaseWeightKg,
                purchaseRatePerKg = record.purchaseRatePerKg,
                totalPurchaseAmount = totalPurchaseAmount,
                totalSoldPcs = record.totalSoldPcs,
                totalSoldWeightKg = record.totalSoldWeightKg,
                totalSalesAmount = record.totalSalesAmount,
                totalDepositReceived = record.totalDepositAmount,
                totalRemainingAmount = totalRemainingAmount,
                availableStockPcs = availableStockPcs,
                availableWeightKg = availableWeightKg,
                actualClosingPcs = record.actualClosingPcs,
                actualClosingWeightKg = record.actualClosingWeightKg,
                pcsDifference = pcsDifference,
                weightDifferenceKg = weightDifferenceKg,
                weightLossKg = weightDifferenceKg,
                pcsStatus = when {
                    pcsDifference == 0L -> StockStatus.MATCHING
                    pcsDifference > 0L -> StockStatus.SHORTAGE
                    else -> StockStatus.EXCESS
                },
                weightStatus = when {
                    abs(weightDifferenceKg) < 0.01 -> StockStatus.MATCHING
                    weightDifferenceKg > 0.0 -> StockStatus.SHORTAGE
                    else -> StockStatus.EXCESS
                },
                closingStockValue = closingStockValue,
                totalBusinessValue = totalBusinessValue,
                grossIncome = grossIncome,
                shopExpenses = record.shopExpenses,
                workerPayment = record.workerPayment,
                otherExpenses = record.otherExpenses,
                totalExpenses = totalExpenses,
                finalDailyProfit = finalDailyProfit,
            )
        }
    }
}

object LedgerFormatter {
    private val dateIdFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
    private val displayDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("dd MMM yyyy (EEE)", Locale.US)
    }
    private val shortDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("dd MMM yyyy", Locale.US)
    }
    private val weightFormat = ThreadLocal.withInitial {
        DecimalFormat("#,##0.00")
    }

    fun todayDateId(): String = dateIdFormat.get()!!.format(Date())

    fun shiftDateId(dateId: String, daysDelta: Int): String {
        val parsed = runCatching { dateIdFormat.get()!!.parse(dateId) }.getOrNull() ?: Date()
        val cal = Calendar.getInstance().apply {
            time = parsed
            add(Calendar.DAY_OF_MONTH, daysDelta)
        }
        return dateIdFormat.get()!!.format(cal.time)
    }

    fun epochMillisToDateId(utcMillis: Long): String {
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMillis
        }
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    fun dateIdToUtcMillis(dateId: String): Long {
        val parts = dateId.split("-")
        if (parts.size != 3) return System.currentTimeMillis()
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                parts[0].toIntOrNull() ?: 2026,
                (parts[1].toIntOrNull() ?: 1) - 1,
                parts[2].toIntOrNull() ?: 1,
            )
        }
        return cal.timeInMillis
    }

    fun formatDisplayDate(dateId: String): String {
        val parsed = runCatching { dateIdFormat.get()!!.parse(dateId) }.getOrNull() ?: return dateId
        return displayDateFormat.get()!!.format(parsed)
    }

    fun formatShortDate(dateId: String): String {
        val parsed = runCatching { dateIdFormat.get()!!.parse(dateId) }.getOrNull() ?: return dateId
        return shortDateFormat.get()!!.format(parsed)
    }

    fun formatKg(kg: Double): String = "${weightFormat.get()!!.format(kg)} KG"

    fun formatPlainDecimal(value: Double): String {
        if (value == 0.0) return ""
        val rounded = (value * 100.0).roundToLong() / 100.0
        return if (abs(rounded - rounded.toLong()) < 0.001) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", rounded)
        }
    }

    fun formatInr(amount: Double, includeSymbol: Boolean = true): String {
        val isNegative = amount < -0.004
        val absValue = abs(amount)
        val totalPaise = (absValue * 100.0).roundToLong()
        val whole = totalPaise / 100L
        val paise = (totalPaise % 100L).toInt()
        val groupedWhole = formatIndianInteger(whole)
        val numberStr = if (paise == 0) {
            groupedWhole
        } else {
            "$groupedWhole.${String.format(Locale.US, "%02d", paise)}"
        }
        val prefix = if (isNegative) "-" else ""
        val symbol = if (includeSymbol) "₹" else ""
        return "$prefix$symbol$numberStr"
    }

    fun formatInrPdf(amount: Double): String {
        val isNegative = amount < -0.004
        val absValue = abs(amount)
        val totalPaise = (absValue * 100.0).roundToLong()
        val whole = totalPaise / 100L
        val paise = (totalPaise % 100L).toInt()
        val groupedWhole = formatIndianInteger(whole)
        val numberStr = if (paise == 0) {
            groupedWhole
        } else {
            "$groupedWhole.${String.format(Locale.US, "%02d", paise)}"
        }
        return if (isNegative) "-Rs. $numberStr" else "Rs. $numberStr"
    }

    private fun formatIndianInteger(value: Long): String {
        val raw = value.toString()
        if (raw.length <= 3) return raw
        val lastThree = raw.takeLast(3)
        val remaining = raw.dropLast(3)
        val groupedPrefix = remaining.reversed().chunked(2).joinToString(",").reversed()
        return "$groupedPrefix,$lastThree"
    }
}
