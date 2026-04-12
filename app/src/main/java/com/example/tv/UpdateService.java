package com.example.tv;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Url;

public interface UpdateService {
    @GET
    Call<UpdateResponse> checkUpdate(@Url String url);
}