package ro.ainpc.listeners;

import org.bukkit.event.Listener;
import ro.ainpc.AINPCPlugin;

/**
 * Punct unic pentru inregistrarea listener-elor pluginului.
 */
public class ListenerRegistry {

    private final AINPCPlugin plugin;

    public ListenerRegistry(AINPCPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        register(new NPCInteractionListener(plugin));
        register(new NPCChatListener(plugin));
        register(new PlayerJoinListener(plugin));
        register(new VillagerLifecycleListener(plugin));
    }

    private void register(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }
}
