package com.example.mymusicplayer.data.remote;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface JamendoApi {

    @GET("tracks")
    Call<JamendoResponse> getWaveTracks(
            @Query("client_id") String clientId,
            @Query("format") String format,
            @Query("order") String order,
            @Query("limit") int limit,
            @Query("offset") int offset,
            @Query("audioformat") String audioFormat
    );
}
