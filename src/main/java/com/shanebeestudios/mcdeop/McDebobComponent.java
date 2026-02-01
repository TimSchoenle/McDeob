package com.shanebeestudios.mcdeop;

import dagger.Component;
import de.timmi6790.launchermeta.LauncherMetaModule;
import javax.inject.Singleton;

@Singleton
@Component(modules = LauncherMetaModule.class)
public interface McDebobComponent {
    VersionManager getVersionManager();
}
