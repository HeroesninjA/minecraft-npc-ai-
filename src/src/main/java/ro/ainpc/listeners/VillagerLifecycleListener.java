package ro.ainpc.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import ro.ainpc.AINPCPlugin;

/**
 * Sincronizeaza villagerii noi sau incarcati din chunk cu sistemul de NPC-uri.
 */
public class VillagerLifecycleListener extends AbstractPluginListener {

    public VillagerLifecycleListener(AINPCPlugin plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        plugin.getNpcManager().ensureVillagerIsNPC(villager);
        runLater(() -> plugin.getNpcManager().refreshVillagerProfile(villager), 60L);

        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            runLater(() -> plugin.getNpcManager().rebalanceVillagePopulation(villager.getLocation().getChunk()), 80L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        runLater(() -> {
            for (Entity entity : event.getChunk().getEntities()) {
                if (entity instanceof Villager villager) {
                    plugin.getNpcManager().ensureVillagerIsNPC(villager);
                    plugin.getNpcManager().refreshVillagerProfile(villager);
                }
            }

            plugin.getNpcManager().restoreNPCsForChunk(event.getChunk());
            plugin.getNpcManager().rebalanceVillagePopulation(event.getChunk());
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerCareerChange(VillagerCareerChangeEvent event) {
        Villager villager = event.getEntity();
        runLater(() -> plugin.getNpcManager().refreshVillagerProfile(villager), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVillagerDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            plugin.getNpcManager().handleEntityDeath(villager);
        }
    }
}
