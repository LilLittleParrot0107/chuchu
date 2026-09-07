package com.jossephus.chuchu.service

/**
 * App có đang ở foreground không — cờ cấp TIẾN TRÌNH, nav gán theo ON_START/ON_STOP.
 * Tiến trình mới sinh (receiver đánh thức sau khi bị giết) mặc định false = đúng là
 * đang ở nền. Dùng để alarm nền nổ trùng lúc user vừa mở lại app không tắt VPN ngay
 * trước mặt họ (review 7/9).
 */
object AppForeground {
    @Volatile var inForeground: Boolean = false
}
