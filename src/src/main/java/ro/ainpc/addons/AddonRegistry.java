package ro.ainpc.addons;

import ro.ainpc.AINPCPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AddonRegistry {

    private final Map<String, AddonDescriptor> descriptorsById;
    private final Map<String, AINPCAddon> addonsById;
    private final Map<AddonType, List<AddonDescriptor>> descriptorsByType;

    public AddonRegistry() {
        this.descriptorsById = new LinkedHashMap<>();
        this.addonsById = new LinkedHashMap<>();
        this.descriptorsByType = new EnumMap<>(AddonType.class);
    }

    public synchronized void registerDescriptor(AddonDescriptor descriptor) {
        AddonDescriptor previous = descriptorsById.put(descriptor.getId(), descriptor);
        if (previous != null) {
            List<AddonDescriptor> previousList = descriptorsByType.get(previous.getType());
            if (previousList != null) {
                previousList.removeIf(existing -> existing.getId().equals(previous.getId()));
            }
        }

        descriptorsByType
            .computeIfAbsent(descriptor.getType(), ignored -> new ArrayList<>())
            .add(descriptor);
        descriptorsByType.get(descriptor.getType()).sort(Comparator.comparing(AddonDescriptor::getId));
    }

    public synchronized void registerAddon(AINPCAddon addon, AINPCPlugin plugin) {
        addon.onLoad(plugin);
        registerDescriptor(addon.getDescriptor());
        addonsById.put(addon.getDescriptor().getId(), addon);
        addon.onEnable(plugin);
    }

    public synchronized void removeByOrigin(String origin) {
        List<String> idsToRemove = descriptorsById.values().stream()
            .filter(descriptor -> descriptor.getOrigin().equalsIgnoreCase(origin))
            .map(AddonDescriptor::getId)
            .toList();

        for (String id : idsToRemove) {
            AddonDescriptor removed = descriptorsById.remove(id);
            if (removed != null) {
                List<AddonDescriptor> descriptors = descriptorsByType.get(removed.getType());
                if (descriptors != null) {
                    descriptors.removeIf(existing -> existing.getId().equals(id));
                }
            }
            addonsById.remove(id);
        }
    }

    public synchronized Collection<AddonDescriptor> getDescriptors() {
        return Collections.unmodifiableCollection(new ArrayList<>(descriptorsById.values()));
    }

    public synchronized List<AddonDescriptor> getDescriptors(AddonType type) {
        return Collections.unmodifiableList(
            new ArrayList<>(descriptorsByType.getOrDefault(type, Collections.emptyList()))
        );
    }

    public synchronized AddonDescriptor getDescriptor(String id) {
        return descriptorsById.get(id);
    }

    public synchronized AddonDescriptor getPrimaryScenario() {
        return descriptorsById.values().stream()
            .filter(descriptor -> descriptor.getType() == AddonType.SCENARIO && descriptor.isPrimaryScenario())
            .findFirst()
            .orElseGet(() -> descriptorsById.values().stream()
                .filter(descriptor -> descriptor.getType() == AddonType.SCENARIO)
                .findFirst()
                .orElse(null));
    }

    public synchronized int size() {
        return descriptorsById.size();
    }

    public synchronized void shutdown(AINPCPlugin plugin) {
        List<AINPCAddon> addons = new ArrayList<>(addonsById.values());
        Collections.reverse(addons);
        for (AINPCAddon addon : addons) {
            addon.onDisable(plugin);
        }
        addonsById.clear();
    }
}
