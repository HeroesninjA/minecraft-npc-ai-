package ro.ainpc.platform;

import ro.ainpc.AINPCPlugin;
import ro.ainpc.addons.AddonDescriptor;
import ro.ainpc.addons.AddonRegistry;
import ro.ainpc.addons.AddonType;
import ro.ainpc.api.AINPCPlatformApi;
import ro.ainpc.world.StoryMode;
import ro.ainpc.world.WorldAdminService;
import ro.ainpc.world.WorldMode;

import java.util.EnumSet;
import java.util.List;

public class AINPCPlatform implements AINPCPlatformApi {

    private final AINPCPlugin plugin;
    private final AddonRegistry addonRegistry;
    private final WorldAdminService worldAdminService;
    private PlatformProfile profile;

    public AINPCPlatform(AINPCPlugin plugin) {
        this.plugin = plugin;
        this.addonRegistry = new AddonRegistry();
        this.worldAdminService = new WorldAdminService(plugin);
        this.profile = PlatformProfile.fromConfig(plugin.getConfig());
    }

    public void initialize() {
        reloadFromConfig();
        registerCoreDescriptor();
    }

    public void reloadFromConfig() {
        this.profile = PlatformProfile.fromConfig(plugin.getConfig());
        worldAdminService.reloadFromConfig(plugin.getConfig(), profile);
    }

    private void registerCoreDescriptor() {
        addonRegistry.registerDescriptor(new AddonDescriptor(
            AddonDescriptor.ORIGIN_CORE,
            "ainpc-core",
            "AINPC Core",
            plugin.getPluginMeta().getVersion(),
            "Nucleul universal al platformei NPC AI",
            AddonType.CORE,
            false,
            EnumSet.allOf(RuntimeMode.class),
            List.of("ai-engine", "context-system", "dialog-system", "world-admin-api", "addon-api"),
            List.of()
        ));
    }

    @Override
    public RuntimeMode getRuntimeMode() {
        return profile.getRuntimeMode();
    }

    @Override
    public WorldMode getWorldMode() {
        return profile.getWorldMode();
    }

    @Override
    public StoryMode getDefaultStoryMode() {
        return profile.getDefaultStoryMode();
    }

    @Override
    public AddonRegistry getAddonRegistry() {
        return addonRegistry;
    }

    @Override
    public WorldAdminService getWorldAdminService() {
        return worldAdminService;
    }

    public PlatformProfile getProfile() {
        return profile;
    }

    public void shutdown() {
        addonRegistry.shutdown(plugin);
    }
}
