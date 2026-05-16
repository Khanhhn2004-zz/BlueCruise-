package com.vibegravity.bluecruise.di

import android.content.Context
import com.vibegravity.bluecruise.data.auth.AuthPreferencesStore
import com.vibegravity.bluecruise.data.auth.DefaultAuthRepository
import com.vibegravity.bluecruise.data.auth.DefaultUserDataCleaner
import com.vibegravity.bluecruise.data.auth.LoginApiClient
import com.vibegravity.bluecruise.data.dataStore
import com.vibegravity.bluecruise.data.customer.AppScopeCustomerSongSyncScheduler
import com.vibegravity.bluecruise.data.customer.CustomerSongApiClient
import com.vibegravity.bluecruise.data.customer.CustomerSongDownloader
import com.vibegravity.bluecruise.data.customer.CustomerSongFileStore
import com.vibegravity.bluecruise.data.customer.DefaultCustomerSongFileStore
import com.vibegravity.bluecruise.data.customer.DefaultCustomerSongSyncRepository
import com.vibegravity.bluecruise.domain.repository.AuthRepository
import com.vibegravity.bluecruise.domain.repository.AuthSessionRepository
import com.vibegravity.bluecruise.domain.repository.CustomerSongSyncRepository
import com.vibegravity.bluecruise.domain.repository.CustomerSongSyncScheduler
import com.vibegravity.bluecruise.domain.repository.UserDataCleaner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindingsModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        repository: DefaultAuthRepository
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindCustomerSongSyncRepository(
        repository: DefaultCustomerSongSyncRepository
    ): CustomerSongSyncRepository

    @Binds
    @Singleton
    abstract fun bindCustomerSongSyncScheduler(
        scheduler: AppScopeCustomerSongSyncScheduler
    ): CustomerSongSyncScheduler

    @Binds
    @Singleton
    abstract fun bindUserDataCleaner(
        cleaner: DefaultUserDataCleaner
    ): UserDataCleaner
}

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideAuthSessionRepository(
        @ApplicationContext context: Context
    ): AuthSessionRepository {
        return AuthPreferencesStore(context.dataStore)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json { ignoreUnknownKeys = true }
    }

    @Provides
    @Named(API_BASE_URL)
    fun provideApiBaseUrl(): String {
        return "http://103.118.28.117/api"
    }

    @Provides
    @Singleton
    fun provideLoginApiClient(
        okHttpClient: OkHttpClient,
        json: Json,
        @Named(API_BASE_URL) baseUrl: String
    ): LoginApiClient {
        return LoginApiClient(
            okHttpClient = okHttpClient,
            json = json,
            baseUrl = baseUrl
        )
    }

    @Provides
    @Singleton
    fun provideCustomerSongDownloader(
        okHttpClient: OkHttpClient,
        json: Json,
        @Named(API_BASE_URL) baseUrl: String
    ): CustomerSongDownloader {
        return CustomerSongApiClient(
            okHttpClient = okHttpClient,
            json = json,
            baseUrl = baseUrl
        )
    }

    @Provides
    @Singleton
    fun provideCustomerSongFileStore(
        @ApplicationContext context: Context
    ): CustomerSongFileStore {
        return DefaultCustomerSongFileStore(context.filesDir)
    }

    private const val API_BASE_URL = "api_base_url"
}
