package com.yuno.tools.data

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "https://qyapi.ipaybuy.cn/"

    private val dispatcher = Dispatcher().apply {
        maxRequests = 12
        maxRequestsPerHost = 6
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(6, 5, TimeUnit.MINUTES))
        .dispatcher(dispatcher)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "YunoTools/Android VideoParser")
                .header("Accept", "application/json, text/plain, */*")
                .header("Connection", "keep-alive")
                .build()
            chain.proceed(request)
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
}