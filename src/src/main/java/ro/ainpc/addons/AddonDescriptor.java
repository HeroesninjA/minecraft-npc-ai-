package ro.ainpc.addons;

import ro.ainpc.platform.RuntimeMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class AddonDescriptor {

    public static final String ORIGIN_CORE = "core";
    public static final String ORIGIN_FEATURE_PACK = "feature-pack";

    private final String origin;
    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final AddonType type;
    private final boolean primaryScenario;
    private final Set<RuntimeMode> supportedRuntimeModes;
    private final List<String> capabilities;
    private final List<String> dependencies;

    public AddonDescriptor(String origin,
                           String id,
                           String name,
                           String version,
                           String description,
                           AddonType type,
                           boolean primaryScenario,
                           Set<RuntimeMode> supportedRuntimeModes,
                           List<String> capabilities,
                           List<String> dependencies) {
        this.origin = origin;
        this.id = id;
        this.name = name;
        this.version = version;
        this.description = description;
        this.type = type;
        this.primaryScenario = primaryScenario;
        this.supportedRuntimeModes = supportedRuntimeModes == null || supportedRuntimeModes.isEmpty()
            ? EnumSet.allOf(RuntimeMode.class)
            : EnumSet.copyOf(supportedRuntimeModes);
        this.capabilities = Collections.unmodifiableList(new ArrayList<>(capabilities != null ? capabilities : List.of()));
        this.dependencies = Collections.unmodifiableList(new ArrayList<>(dependencies != null ? dependencies : List.of()));
    }

    public String getOrigin() {
        return origin;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public AddonType getType() {
        return type;
    }

    public boolean isPrimaryScenario() {
        return primaryScenario;
    }

    public Set<RuntimeMode> getSupportedRuntimeModes() {
        return Collections.unmodifiableSet(supportedRuntimeModes);
    }

    public List<String> getCapabilities() {
        return capabilities;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public boolean supports(RuntimeMode runtimeMode) {
        return supportedRuntimeModes.contains(runtimeMode);
    }
}
