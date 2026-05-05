package com.sroadtutor.di

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.sroadtutor.BuildConfig
import com.sroadtutor.data.local.SessionManager
import com.sroadtutor.data.remote.ApiService
import com.sroadtutor.data.remote.AuthInterceptor
import com.sroadtutor.data.remote.TokenAuthenticator
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    // Wired via build.gradle.kts buildConfigField; override per build type.
    // Debug -> http://10.0.0.228:8080/  Release -> https://api.sroadtutor.com/
    private val BASE_URL: String = BuildConfig.API_BASE_URL

    @Volatile
    private var apiServiceInstance: ApiService? = null

    private fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private fun provideOkHttpClient(context: Context, apiService: Lazy<ApiService>): OkHttpClient {
        val sessionManager = SessionManager(context)
        val authInterceptor = AuthInterceptor(sessionManager)
        val authenticator = TokenAuthenticator(sessionManager, apiService)

        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(authenticator)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        // Body-level logging only when explicitly enabled (debug builds).
        if (BuildConfig.ENABLE_HTTP_LOGGING) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    fun provideApiService(context: Context): ApiService {
        // Singleton so OkHttp's connection pool / authenticator state is shared.
        apiServiceInstance?.let { return it }
        return synchronized(this) {
            apiServiceInstance ?: run {
                lateinit var apiService: ApiService
                val lazyApiService = lazy { apiService }

                apiService = Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(provideOkHttpClient(context.applicationContext, lazyApiService))
                    .addConverterFactory(MoshiConverterFactory.create(provideMoshi()))
                    .build()
                    .create(ApiService::class.java)

                apiServiceInstance = apiService
                apiService
            }
        }
    }
}
