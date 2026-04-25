package ro.ainpc.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.npc.AINPC;

import java.util.List;

/**
 * Listener pentru evenimente de intrare si recunoastere a jucatorului.
 */
public class PlayerJoinListener extends AbstractPluginListener {

    public PlayerJoinListener(AINPCPlugin plugin) {
        super(plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Verifica daca sunt NPC-uri in apropiere care il recunosc pe jucator
        runLater(() -> checkNearbyNPCsRecognition(player), 40L);
    }

    /**
     * Verifica daca NPC-urile din apropiere recunosc jucatorul
     */
    private void checkNearbyNPCsRecognition(Player player) {
        List<AINPC> nearbyNPCs = plugin.getNpcManager().getActiveNPCsNear(player.getLocation(), 20);
        
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
