package com.researchradar.core.network

import com.researchradar.core.model.CreateMapRequest
import com.researchradar.core.model.CreateMapResponse
import com.researchradar.core.model.Paper
import com.researchradar.core.model.ResearchMap
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Retrofit service interface for the Ereuna API.
 */
interface RadarApi {

    @POST("v1/auth/signup")
    suspend fun signUp(@Body request: com.researchradar.core.model.SignUpRequest): com.researchradar.core.model.AuthResponse

    @POST("v1/auth/signin")
    suspend fun signIn(@Body request: com.researchradar.core.model.SignInRequest): com.researchradar.core.model.AuthResponse

    @GET("v1/auth/me")
    suspend fun me(): com.researchradar.core.model.AccountUser

    /** Permanently deletes the signed-in account (the password is asked again). */
    @POST("v1/auth/delete")
    suspend fun deleteAccount(@Body request: com.researchradar.core.model.DeleteAccountRequest): retrofit2.Response<Unit>

    @POST("v1/maps")
    suspend fun createMap(@Body request: CreateMapRequest): CreateMapResponse

    /** Builds a private map from the user's PDFs (form fields `topic`, `include_search`, parts `files`). */
    @Multipart
    @POST("v1/maps/upload")
    suspend fun createMapFromUploads(
        @Part("topic") topic: RequestBody,
        @Part("include_search") includeSearch: RequestBody,
        @Part files: List<MultipartBody.Part>,
    ): CreateMapResponse

    @GET("v1/maps/{mapId}")
    suspend fun getMap(@Path("mapId") mapId: String): ResearchMap

    @GET("v1/papers/{paperId}")
    suspend fun getPaper(@Path("paperId") paperId: String): Paper

    @GET("v1/maps/{mapId}/graph")
    suspend fun getMapGraph(@Path("mapId") mapId: String): com.researchradar.core.model.GraphData

    @POST("v1/maps/{mapId}/export")
    suspend fun exportMap(
        @Path("mapId") mapId: String,
        @Body request: com.researchradar.core.model.ExportRequest,
    ): com.researchradar.core.model.ExportResponse

    // Library: saved maps and papers belong to the account, so the app and the
    // website show the same library.
    @GET("v1/library/maps")
    suspend fun listSavedMaps(): List<ResearchMap>

    @POST("v1/library/maps/{mapId}")
    suspend fun saveMap(@Path("mapId") mapId: String): retrofit2.Response<Unit>

    @DELETE("v1/library/maps/{mapId}")
    suspend fun removeSavedMap(@Path("mapId") mapId: String): retrofit2.Response<Unit>

    @GET("v1/library/papers")
    suspend fun listSavedPapers(): List<Paper>

    @POST("v1/library/papers/{paperId}")
    suspend fun savePaper(@Path("paperId") paperId: String): retrofit2.Response<Unit>

    @DELETE("v1/library/papers/{paperId}")
    suspend fun removeSavedPaper(@Path("paperId") paperId: String): retrofit2.Response<Unit>
}
