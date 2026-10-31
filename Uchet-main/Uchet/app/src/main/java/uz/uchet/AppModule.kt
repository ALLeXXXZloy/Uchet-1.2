package uz.uchet

import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Создаем базу данных как Singleton, используя контекст приложения
    single { AppDb.get(androidContext()) }
    
    // Достаем DAO
    single { get<AppDb>().dao() }
    
    // Создаем VM через Koin, безопасно передавая Application
    viewModel { VM(androidApplication(), get(), get()) }
}
