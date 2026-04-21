package ro.ainpc.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.npc.AINPC;

import java.util.List;

/**
 * Listener pentru evenimente de conectare/deconectare jucatori
 */
public class PlayerJoinListener implements Listener {

    private final AINPCPlugin plugin;

    public PlayerJoinListener(AINPCPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Verifica daca sunt NPC-uri in apropiere care il recunosc pe jucator
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            checkNearbyNPCsRecognition(player);
        }, 40L); // 2 secunde dupa join
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Curata conversatiile active (daca exista)
        // Aceasta se face automat prin timeout, dar e bine sa curatam
    }

    /**
     * Verifica daca NPC-urile din apropiere recunosc jucatorul
     */
    private void checkNearbyNPCsRecognition(Player player) {
        List<AINPC> nearbyNPCs = plugin.getNpcManager().getNPCsNear(player.getLocation(), 20);
        
        for (AINPC npc : nearbyNPCs) {
            // Verifica daca NPC-ul are amintiri despre jucator
            if (plugin.getMemoryManager().hasMemoriesOf(npc, player)) {
                int memoryCount = plugin.getMemoryManager().getMemoryCount(npc, player);
                double emotionalImpact = plugin.getMemoryManager().getTotalEmotionalImpact(npc, player);
                
                // Daca are multe amintiri sau impact emotional semnificativ, reactioneaza
                if (memoryCount >= 5 || Math.abs(emotionalImpact) > 0.3) {
                    // Face NPC-ul sa se uite la jucator
                    npc.lookAt(player);
                    
                    // Aplica efectul emotional
                    if (emotionalImpact > 0) {
                        plugin.getEmotionManager().applyEmotion(npc, "happiness", 0.1);
                    } else if (emotionalImpact < -0.2) {
                        plugin.getEmotionManager().applyEmotion(npc, "anger", 0.1);
                    }
                }
            }
        }
    }
}
