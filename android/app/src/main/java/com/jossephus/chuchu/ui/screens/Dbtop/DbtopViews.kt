package com.jossephus.chuchu.ui.screens.Dbtop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.data.model.dbtop.CurvePoint
import com.jossephus.chuchu.data.model.dbtop.DailyYield
import com.jossephus.chuchu.data.model.dbtop.DeFiFormatter
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.KohiSectionBand
import com.jossephus.chuchu.ui.components.chart.DailyYieldBarChart
import com.jossephus.chuchu.ui.components.chart.NetWorthCurveChart
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import java.util.Locale

@Composable
internal fun ChartsView(
    netWorth: Double,
    currentPerDay: Double?,
    curve: List<CurvePoint>,
    daily: List<DailyYield>,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "net_worth_curve") {
            KohiSectionBand("NET WORTH CURVE", DeFiFormatter.formatUsd(netWorth))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceVariant)
                    .padding(10.dp),
            ) {
                if (curve.isEmpty()) {
                    ChuText("NO CURVE DATA", style = type.labelSmall, color = colors.textMuted)
                } else {
                    NetWorthCurveChart(points = curve, lineColor = colors.accent, height = 180.dp)
                }
            }
        }
        item(key = "daily_yield") {
            KohiSectionBand(
                "DAILY YIELD",
                currentPerDay?.let { "+${DeFiFormatter.formatUsd(it)}/D" } ?: "SCAN OFFLINE",
                accent = if (currentPerDay != null) colors.success else colors.error,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceVariant)
                    .padding(10.dp),
            ) {
                if (daily.isEmpty()) {
                    ChuText("NO DAILY YIELD DATA", style = type.bodySmall, color = colors.textMuted)
                } else {
                    DailyYieldBarChart(
                        dailyData = daily,
                        primaryColor = colors.accent,
                        accentColor = colors.success,
                        height = 160.dp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DashboardEmpty(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ChuText(text, style = ChuTypography.current.body, color = ChuColors.current.textMuted)
    }
}

@Composable
internal fun DashboardHint(text: String) {
    ChuText(
        text,
        style = ChuTypography.current.labelSmall,
        color = ChuColors.current.textMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .background(ChuColors.current.background)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    )
}
