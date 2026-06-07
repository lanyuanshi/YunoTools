package com.yuno.tools.data

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    @GET("api/video")
    suspend fun parseVideo(
        @Query("url") url: String,
        @Query("appId") appId: String = "116786",
        @Query("appKey") appKey: String = "1a4f8drm5o0dix0ss9a1x74cs01ts9cy"
    ): Response<ApiResponse>
}