package com.jossephus.chuchu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jossephus.chuchu.ui.theme.ChuColors

/**
 * Tấm trượt từ đáy (22/9, tab SPEND chi tiết ngày): cửa sổ Dialog phủ cả màn, nền tối 45%,
 * nội dung dán đáy trên nền theme với một hairline ở mép trên. Chạm nền hoặc back là đóng
 * (Dialog tự ăn back). Không nút, không chỉ dẫn — user chốt "bỏ mấy cái chỉ dẫn".
 */
@Composable
fun ChuBottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = ChuColors.current
    // decorFitsSystemWindows = false: cửa sổ Dialog trên Android 15 (targetSdk 36) vốn vẽ tràn dưới vạch
    // điều hướng, nhưng mặc định KHÔNG giao inset cho nội dung → navigationBarsPadding() bằng 0 và dòng
    // cuối chui dưới vạch (ảnh 22/9 19:40 và 20:49). Tắt decorFits thì inset thật về tới Column.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .noRippleClickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClickable {}   // nuốt chạm trong tấm, không rơi xuống nền
                    .background(colors.background)
                    .drawBehind {
                        val stroke = 1.dp.toPx()
                        drawLine(colors.border, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
                    }
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 14.dp)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
        }
    }
}
