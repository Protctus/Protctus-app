package com.protctus.app.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.protctus.app.network.BreachDetail
import com.protctus.app.network.DarkWebScanResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportGenerator {

    private const val PAGE_WIDTH = 595  // A4 in points
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = (595 - 80).toFloat()

    // Protctus Brand Colors (matching template.html)
    private val BG_COLOR = android.graphics.Color.parseColor("#1C1919")
    private val SURFACE_COLOR = android.graphics.Color.parseColor("#2A2B31")
    private val PRIMARY_TEXT = android.graphics.Color.parseColor("#E3E3E3")
    private val SECONDARY_TEXT = android.graphics.Color.parseColor("#7F909F")
    private val ACCENT_COLOR = android.graphics.Color.parseColor("#4A5568")
    private val RED_COLOR = android.graphics.Color.parseColor("#EF4444")
    private val GREEN_COLOR = android.graphics.Color.parseColor("#10B981")
    private val AMBER_COLOR = android.graphics.Color.parseColor("#F59E0B")

    fun generateReport(context: Context, result: DarkWebScanResult, username: String): Uri? {
        val doc = PdfDocument()
        var pageNumber = 1
        var currentPage = newPage(doc, pageNumber++)
        var canvas = currentPage.canvas
        var y = MARGIN

        y = drawBackground(canvas, y)
        y = drawHeader(canvas, context, y)
        y = drawDivider(canvas, y)

        y = drawSectionTitle(canvas, "SCAN SUMMARY", y)
        y = drawSummaryRow(canvas, "Scanned Email", result.scannedEmail.ifEmpty { "N/A" }, y)
        y = drawSummaryRow(canvas, "Scan Date", SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(result.scanTimestamp)), y)
        y = drawSummaryRow(canvas, "Breach Status", if (result.isSafe) "No Leaks Found" else "${result.breachCount} Breach(es) Detected", y, if (result.isSafe) GREEN_COLOR else RED_COLOR)
        y = drawSummaryRow(canvas, "Risk Level", if (result.isSafe) "LOW" else if (result.breachCount > 3) "CRITICAL" else "HIGH", y, if (result.isSafe) GREEN_COLOR else RED_COLOR)
        y += 16f

        y = drawDivider(canvas, y)

        if (result.breaches.isNotEmpty()) {
            y = drawSectionTitle(canvas, "DETAILED BREACH FINDINGS", y)
            y += 8f

            for (breach in result.breaches) {
                // Check if we need a new page (leave 160pt buffer for card)
                if (y > PAGE_HEIGHT - 180) {
                    doc.finishPage(currentPage)
                    currentPage = newPage(doc, pageNumber++)
                    canvas = currentPage.canvas
                    drawBackground(canvas, 0f)
                    y = MARGIN + 20f
                }
                y = drawBreachCard(canvas, breach, y)
                y += 12f
            }
        } else if (result.isSafe) {
            y = drawSectionTitle(canvas, "NO BREACHES DETECTED", y)
            y = drawInfoText(canvas, "Your email address was not found in any known data breach databases. Continue practicing good cybersecurity hygiene.", y)
        }

        y += 20f
        if (y > PAGE_HEIGHT - 120) {
            doc.finishPage(currentPage)
            currentPage = newPage(doc, pageNumber++)
            canvas = currentPage.canvas
            drawBackground(canvas, 0f)
            y = MARGIN + 20f
        }

        y = drawDivider(canvas, y)
        y = drawSectionTitle(canvas, "SECURITY RECOMMENDATIONS", y)
        val recs = if (result.isSafe) {
            listOf("Enable 2FA on all accounts", "Use a password manager", "Run weekly Dark Web scans")
        } else {
            listOf(
                "Change passwords for breached services immediately",
                "Enable Two-Factor Authentication on all accounts",
                "Monitor your email for unusual sign-in activity",
                "Use a unique strong password for every service",
                "Consider freezing your credit if financial data was leaked"
            )
        }
        for (rec in recs) {
            y = drawInfoText(canvas, rec, y)
        }

        y += 20f
        drawFooter(canvas, username)
        doc.finishPage(currentPage)

        // Write to file
        return try {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Protctus")
            dir.mkdirs()
            val file = File(dir, "protctus_darkweb_report_${System.currentTimeMillis()}.pdf")
            doc.writeTo(FileOutputStream(file))
            doc.close()
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            doc.close()
            null
        }
    }

    private fun newPage(doc: PdfDocument, number: Int): PdfDocument.Page {
        val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create()
        return doc.startPage(info)
    }

    private fun drawBackground(canvas: Canvas, y: Float): Float {
        val paint = Paint().apply { color = BG_COLOR; style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paint)
        return y
    }

    private fun drawHeader(canvas: Canvas, context: Context, y: Float): Float {
        val logoPaint = Paint().apply {
            color = PRIMARY_TEXT; textSize = 26f; isFakeBoldText = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText("PROTCTUS", MARGIN, y + 30f, logoPaint)

        val tagPaint = Paint().apply { color = SECONDARY_TEXT; textSize = 10f }
        canvas.drawText("DARK WEB INTELLIGENCE REPORT", MARGIN, y + 48f, tagPaint)

        val borderPaint = Paint().apply {
            color = ACCENT_COLOR; strokeWidth = 1f; style = Paint.Style.STROKE
        }
        canvas.drawLine(MARGIN, y + 58f, (PAGE_WIDTH - MARGIN), y + 58f, borderPaint)
        return y + 70f
    }

    private fun drawDivider(canvas: Canvas, y: Float): Float {
        val paint = Paint().apply { color = ACCENT_COLOR; strokeWidth = 0.5f }
        canvas.drawLine(MARGIN, y + 8f, (PAGE_WIDTH - MARGIN), y + 8f, paint)
        return y + 20f
    }

    private fun drawSectionTitle(canvas: Canvas, title: String, y: Float): Float {
        val paint = Paint().apply {
            color = PRIMARY_TEXT; textSize = 13f; isFakeBoldText = true
        }
        canvas.drawText(title, MARGIN, y + 16f, paint)
        return y + 28f
    }

    private fun drawSummaryRow(canvas: Canvas, label: String, value: String, y: Float, valueColor: Int = PRIMARY_TEXT): Float {
        val labelPaint = Paint().apply { color = SECONDARY_TEXT; textSize = 10f }
        val valuePaint = Paint().apply { color = valueColor; textSize = 10f; isFakeBoldText = true }

        val cardPaint = Paint().apply { color = SURFACE_COLOR; style = Paint.Style.FILL }
        canvas.drawRoundRect(
            android.graphics.RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 28f),
            6f, 6f, cardPaint
        )

        canvas.drawText(label, MARGIN + 10f, y + 18f, labelPaint)
        val valueX = PAGE_WIDTH - MARGIN - valuePaint.measureText(value) - 10f
        canvas.drawText(value, valueX, y + 18f, valuePaint)
        return y + 34f
    }

    private fun drawBreachCard(canvas: Canvas, breach: BreachDetail, y: Float): Float {
        val cardH = 140f
        val riskColor = if (breach.isSensitive) RED_COLOR else AMBER_COLOR

        // Card background
        val cardPaint = Paint().apply { color = SURFACE_COLOR; style = Paint.Style.FILL }
        canvas.drawRoundRect(android.graphics.RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + cardH), 8f, 8f, cardPaint)

        // Left accent stripe (colored by sensitivity)
        val stripePaint = Paint().apply { color = riskColor; style = Paint.Style.FILL }
        canvas.drawRoundRect(android.graphics.RectF(MARGIN, y, MARGIN + 4f, y + cardH), 2f, 2f, stripePaint)

        var cy = y + 16f
        val titlePaint = Paint().apply { color = PRIMARY_TEXT; textSize = 12f; isFakeBoldText = true }
        canvas.drawText(breach.name, MARGIN + 14f, cy, titlePaint)

        // Badge
        val badge = if (breach.isSensitive) "SENSITIVE" else "BREACHED"
        val badgePaint = Paint().apply { color = riskColor; textSize = 8f }
        val bw = badgePaint.measureText(badge) + 12f
        val badgeBoxPaint = Paint().apply { color = riskColor; alpha = 40; style = Paint.Style.FILL }
        canvas.drawRoundRect(android.graphics.RectF(PAGE_WIDTH - MARGIN - bw - 4f, cy - 12f, PAGE_WIDTH - MARGIN - 4f, cy + 4f), 4f, 4f, badgeBoxPaint)
        badgePaint.alpha = 255
        canvas.drawText(badge, PAGE_WIDTH - MARGIN - bw + 4f, cy, badgePaint)

        cy += 18f
        val subPaint = Paint().apply { color = SECONDARY_TEXT; textSize = 9f }

        if (breach.domain.isNotEmpty()) {
            canvas.drawText("Domain: ${breach.domain}", MARGIN + 14f, cy, subPaint)
            cy += 14f
        }
        if (breach.breachDate.isNotEmpty()) {
            canvas.drawText("Breach Date: ${breach.breachDate}", MARGIN + 14f, cy, subPaint)
            cy += 14f
        }
        if (breach.pwnCount > 0) {
            canvas.drawText("Affected Users: ${"%,d".format(breach.pwnCount)}", MARGIN + 14f, cy, subPaint)
            cy += 14f
        }
        if (breach.dataClasses.isNotEmpty()) {
            val dcText = "Exposed Data: ${breach.dataClasses.take(4).joinToString(", ")}"
            canvas.drawText(dcText.take(70), MARGIN + 14f, cy, subPaint)
            cy += 14f
        }
        if (breach.description.isNotEmpty()) {
            val descPaint = Paint().apply { color = SECONDARY_TEXT; textSize = 8f; isAntiAlias = true }
            canvas.drawText(breach.description.take(80) + if (breach.description.length > 80) "…" else "", MARGIN + 14f, cy, descPaint)
        }

        return y + cardH
    }

    private fun drawInfoText(canvas: Canvas, text: String, y: Float): Float {
        val paint = Paint().apply { color = SECONDARY_TEXT; textSize = 9f; isAntiAlias = true }
        canvas.drawText("  $text", MARGIN + 10f, y + 14f, paint)
        return y + 20f
    }

    private fun drawFooter(canvas: Canvas, username: String) {
        val paint = Paint().apply { color = ACCENT_COLOR; textSize = 8f }
        val footerText = "Generated by Protctus for $username · © 2026 Protctus · All rights reserved"
        val x = (PAGE_WIDTH - paint.measureText(footerText)) / 2
        canvas.drawText(footerText, x, (PAGE_HEIGHT - 20).toFloat(), paint)

        val borderPaint = Paint().apply { color = ACCENT_COLOR; strokeWidth = 0.5f }
        canvas.drawLine(MARGIN, (PAGE_HEIGHT - 32).toFloat(), PAGE_WIDTH - MARGIN, (PAGE_HEIGHT - 32).toFloat(), borderPaint)
    }
}
