package com.vibegravity.bluecruise.di

import com.vibegravity.bluecruise.data.AndroidBluetoothManagerWrapper
import com.vibegravity.bluecruise.domain.IBluetoothManagerWrapper
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindBluetoothManagerWrapper(
        androidBluetoothManagerWrapper: AndroidBluetoothManagerWrapper
    ): IBluetoothManagerWrapper
}
