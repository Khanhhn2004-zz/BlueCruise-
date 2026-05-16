package com.vibegravity.bluecruise.di

import android.content.Context
import com.vibegravity.bluecruise.data.BluetoothAdapterRepo
import com.vibegravity.bluecruise.data.PreferencesManager
import com.vibegravity.bluecruise.domain.RoutingExecutor
import com.vibegravity.bluecruise.domain.repository.IBluetoothAdapterRepo
import com.vibegravity.bluecruise.domain.repository.SettingsRepository
import com.vibegravity.bluecruise.domain.SmartAutoRoutingEngine
import com.vibegravity.bluecruise.ui.AndroidMediaControllerProvider
import com.vibegravity.bluecruise.ui.MediaControllerProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModuleBinder {

    @Binds
    @Singleton
    abstract fun bindBluetoothAdapterRepo(
        bluetoothAdapterRepo: BluetoothAdapterRepo
    ): IBluetoothAdapterRepo

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        preferencesManager: PreferencesManager
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindRoutingExecutor(
        engine: SmartAutoRoutingEngine
    ): RoutingExecutor

    @Binds
    @Singleton
    abstract fun bindMediaControllerProvider(
        impl: AndroidMediaControllerProvider
    ): MediaControllerProvider
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationContext(
        @ApplicationContext context: Context
    ): Context {
        return context
    }
}
