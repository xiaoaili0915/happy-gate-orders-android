package nl.happygate.orders.data

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface RestaurantApi {
    @GET("api/restaurant/ping")
    suspend fun ping(): PingResponse

    @GET("api/restaurant/orders")
    suspend fun orders(
        @Query("scope") scope: String,
        @Query("date") date: String? = null,
        @Query("q") q: String? = null,
    ): List<Order>

    @GET("api/restaurant/orders/{id}")
    suspend fun order(@Path("id") id: Int): Order

    @POST("api/restaurant/orders/{id}/accept")
    suspend fun accept(@Path("id") id: Int, @Body body: AcceptBody): Order

    @POST("api/restaurant/orders/{id}/reject")
    suspend fun reject(@Path("id") id: Int, @Body body: RejectBody): Order

    @POST("api/restaurant/orders/{id}/ready")
    suspend fun ready(@Path("id") id: Int): Order

    @POST("api/restaurant/orders/{id}/complete")
    suspend fun complete(@Path("id") id: Int): Order

    @POST("api/restaurant/orders/{id}/print-events")
    suspend fun printEvent(@Path("id") id: Int, @Body body: PrintEventBody): Order

    @POST("api/restaurant/devices/register")
    suspend fun registerDevice(@Body body: DeviceBody): Response<Unit>
}

val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

/** Normalises what staff type into something Retrofit accepts ("https://host/"). */
fun normaliseBaseUrl(raw: String): String {
    var url = raw.trim()
    if (url.isEmpty()) return url
    if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
    if (!url.endsWith("/")) url += "/"
    return url
}

fun buildApi(baseUrl: String, token: String, debugLogging: Boolean = false): RestaurantApi {
    val auth = Interceptor { chain ->
        chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $token").build())
    }
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(auth)
        .apply {
            if (debugLogging) addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        .build()
    return Retrofit.Builder()
        .baseUrl(normaliseBaseUrl(baseUrl))
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(RestaurantApi::class.java)
}

/** Human-readable message for a failed call, using the backend's `detail` when present. */
fun Throwable.userMessage(): String = when (this) {
    is HttpException -> {
        val detail = try {
            response()?.errorBody()?.string()?.let { json.decodeFromString(ApiError.serializer(), it).detail }
        } catch (e: Exception) {
            null
        }
        when {
            !detail.isNullOrBlank() -> detail
            code() == 401 -> "Invalid token"
            else -> "Server error ${code()}"
        }
    }
    is java.net.UnknownHostException -> "Server not found"
    is java.net.ConnectException -> "Cannot connect to server"
    is java.net.SocketTimeoutException -> "Connection timed out"
    else -> message ?: javaClass.simpleName
}
