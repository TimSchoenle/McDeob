package de.timmi6790.launchermeta;

import de.timmi6790.RequestModule;
import okhttp3.OkHttpClient;

public class LauncherMetaModule {
    private final RequestModule requestModule = new RequestModule();

    public LauncherMeta getLauncherMeta(final OkHttpClient httpClient) {
        return new LauncherMeta(httpClient);
    }

    public LauncherMeta getLauncherMeta() {
        return this.getLauncherMeta(this.requestModule.getHttpClient());
    }
}
