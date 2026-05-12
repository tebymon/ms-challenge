package com.tenpo.challenge.proxy.api;

import retrofit2.Call;
import retrofit2.http.GET;

public interface PercentageApi {

    @GET("/percentage")
    Call<PercentageResponse> getPercentage();
}
