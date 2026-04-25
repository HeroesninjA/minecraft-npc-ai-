package ro.ainpc.platform;

import org.bukkit.configuration.file.FileConfiguration;
import ro.ainpc.world.StoryMode;
import ro.ainpc.world.WorldMode;

public class PlatformProfile {

    private final RuntimeMode runtimeMode;
    private final WorldMode worldMode;
    private final StoryMode defaultStoryMode;

    public PlatformProfile(RuntimeMode runtimeMode, WorldMode worldMode, StoryMode defaultStoryMode) {
        this.runtimeMode = runtimeMode;
        this.worldMode = worldMode;
        this.defaultStoryMode = defaultStoryMode;
    }

    public RuntimeMode getRuntimeMode() {
        return runtimeMode;
    }

    public WorldMode getWorldMode() {
        return worldMode;
    }

    public StoryMode getDefaultStoryMode() {
        return defaultStoryMode;
    }

    public static PlatformProfile fromConfig(FileConfiguration config) {
        RuntimeMode runtimeMode = RuntimeMode.fromId(config.getString("platform.runtime_mode", "standalone"));
        WorldMode worldMode = WorldMode.fromId(config.getString("platform.world_mode", "finite_dynamic"));
        StoryMode storyMode = StoryMode.fromId(config.getString("platform.default_story_mode", "evolutive"));
        return new PlatformProfile(runtimeMode, worldMode, storyMode);
    }
}
