package one.mixin.android.api.service

import io.reactivex.Observable
import one.mixin.android.api.MixinResponse
import one.mixin.android.api.request.ConversationRequest
import one.mixin.android.api.request.ParticipantRequest
import one.mixin.android.api.response.AttachmentResponse
import one.mixin.android.api.response.ConversationResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ConversationService {

    @POST("conversations")
    fun create(@Body request: ConversationRequest): Call<MixinResponse<ConversationResponse>>

    @GET("conversations/{id}")
    fun getConversation(@Path("id") id: String): Call<MixinResponse<ConversationResponse>>

    @GET("conversations/{id}")
    fun findConversation(@Path("id") id: String): Observable<MixinResponse<ConversationResponse>>

    @POST("attachments")
    fun requestAttachment(): Observable<MixinResponse<AttachmentResponse>>

    @GET("attachments/{id}")
    fun getAttachment(@Path("id") id: String): Call<MixinResponse<AttachmentResponse>>

    @POST("conversations/{id}/participants/{action}")
    fun participants(
        @Path("id") id: String,
        @Path("action") action: String,
        @Body requests: List<ParticipantRequest>
    ): Call<MixinResponse<ConversationResponse>>

    @POST("conversations/{id}")
    fun update(@Path("id") id: String, @Body request: ConversationRequest):
        Call<MixinResponse<ConversationResponse>>

    @POST("conversations/{id}/exit")
    fun exit(@Path("id") id: String): Call<MixinResponse<ConversationResponse>>

    @POST("conversations/{code_id}/join")
    fun join(@Path("code_id") codeId: String): Observable<MixinResponse<ConversationResponse>>

    @POST("conversations/{id}/rotate")
    fun rotate(@Path("id") id: String): Observable<MixinResponse<ConversationResponse>>

    @POST("conversations/{id}/mute")
    fun mute(@Path("id") id: String, @Body request: ConversationRequest): Call<MixinResponse<ConversationResponse>>
}
