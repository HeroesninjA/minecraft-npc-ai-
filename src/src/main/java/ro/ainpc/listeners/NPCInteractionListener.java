package ro.ainpc.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.ai.DialogManager;
import ro.ainpc.npc.AINPC;

/**
 * Listener pentru interactiunile cu NPC-urile
 */
public class NPCInteractionListener extends AbstractPluginListener {

    private final DialogManager dialogManager;

    public NPCInteractionListener(AINPCPlugin plugin) {
        super(plugin);
        this.dialogManager = plugin.getDialogManager();
    }

    /**
     * Cand un jucator da click dreapta pe un NPC
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        
        // Verifica daca e un Villager (baza pentru NPC-uri)
        if (!(entity instanceof Villager)) return;
        
        AINPC npc = plugin.getNpcManager().getNPCByEntity(entity);
        if (npc == null) {
            npc = plugin.getNpcManager().ensureVillagerIsNPC((Villager) entity);
        }
        if (npc == null) return;
        
        // Anuleaza interactiunea default
        event.setCancelled(true);
        
        Player player = event.getPlayer();
        
        // Verifica distanta
        if (!npc.isInRange(player)) {
            messages().sendMessage(player, "too_far");
            return;
        }
        
        // Face NPC-ul sa se uite la jucator
        npc.lookAt(player);
        npc.updateContext();
        npc.getContext().setInteractingPlayer(player);
        
        // Activeaza conversatia
        startConversation(player, npc);
    }

    /**
     * Incepe o conversatie cu un NPC
     */
    private void startConversation(Player player, AINPC npc) {
        boolean firstMeeting = beginConversationSession(player, npc);
        
        // Mesaj de inceput
        String greeting;
        if (firstMeeting) {
            greeting = getFirstMeetingGreeting(npc);
        } else {
            greeting = getReturningGreeting(npc, player);
        }
        
        messages().sendNPCMessage(player, npc.getName(), greeting);
        messages().send(player, "&7&o(Scrie in chat pentru a vorbi cu " + npc.getName() + ". Scrie 'pa' pentru a termina conversatia.)");
        
        // Aplica efectul emotional de intalnire
        plugin.getEmotionManager().processEvent(npc, "player_approach", 1.0);
    }

    private String getFirstMeetingGreeting(AINPC npc) {
        double extraversion = npc.getPersonality().getExtraversion();
        
        if (extraversion > 0.7) {
            return "Buna ziua, straiin! Ce te aduce pe aici? Eu sunt " + npc.getName() + "!";
        } else if (extraversion > 0.4) {
            return "Salut. Nu cred ca ne-am mai intalnit. Eu sunt " + npc.getName() + ".";
        } else {
            return "Hm? Ah... salut. Sunt " + npc.getName() + ".";
        }
    }

    private String getReturningGreeting(AINPC npc, Player player) {
        double affection = 0;
        var relationship = dialogManager.getRelationship(npc, player);
        if (relationship != null) {
            affection = relationship.getAffection();
        }

        if (affection > 0.6) {
            return "Ce bucurie sa te vad din nou, " + player.getName() + "! Cum mai esti?";
        } else if (affection > 0.3) {
            return "A, " + player.getName() + "! Bine ai revenit.";
        } else if (affection > 0) {
            return "Te-ai intors, " + player.getName() + ". Ce mai vrei?";
        } else if (affection > -0.3) {
            return "Tu iar? Ce vrei?";
        } else {
            return "*oftez* Ce vrei de la mine?";
        }
    }

}
