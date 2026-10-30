package uz.uchet

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Создаем базу данных как Singleton
    single { AppDb.get(androidContext()) }
    
    // Достаем DAO
    single { get<AppDb>().dao() }
    
    // Создаем VM через Koin
    viewModel { VM(androidContext() as Application, get(), get()) }
}
