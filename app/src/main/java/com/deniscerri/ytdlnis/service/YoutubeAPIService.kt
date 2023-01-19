package com.deniscerri.ytdlnis.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

interface YoutubeAPIService {
    @GET("videos?part=snippet,contentDetails&id={id}&key={apiKey}")
    suspend fun getVideo(
        @Path("id") id: String?
    ) : Response<JsonObject>

    @GET("playlistItems?part=snippet&pageToken={nextPageToken}&maxResults=50&regionCode={regionCode}&playlistId={playlistId}&key={apiKey}")
    suspend fun getPlaylist(
        @Path("nextPageToken") nextPageToken: String,
        @Path("playlistId") playlistId : String,
        @Path("apiKey") apiKey : String
    ) : Response<JsonObject>

    @GET("videos?part=snippet&chart=mostPopular&videoCategoryId=10&regionCode={regionCode}&maxResults=25&key={apiKey}")
    suspend fun getTrending(
        @Path("regionCode") regionCode: String,
        @Path("apiKey") apiKey : String
    ) : Response<JsonArray>

    @GET("videos?part=contentDetails&chart=mostPopular&videoCategoryId=10&regionCode={regionCode}&maxResults=25&key={apiKey}")
    suspend fun getTrendingExtra(
        @Path("regionCode") regionCode: String,
        @Path("apiKey") apiKey : String
    ) : Response<JsonArray>

    @GET("search?part=snippet&q={query}&maxResults=25&regionCode={regionCode}&key={apiKey}")
    suspend fun search(
        @Path("query") query: String,
        @Path("regionCode") regionCode: String,
        @Path("apiKey") apiKey: String
    ) : Response<JsonObject>

    @GET("videos?id={ids}&part=contentDetails&regionCode={regionCode}&key={apiKey}")
    suspend fun getExtraData(
        @Path("ids") ids: String,
        @Path("regionCode") regionCode: String,
        @Path("apiKey") apiKey: String
    ) : Response<JsonObject>

    companion object {
        var invidiousService : YoutubeAPIService? = null
        lateinit var apiKey : String
        lateinit var regionCode: String
        fun getInstance(key: String, regionCode: String) : YoutubeAPIService {
            apiKey = key
            return invidiousService ?: run {
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://www.googleapis.com/youtube/v3/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                invidiousService = retrofit.create(YoutubeAPIService::class.java)
                invidiousService!!
            }
        }
    }
}