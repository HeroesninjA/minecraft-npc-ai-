package ro.ainpc.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.ai.DialogManager;
import ro.ainpc.npc.AINPC;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Listener dedicat chat-ului privat dintre jucator si NPC.
 */
public class NPCChatListener extends AbstractPluginListener {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final long CONVERSATION_TIMEOUT_MILLIS = 300_000L;

    private final DialogManager dialogManager;

    public NPCChatListener(AINPCPlugin plugin) {
        super(plugin);
        this.dialogManager = plugin.getDialogManager();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = PLAIN_TEXT.serialize(event.message());

        ResolvedDialogTarget target;
        try {
            target = callSync(() -> resolveTarget(player, message));
        } catch (IllegalStateException ex) {
            plugin.getLogger().warning("Nu s-a putut rezolva tinta de dialog: " + ex.getMessage());
            return;
        }

        if (target == null) {
            return;
        }

        event.setCancelled(true);
        runSync(() -> handleResolvedMessage(player, message, target));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        conversations().clearConversation(event.getPlayer());
    }

    private ResolvedDialogTarget resolveTarget(Player player, String message) {
        AINPC activeNpc = conversations().getConversationPartner(player);
        if (activeNpc != null) {
            if (conversations().isExpired(player, CONVERSATION_TIMEOUT_MILLIS)) {
                conversations().clearConversation(player);
            } else {
                return buildTarget(player, activeNpc, true, true, "active_session", 1);
            }
        }

        if (!plugin.getConfig().getBoolean("dialog.passive_listen_enabled", true)) {
            return null;
        }

        double listenRadius = plugin.getConfig().getDouble("dialog.passive_listen_radius", 8.0);
        List<AINPC> nearby = plugin.getNpcManager().getActiveNPCsNear(player.getLocation(), listenRadius).stream()
            .sorted(Comparator.comparingDouble(npc -> npc.getLocation().distanceSquared(player.getLocation())))
            .toList();

        if (nearby.isEmpty()) {
            return null;
        }

        List<AINPC> directMatches = nearby.stream()
            .filter(npc -> mentionsNpc(message, npc))
            .toList();

        if (!directMatches.isEmpty()) {
            return buildTarget(player, directMatches.get(0), true, false, "name_match", nearby.size());
        }

        if (nearby.size() == 1) {
            return buildTarget(player, nearby.get(0), false, false, "single_nearby_npc", 1);
        }

        AINPC nearest = nearby.get(0);
        double directRadius = plugin.getConfig().getDouble("dialog.auto_engage_radius", 4.0);
        if (nearest.getLocation().distance(player.getLocation()) <= directRadius) {
            return buildTarget(player, nearest, false, false, "nearest_npc", nearby.size());
        }

        return null;
    }

    private ResolvedDialogTarget buildTarget(Player player,
                                             AINPC npc,
                                             boolean directAddress,
                                             boolean explicitConversation,
                                             String triggerReason,
                                             int nearbyNpcCount) {
        double distance = npc.getLocation() != null ? npc.getLocation().distance(player.getLocation()) : 0.0;
        return new ResolvedDialogTarget(npc, directAddress, explicitConversation, triggerReason, nearbyNpcCount, distance);
    }

    private void handleResolvedMessage(Player player, String message, ResolvedDialogTarget target) {
        AINPC npc = target.npc();
        if (npc == null || !npc.isSpawned()) {
            conversations().clearConversation(player);
            return;
        }

        if (isGoodbye(message)) {
            if (target.explicitConversation()) {
                endConversation(player, npc);
            }
            return;
        }

        if (!target.explicitConversation()) {
            beginConversationSession(player, npc);
        } else {
            refreshConversationSession(player);
        }

        npc.lookAt(player);
        npc.updateContext();
        npc.getContext().setInteractingPlayer(player);
        npc.getContext().setLastPlayerMessage(message);

        if (dialogManager.isOnCooldown(player, npc)) {
            messages().sendMessage(player, "cooldown");
            return;
        }

        messages().send(player, "&7Tu: &f" + message);
        messages().send(player, "&8" + npc.getName() + " se gandeste...");

        DialogManager.DialogRequest request = new DialogManager.DialogRequest(
            npc,
            player,
            message,
            target.directAddress(),
            target.explicitConversation(),
            target.triggerReason(),
            target.nearbyNpcCount(),
            target.distanceToNpc()
        );

        dialogManager.processMessage(request).thenAccept(result ->
            runSync(() -> {
                if (result == null) {
                    messages().sendMessage(player, "ai_error");
                    return;
                }

                switch (result.getStatus()) {
                    case SUCCESS -> messages().sendNPCMessage(player, npc.getName(), result.getResponse());
                    case COOLDOWN -> messages().sendMessage(player, "cooldown");
                    case ERROR -> messages().sendMessage(player, "ai_error");
                }
            })
        ).exceptionally(ex -> {
            runSync(() -> {
                plugin.getLogger().warning("Eroare la procesarea mesajului: " + ex.getMessage());
                messages().sendMessage(player, "ai_error");
            });
            return null;
        });
    }

    private boolean mentionsNpc(String message, AINPC npc) {
        String normalizedMessage = normalize(message);
        return containsNpcName(normalizedMessage, npc.getName()) || containsNpcName(normalizedMessage, npc.getDisplayName());
    }

    private boolean containsNpcName(String normalizedMessage, String npcName) {
        if (npcName == null || npcName.isBlank()) {
            return false;
        }

        String normalizedName = normalize(npcName);
        return !normalizedName.isBlank() && normalizedMessage.contains(normalizedName);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    private void endConversation(Player player, AINPC npc) {
        conversations().clearConversation(player);
        messages().sendNPCMessage(player, npc.getName(), getGoodbyeMessage(npc));
        messages().send(player, "&7&o(Conversatia cu " + npc.getName() + " s-a incheiat.)");
        plugin.getEmotionManager().processEvent(npc, "player_leave", 1.0);
    }

    private boolean isGoodbye(String message) {
        String lower = message.toLowerCase().trim();
        return lower.equals("pa") || lower.equals("la revedere") || lower.equals("bye") ||
               lower.equals("adio") || lower.equals("exit") || lower.equals("quit") ||
               lower.startsWith("pa ") || lower.equals("gata");
    }

    private String getGoodbyeMessage(AINPC npc) {
        double extraversion = npc.getPersonality().getExtraversion();
        String emotion = npc.getEmotions().getDominantEmotion();

        if (emotion.equals("happiness")) {
            return "La revedere! A fost placut sa vorbim!";
        } else if (emotion.equals("sadness")) {
            return "Pa... ai grija de tine.";
        } else if (emotion.equals("anger")) {
            return "In sfarsit pleci. La revedere.";
        }

        if (extraversion > 0.6) {
            return "Pa pa! Sa ne vedem curand!";
        }
        return "La revedere.";
    }

    private record ResolvedDialogTarget(
        AINPC npc,
        boolean directAddress,
        boolean explicitConversation,
        String triggerReason,
        int nearbyNpcCount,
        double distanceToNpc
    ) {
    }
}
