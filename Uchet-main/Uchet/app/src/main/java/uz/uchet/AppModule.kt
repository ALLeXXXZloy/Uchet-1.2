package uz.uchet

import android.app.Application
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Создаем базу данных как Singleton
    single { AppDb.get(androidContext()) }
    
    // Достаем DAO
    single { get<AppDb>().dao() }
    
    // Используем androidApplication() для безопасной передачи Application в VM
    viewModel { VM(androidApplication(), get(), get()) }
}
