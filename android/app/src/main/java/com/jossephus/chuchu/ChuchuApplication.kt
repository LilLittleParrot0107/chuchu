package com.jossephus.chuchu

import android.app.Application
import com.jossephus.chuchu.data.repository.SettingsRepository
import com.jossephus.chuchu.ui.theme.GhosttyThemeRegistry
import kotlin.concurrent.thread

class ChuchuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Lam am cac singleton doc dia TRUOC khi UI can den: constructor cua
        // SettingsRepository doc ~20 khoa SharedPreferences + parse JSON
        // (layout accessory, custom keys, shortcuts), GhosttyThemeRegistry
        // liet ke assets — de nguyen thi tat ca do vao main thread ngay frame
        // dau tien. Warm o day thi luc MainActivity/AppRoot goi getInstance
        // chi con tra ve cache; neu Activity chay truoc khi warm xong thi
        // getInstance van synchronized nhu cu — khong te hon hien trang.
        thread(name = "chuchu-warmup") {
            GhosttyThemeRegistry.init(this)
            SettingsRepository.getInstance(this)
        }
    }
}
