package ro.ainpc.api;

import ro.ainpc.addons.AddonRegistry;
import ro.ainpc.platform.RuntimeMode;
import ro.ainpc.world.StoryMode;
import ro.ainpc.world.WorldAdminService;
import ro.ainpc.world.WorldMode;

public interface AINPCPlatformApi {

    RuntimeMode getRuntimeMode();

    WorldMode getWorldMode();

    StoryMode getDefaultStoryMode();

    AddonRegistry getAddonRegistry();

    WorldAdminService getWorldAdminService();
}
