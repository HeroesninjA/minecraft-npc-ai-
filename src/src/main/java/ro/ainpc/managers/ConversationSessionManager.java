package ro.ainpc.managers;

import org.bukkit.entity.Player;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.npc.AINPC;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pastreaza doar starea de sesiune a conversatiilor active.
 * Listener-ele nu ar trebui sa detina aceasta stare direct.
 */
public class ConversationSessionManager {

    private final AINPCPlugin plugin;
    private final Map<UUID, ConversationSession> sessions;

    public ConversationSessionManager(AINPCPlugin plugin) {
        this.plugin = plugin;
        this.sessions = new ConcurrentHashMap<>();
    }

    public void startConversation(Player player, AINPC npc) {
        sessions.put(player.getUniqueId(), new ConversationSession(npc.getUuid(), System.currentTimeMillis()));
    }

    public void touchConversation(Player player) {
        sessions.computeIfPresent(player.getUniqueId(), (ignored, session) -> session.touch());
    }

    public boolean isInConversation(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public boolean isExpired(Player player, long timeoutMillis) {
        ConversationSession session = sessions.get(player.getUniqueId());
        return session != null && System.currentTimeMillis() - session.lastInteractionAt() > timeoutMillis;
    }

    public UUID getConversationNpcId(Player player) {
        ConversationSession session = sessions.get(player.getUniqueId());
        return session != null ? session.npcUuid() : null;
    }

    public AINPC getConversationPartner(Player player) {
        UUID npcUuid = getConversationNpcId(player);
        return npcUuid != null ? plugin.getNpcManager().getNPCByUuid(npcUuid) : null;
    }

    public void clearConversation(Player player) {
        clearConversation(player.getUniqueId());
    }

    public void clearConversation(UUID playerUuid) {
        sessions.remove(playerUuid);
    }

    private record ConversationSession(UUID npcUuid, long lastInteractionAt) {
        private ConversationSession touch() {
            return new ConversationSession(npcUuid, System.currentTimeMillis());
        }
    }
}
