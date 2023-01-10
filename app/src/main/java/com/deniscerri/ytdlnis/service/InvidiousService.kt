package com.deniscerri.ytdlnis.service

import androidx.room.Room
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

interface InvidiousService {
    @GET("video/{id}")
    suspend fun getVideo(
        @Path("id") id : String
    ) : Call<ResultItem>

    @GET("playlist/{id}")
    suspend fun getPlaylist(
        @Path("id") id: String
    ) : Response<JsonObject>

    @GET("trending?type=music&region={regionCode}")
    suspend fun getTrending(
        @Path("regionCode") regionCode: String
    ) : Response<JsonArray>

    @GET("search/suggestions?q={query}")
    suspend fun getSearchSuggestions(
        @Path("query") query: String
    ) : Response<JsonObject>

    companion object {
        var invidiousService : InvidiousService? = null
        fun getInstance() : InvidiousService {
            return invidiousService ?: run {
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://invidious.baczek.me/api/v1/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                invidiousService = retrofit.create(InvidiousService::class.java)
                invidiousService!!
            }
        }
    }
}