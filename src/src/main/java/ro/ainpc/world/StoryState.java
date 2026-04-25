package ro.ainpc.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StoryState {

    private final StoryMode mode;
    private String stateKey;
    private final List<String> storyPool;

    public StoryState(StoryMode mode, String stateKey) {
        this.mode = mode;
        this.stateKey = stateKey;
        this.storyPool = new ArrayList<>();
    }

    public StoryMode getMode() {
        return mode;
    }

    public String getStateKey() {
        return stateKey;
    }

    public void setStateKey(String stateKey) {
        this.stateKey = stateKey;
    }

    public List<String> getStoryPool() {
        return Collections.unmodifiableList(storyPool);
    }

    public void setStoryPool(List<String> storyPool) {
        this.storyPool.clear();
        if (storyPool != null) {
            this.storyPool.addAll(storyPool);
        }
    }
}
