package com.example

import com.example.model.CustomerSaleDoc
import com.example.model.DailyCalculation
import com.example.model.DailyInputs
import com.example.model.LedgerFormatter
import com.example.model.StockStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun purchaseSalesStockAndProfit_matchesShopkeeperNotebookExample() {
        val inputs = DailyInputs(
            purchasePcs = 100L,
            purchaseWeightKg = 278.90,
            purchaseRatePerKg = 100.0,
            actualClosingPcs = 10L,
            actualClosingWeightKg = 20.0,
            remainingStockValue = 2350.0,
            shopExpenses = 300.0,
            workerPayment = 500.0,
            otherExpenses = 200.0,
        )

        val sales = listOf(
            CustomerSaleDoc(
                id = "s1",
                customerName = "Ahmed Chicken Shop",
                pcs = 15L,
                weightKg = 42.50,
                ratePerKg = 110.0,
                depositAmount = 3000.0,
            ),
            CustomerSaleDoc(
                id = "s2",
                customerName = "Star Poultry",
                pcs = 75L,
                weightKg = 208.40,
                ratePerKg = 115.0,
                depositAmount = 20000.0,
            ),
        )

        val calc = DailyCalculation.compute(inputs, sales)

        // Purchase: 278.90 * 100 = 27890
        assertEquals(27890.0, calc.totalPurchaseAmount, 0.01)

        // Customer 1: 42.50 * 110 = 4675, Remaining = 1675
        assertEquals(4675.0, sales[0].totalAmount, 0.01)
        assertEquals(1675.0, sales[0].remainingAmount, 0.01)

        // Total Sold: 90 PCS, 250.90 KG, Sales = 4675 + 23966 = 28641
        assertEquals(90L, calc.totalSoldPcs)
        assertEquals(250.90, calc.totalSoldWeightKg, 0.01)
        assertEquals(28641.0, calc.totalSalesAmount, 0.01)
        assertEquals(23000.0, calc.totalDepositReceived, 0.01)
        assertEquals(5641.0, calc.totalRemainingAmount, 0.01)

        // Stock: Available = 10 PCS, 28.00 KG; Actual = 10 PCS, 20.00 KG -> Weight Loss = 8.00 KG
        assertEquals(10L, calc.availableStockPcs)
        assertEquals(28.00, calc.availableWeightKg, 0.01)
        assertEquals(0L, calc.pcsDifference)
        assertEquals(StockStatus.MATCHING, calc.pcsStatus)
        assertEquals(8.00, calc.weightLossKg, 0.01)
        assertEquals(StockStatus.SHORTAGE, calc.weightStatus)

        // Income: Manual Remaining Stock Value = 2350; Total Business Value = 28641 + 2350 = 30991; Gross = 3101
        assertEquals(2350.0, calc.closingStockValue, 0.01)
        assertEquals(30991.0, calc.totalBusinessValue, 0.01)
        assertEquals(3101.0, calc.grossIncome, 0.01)

        // Expenses: 1000; Final Profit = 2101
        assertEquals(1000.0, calc.totalExpenses, 0.01)
        assertEquals(2101.0, calc.finalDailyProfit, 0.01)
        assertTrue(calc.isProfit)
    }

    @Test
    fun negativeProfit_reportsAsLoss() {
        val inputs = DailyInputs(
            purchasePcs = 50L,
            purchaseWeightKg = 100.0,
            purchaseRatePerKg = 120.0,
            actualClosingPcs = 0L,
            actualClosingWeightKg = 0.0,
            shopExpenses = 1000.0,
            workerPayment = 500.0,
            otherExpenses = 0.0,
        )
        val sales = listOf(
            CustomerSaleDoc(
                id = "s1",
                customerName = "Retail Walk-in",
                pcs = 50L,
                weightKg = 92.0,
                ratePerKg = 125.0,
                depositAmount = 11500.0,
            ),
        )

        val calc = DailyCalculation.compute(inputs, sales)
        // Sales = 11500, Purchase = 12000 -> Gross = -500, Expenses = 1500 -> Final = -2000
        assertEquals(-500.0, calc.grossIncome, 0.01)
        assertEquals(-2000.0, calc.finalDailyProfit, 0.01)
        assertFalse(calc.isProfit)
        assertEquals("-₹2,000", LedgerFormatter.formatInr(calc.finalDailyProfit))
    }

    @Test
    fun formatInr_usesIndianCommaGrouping() {
        assertEquals("₹27,890", LedgerFormatter.formatInr(27890.0))
        assertEquals("₹1,25,000", LedgerFormatter.formatInr(125000.0))
        assertEquals("₹4,675.50", LedgerFormatter.formatInr(4675.5))
    }
}
