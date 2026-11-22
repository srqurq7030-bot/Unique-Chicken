package com.example.pdf

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.model.CustomerSaleDoc
import com.example.model.DailyCalculation
import com.example.model.DailyRecordDoc
import com.example.model.LedgerFormatter
import com.example.model.StockStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class GeneratedPdfResult(
    val shareUri: Uri,
    val fileName: String,
    val savedToDownloads: Boolean,
)

object DailyPdfGenerator {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 32f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_X * 2)
    private const val BOTTOM_LIMIT = PAGE_HEIGHT - 44f

    suspend fun generateAndSavePdf(
        context: Context,
        dateId: String,
        record: DailyRecordDoc,
        sales: List<CustomerSaleDoc>,
    ): Result<GeneratedPdfResult> = withContext(Dispatchers.IO) {
        runCatching {
            val calc = DailyCalculation.compute(record.toInputs(), sales)
            val pdfDocument = PdfDocument()

            val brandGreen = Color.rgb(27, 94, 32)
            val softGreenBg = Color.rgb(232, 245, 233)
            val lightGrayBg = Color.rgb(245, 247, 246)
            val borderGray = Color.rgb(205, 215, 208)
            val darkInk = Color.rgb(20, 27, 21)
            val mutedInk = Color.rgb(85, 98, 88)
            val lossRed = Color.rgb(198, 40, 40)
            val lossBg = Color.rgb(255, 235, 238)

            val titlePaint = Paint().apply {
                color = brandGreen
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val subtitlePaint = Paint().apply {
                color = mutedInk
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }
            val sectionHeaderPaint = Paint().apply {
                color = brandGreen
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val labelPaint = Paint().apply {
                color = darkInk
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }
            val boldLabelPaint = Paint().apply {
                color = darkInk
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val valueRightPaint = Paint().apply {
                color = darkInk
                textSize = 10f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
                isAntiAlias = true
            }
            val tableHeaderPaint = Paint().apply {
                color = Color.WHITE
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val tableCellPaint = Paint().apply {
                color = darkInk
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }
            val tableCellRightPaint = Paint().apply {
                color = darkInk
                textSize = 9f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
                isAntiAlias = true
            }
            val linePaint = Paint().apply {
                color = borderGray
                strokeWidth = 0.8f
            }
            val fillPaint = Paint().apply {
                style = Paint.Style.FILL
            }

            var pageNumber = 1
            var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            var canvas = page.canvas
            var y = 38f

            fun newPage() {
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = 38f
                canvas.drawText("UNIQUE CHICKEN - Daily Account (${LedgerFormatter.formatShortDate(dateId)}) (Cont.)", MARGIN_X, y, subtitlePaint)
                y += 18f
            }

            fun ensureSpace(neededHeight: Float) {
                if (y + neededHeight > BOTTOM_LIMIT) {
                    newPage()
                }
            }

            fun drawKeyValueRow(label: String, value: String, highlight: Boolean = false, valueColor: Int = darkInk) {
                ensureSpace(20f)
                if (highlight) {
                    fillPaint.color = softGreenBg
                    canvas.drawRect(MARGIN_X, y - 12f, PAGE_WIDTH - MARGIN_X, y + 6f, fillPaint)
                }
                val lp = if (highlight) boldLabelPaint else labelPaint
                val vp = Paint(valueRightPaint).apply { color = valueColor }
                canvas.drawText(label, MARGIN_X + 6f, y, lp)
                canvas.drawText(value, PAGE_WIDTH - MARGIN_X - 6f, y, vp)
                y += 17f
            }

            fun drawSectionTitle(title: String) {
                ensureSpace(32f)
                y += 6f
                fillPaint.color = softGreenBg
                canvas.drawRect(MARGIN_X, y - 13f, PAGE_WIDTH - MARGIN_X, y + 6f, fillPaint)
                canvas.drawText(title, MARGIN_X + 6f, y, sectionHeaderPaint)
                y += 18f
            }

            // HEADER
            canvas.drawText("UNIQUE CHICKEN", MARGIN_X, y, titlePaint)
            val dateBadge = "Date: ${LedgerFormatter.formatDisplayDate(dateId)}"
            val dateRightPaint = Paint(boldLabelPaint).apply {
                textAlign = Paint.Align.RIGHT
                textSize = 11f
            }
            canvas.drawText(dateBadge, PAGE_WIDTH - MARGIN_X, y, dateRightPaint)
            y += 15f
            canvas.drawText("Daily Business Account Report", MARGIN_X, y, subtitlePaint)
            y += 10f
            canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, Paint().apply { color = brandGreen; strokeWidth = 1.5f })
            y += 14f

            // 1. PURCHASE DETAILS
            drawSectionTitle("1. PURCHASE DETAILS")
            drawKeyValueRow("Total Purchase Pieces (PCS)", "${calc.purchasePcs} PCS")
            drawKeyValueRow("Total Purchase Weight (KG)", LedgerFormatter.formatKg(calc.purchaseWeightKg))
            drawKeyValueRow("Purchase Rate Per KG", LedgerFormatter.formatInrPdf(calc.purchaseRatePerKg))
            drawKeyValueRow("Total Purchase Amount", LedgerFormatter.formatInrPdf(calc.totalPurchaseAmount), highlight = true, valueColor = brandGreen)

            // 2. CUSTOMER-WISE SALES
            drawSectionTitle("2. CUSTOMER SALES & PENDING BALANCES (${sales.size} Customers)")

            // Column X positions (right-aligned for numbers, left-aligned for name)
            val colNameX = MARGIN_X + 4f
            val colPcsRight = MARGIN_X + 185f
            val colWtRight = MARGIN_X + 245f
            val colRateRight = MARGIN_X + 305f
            val colTotalRight = MARGIN_X + 382f
            val colDepRight = MARGIN_X + 458f
            val colRemRight = PAGE_WIDTH - MARGIN_X - 4f

            fun drawSalesTableHeader() {
                ensureSpace(24f)
                fillPaint.color = brandGreen
                canvas.drawRect(MARGIN_X, y - 12f, PAGE_WIDTH - MARGIN_X, y + 6f, fillPaint)
                val rightHeader = Paint(tableHeaderPaint).apply { textAlign = Paint.Align.RIGHT }
                canvas.drawText("Customer", colNameX, y, tableHeaderPaint)
                canvas.drawText("PCS", colPcsRight, y, rightHeader)
                canvas.drawText("Wt(KG)", colWtRight, y, rightHeader)
                canvas.drawText("Rate", colRateRight, y, rightHeader)
                canvas.drawText("Total", colTotalRight, y, rightHeader)
                canvas.drawText("Deposit", colDepRight, y, rightHeader)
                canvas.drawText("Balance", colRemRight, y, rightHeader)
                y += 16f
            }

            if (sales.isEmpty()) {
                ensureSpace(20f)
                canvas.drawText("No customer sales recorded for this date.", MARGIN_X + 6f, y, subtitlePaint)
                y += 18f
            } else {
                drawSalesTableHeader()
                sales.forEachIndexed { index, sale ->
                    if (y + 18f > BOTTOM_LIMIT) {
                        newPage()
                        drawSalesTableHeader()
                    }
                    if (index % 2 == 1) {
                        fillPaint.color = lightGrayBg
                        canvas.drawRect(MARGIN_X, y - 11f, PAGE_WIDTH - MARGIN_X, y + 5f, fillPaint)
                    }
                    val truncatedName = if (sale.customerName.length > 23) {
                        sale.customerName.take(21) + ".."
                    } else {
                        sale.customerName
                    }
                    canvas.drawText("${index + 1}. $truncatedName", colNameX, y, tableCellPaint)
                    canvas.drawText("${sale.pcs}", colPcsRight, y, tableCellRightPaint)
                    canvas.drawText(String.format("%.2f", sale.weightKg), colWtRight, y, tableCellRightPaint)
                    canvas.drawText(LedgerFormatter.formatInrPdf(sale.ratePerKg).removePrefix("Rs. "), colRateRight, y, tableCellRightPaint)
                    canvas.drawText(LedgerFormatter.formatInrPdf(sale.totalAmount).removePrefix("Rs. "), colTotalRight, y, tableCellRightPaint)
                    canvas.drawText(LedgerFormatter.formatInrPdf(sale.depositAmount).removePrefix("Rs. "), colDepRight, y, tableCellRightPaint)
                    val remPaint = if (sale.remainingAmount > 0.01) {
                        Paint(tableCellRightPaint).apply { color = lossRed; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
                    } else {
                        tableCellRightPaint
                    }
                    canvas.drawText(LedgerFormatter.formatInrPdf(sale.remainingAmount).removePrefix("Rs. "), colRemRight, y, remPaint)
                    y += 15f
                }

                // Grand Totals Row
                ensureSpace(22f)
                fillPaint.color = softGreenBg
                canvas.drawRect(MARGIN_X, y - 11f, PAGE_WIDTH - MARGIN_X, y + 6f, fillPaint)
                val rightBold = Paint(tableCellRightPaint).apply {
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    color = brandGreen
                }
                canvas.drawText("TOTAL", colNameX, y, boldLabelPaint)
                canvas.drawText("${calc.totalSoldPcs}", colPcsRight, y, rightBold)
                canvas.drawText(String.format("%.2f", calc.totalSoldWeightKg), colWtRight, y, rightBold)
                canvas.drawText("-", colRateRight, y, rightBold)
                canvas.drawText(LedgerFormatter.formatInrPdf(calc.totalSalesAmount).removePrefix("Rs. "), colTotalRight, y, rightBold)
                canvas.drawText(LedgerFormatter.formatInrPdf(calc.totalDepositReceived).removePrefix("Rs. "), colDepRight, y, rightBold)
                val remTotalPaint = Paint(rightBold).apply {
                    color = if (calc.totalRemainingAmount > 0.01) lossRed else brandGreen
                }
                canvas.drawText(LedgerFormatter.formatInrPdf(calc.totalRemainingAmount).removePrefix("Rs. "), colRemRight, y, remTotalPaint)
                y += 18f
            }

            // 3. CLOSING STOCK & WEIGHT LOSS
            drawSectionTitle("3. CLOSING STOCK & WEIGHT LOSS")
            drawKeyValueRow("Available / Expected Stock (PCS)", "${calc.availableStockPcs} PCS")
            drawKeyValueRow("Available / Expected Weight (KG)", LedgerFormatter.formatKg(calc.availableWeightKg))
            drawKeyValueRow("Actual Closing Stock (PCS)", "${calc.actualClosingPcs} PCS")
            drawKeyValueRow("Actual Closing Weight (KG)", LedgerFormatter.formatKg(calc.actualClosingWeightKg))
            val pcsStatusLabel = when (calc.pcsStatus) {
                StockStatus.MATCHING -> "Matching (0 PCS)"
                StockStatus.SHORTAGE -> "Shortage (${calc.pcsDifference} PCS)"
                StockStatus.EXCESS -> "Excess (${-calc.pcsDifference} PCS)"
            }
            drawKeyValueRow(
                "PCS Difference",
                pcsStatusLabel,
                valueColor = if (calc.pcsStatus == StockStatus.SHORTAGE) lossRed else brandGreen,
            )
            val weightStatusLabel = when (calc.weightStatus) {
                StockStatus.MATCHING -> "Matching (0.00 KG)"
                StockStatus.SHORTAGE -> "Loss / Shortage (${LedgerFormatter.formatKg(calc.weightLossKg)})"
                StockStatus.EXCESS -> "Excess (${LedgerFormatter.formatKg(-calc.weightLossKg)})"
            }
            drawKeyValueRow(
                "Weight Loss / Difference",
                weightStatusLabel,
                highlight = true,
                valueColor = if (calc.weightStatus == StockStatus.SHORTAGE) lossRed else brandGreen,
            )

            // 4. DAILY EXPENSES
            drawSectionTitle("4. DAILY EXPENSES")
            drawKeyValueRow("Shop Daily Expenses", LedgerFormatter.formatInrPdf(calc.shopExpenses))
            drawKeyValueRow("Worker Payment", LedgerFormatter.formatInrPdf(calc.workerPayment))
            drawKeyValueRow("Other Expenses", LedgerFormatter.formatInrPdf(calc.otherExpenses))
            drawKeyValueRow("Total Daily Expenses", LedgerFormatter.formatInrPdf(calc.totalExpenses), highlight = true)

            // 5. DAILY INCOME & FINAL PROFIT
            drawSectionTitle("5. DAILY INCOME & FINAL PROFIT")
            drawKeyValueRow("Total Sales Amount", LedgerFormatter.formatInrPdf(calc.totalSalesAmount))
            drawKeyValueRow("Remaining Stock Value", LedgerFormatter.formatInrPdf(calc.closingStockValue))
            drawKeyValueRow("Total Business Value", LedgerFormatter.formatInrPdf(calc.totalBusinessValue))
            drawKeyValueRow("Less: Total Purchase Cost", LedgerFormatter.formatInrPdf(calc.totalPurchaseAmount))
            drawKeyValueRow("Gross Income", LedgerFormatter.formatInrPdf(calc.grossIncome), highlight = true)
            drawKeyValueRow("Less: Total Daily Expenses", LedgerFormatter.formatInrPdf(calc.totalExpenses))

            ensureSpace(38f)
            y += 6f
            val finalIsProfit = calc.isProfit
            fillPaint.color = if (finalIsProfit) brandGreen else lossRed
            canvas.drawRect(MARGIN_X, y - 16f, PAGE_WIDTH - MARGIN_X, y + 12f, fillPaint)
            val finalLabelPaint = Paint().apply {
                color = Color.WHITE
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val finalValuePaint = Paint(finalLabelPaint).apply {
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            val finalTitle = if (finalIsProfit) "FINAL DAILY PROFIT" else "FINAL DAILY LOSS"
            canvas.drawText(finalTitle, MARGIN_X + 10f, y + 1f, finalLabelPaint)
            canvas.drawText(LedgerFormatter.formatInrPdf(calc.finalDailyProfit), PAGE_WIDTH - MARGIN_X - 10f, y + 1f, finalValuePaint)
            y += 28f

            canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
            y += 14f
            canvas.drawText("Generated by UNIQUE CHICKEN Business Manager", MARGIN_X, y, subtitlePaint)

            pdfDocument.finishPage(page)

            // Save to cacheDir/reports/ for FileProvider sharing
            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val fileName = "UniqueChicken_${dateId}.pdf"
            val cacheFile = File(reportsDir, fileName)
            FileOutputStream(cacheFile).use { out ->
                pdfDocument.writeTo(out)
            }

            // Also save to public Downloads on Android 10+ (zero permissions needed)
            val savedToDownloads = saveCopyToDownloads(context, cacheFile, fileName)
            pdfDocument.close()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile,
            )

            GeneratedPdfResult(
                shareUri = uri,
                fileName = fileName,
                savedToDownloads = savedToDownloads,
            )
        }
    }

    private fun saveCopyToDownloads(context: Context, sourceFile: File, fileName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/UniqueChicken")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(itemUri)?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            true
        }.getOrDefault(false)
    }

    fun launchShareIntent(context: Context, uri: Uri, dateId: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "UNIQUE CHICKEN - Daily Report (${LedgerFormatter.formatShortDate(dateId)})")
            putExtra(Intent.EXTRA_TEXT, "UNIQUE CHICKEN Daily Business Account Report for ${LedgerFormatter.formatDisplayDate(dateId)}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Daily PDF Report").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
