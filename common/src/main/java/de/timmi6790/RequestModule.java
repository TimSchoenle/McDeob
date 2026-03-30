package de.timmi6790;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class RequestModule {
    public OkHttpClient getHttpClient() {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    final Request originalRequest = chain.request();
                    final Request requestWithUserAgent = originalRequest
                            .newBuilder()
                            .header("User-Agent", "McDeob")
                            .build();

                    return chain.proceed(requestWithUserAgent);
                })
                .build();
    }
}
