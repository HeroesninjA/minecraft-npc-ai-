package ro.ainpc.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WorldRegion {

    private final String id;
    private final String name;
    private final String worldName;
    private final RegionType type;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final List<String> tags;
    private StoryState storyState;

    public WorldRegion(String id,
                       String name,
                       String worldName,
                       RegionType type,
                       int minX,
                       int minY,
                       int minZ,
                       int maxX,
                       int maxY,
                       int maxZ) {
        this.id = id;
        this.name = name;
        this.worldName = worldName;
        this.type = type;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
        this.tags = new ArrayList<>();
        this.storyState = new StoryState(StoryMode.EVOLUTIVE, "default");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public RegionType getType() {
        return type;
    }

    public StoryState getStoryState() {
        return storyState;
    }

    public void setStoryState(StoryState storyState) {
        this.storyState = storyState;
    }

    public List<String> getTags() {
        return Collections.unmodifiableList(tags);
    }

    public void setTags(List<String> tags) {
        this.tags.clear();
        if (tags != null) {
            this.tags.addAll(tags);
        }
    }

    public boolean contains(String worldName, int x, int y, int z) {
        return this.worldName.equalsIgnoreCase(worldName)
            && x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }
}
