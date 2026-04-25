package ro.ainpc.world;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.platform.PlatformProfile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorldAdminService {

    private final AINPCPlugin plugin;
    private final Map<String, WorldRegion> regionsById;
    private final Map<String, List<WorldNode>> nodesByRegion;
    private boolean enabled;
    private WorldMode worldMode;

    public WorldAdminService(AINPCPlugin plugin) {
        this.plugin = plugin;
        this.regionsById = new LinkedHashMap<>();
        this.nodesByRegion = new LinkedHashMap<>();
        this.enabled = true;
        this.worldMode = WorldMode.FINITE_DYNAMIC;
    }

    public void reloadFromConfig(FileConfiguration config, PlatformProfile profile) {
        regionsById.clear();
        nodesByRegion.clear();

        this.enabled = config.getBoolean("world_admin.enabled", true);
        this.worldMode = profile.getWorldMode();
        if (!enabled) {
            plugin.debug("World admin este dezactivat din configuratie.");
            return;
        }

        ConfigurationSection regionsSection = config.getConfigurationSection("world_admin.regions");
        if (regionsSection == null) {
            plugin.debug("World admin nu are regiuni configurate.");
            return;
        }

        for (String regionId : regionsSection.getKeys(false)) {
            ConfigurationSection regionSection = regionsSection.getConfigurationSection(regionId);
            if (regionSection == null) {
                continue;
            }

            WorldRegion region = new WorldRegion(
                regionId,
                regionSection.getString("name", regionId),
                regionSection.getString("world", "world"),
                RegionType.fromId(regionSection.getString("type", "custom")),
                getInt(regionSection, "min.x", 0),
                getInt(regionSection, "min.y", 0),
                getInt(regionSection, "min.z", 0),
                getInt(regionSection, "max.x", 0),
                getInt(regionSection, "max.y", 255),
                getInt(regionSection, "max.z", 0)
            );
            region.setTags(regionSection.getStringList("tags"));
            region.setStoryState(loadStoryState(regionSection, profile));

            registerRegion(region);
            loadNodes(region, regionSection);
        }

        plugin.getLogger().info("World admin incarcat: " + regionsById.size() + " regiuni, " + getNodeCount() + " noduri.");
    }

    private StoryState loadStoryState(ConfigurationSection regionSection, PlatformProfile profile) {
        StoryMode defaultMode = profile.getDefaultStoryMode();
        ConfigurationSection storySection = regionSection.getConfigurationSection("story");
        if (storySection == null) {
            return new StoryState(defaultMode, "default");
        }

        StoryState storyState = new StoryState(
            StoryMode.fromId(storySection.getString("mode", defaultMode.getId())),
            storySection.getString("state", "default")
        );
        storyState.setStoryPool(storySection.getStringList("pool"));
        return storyState;
    }

    private void loadNodes(WorldRegion region, ConfigurationSection regionSection) {
        ConfigurationSection nodesSection = regionSection.getConfigurationSection("nodes");
        if (nodesSection == null) {
            return;
        }

        for (String nodeId : nodesSection.getKeys(false)) {
            ConfigurationSection nodeSection = nodesSection.getConfigurationSection(nodeId);
            if (nodeSection == null) {
                continue;
            }

            WorldNode node = new WorldNode(
                nodeId,
                region.getId(),
                WorldNodeType.fromId(nodeSection.getString("type", "custom")),
                nodeSection.getString("world", region.getWorldName()),
                nodeSection.getDouble("x", 0),
                nodeSection.getDouble("y", 0),
                nodeSection.getDouble("z", 0),
                nodeSection.getDouble("radius", 2.5)
            );

            ConfigurationSection metadataSection = nodeSection.getConfigurationSection("metadata");
            if (metadataSection != null) {
                for (String key : metadataSection.getKeys(false)) {
                    node.putMetadata(key, metadataSection.getString(key, ""));
                }
            }

            registerNode(node);
        }
    }

    private int getInt(ConfigurationSection section, String path, int fallback) {
        return section.isInt(path) ? section.getInt(path) : fallback;
    }

    public void registerRegion(WorldRegion region) {
        regionsById.put(region.getId(), region);
    }

    public void registerNode(WorldNode node) {
        nodesByRegion.computeIfAbsent(node.getRegionId(), ignored -> new ArrayList<>()).add(node);
    }

    public Collection<WorldRegion> getRegions() {
        return Collections.unmodifiableCollection(regionsById.values());
    }

    public List<WorldNode> getNodes(String regionId) {
        return Collections.unmodifiableList(nodesByRegion.getOrDefault(regionId, Collections.emptyList()));
    }

    public WorldRegion findRegion(String worldName, int x, int y, int z) {
        return regionsById.values().stream()
            .filter(region -> region.contains(worldName, x, y, z))
            .findFirst()
            .orElse(null);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public WorldMode getWorldMode() {
        return worldMode;
    }

    public int getNodeCount() {
        return nodesByRegion.values().stream().mapToInt(List::size).sum();
    }
}
