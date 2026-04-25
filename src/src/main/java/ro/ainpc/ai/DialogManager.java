package ro.ainpc.ai;

import org.bukkit.entity.Player;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.engine.DialogueEngine;
import ro.ainpc.npc.AINPC;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager pentru dialoguri si conversatii intre jucatori si NPC-uri
 */
public class DialogManager {

    private final AINPCPlugin plugin;
    private final Map<UUID, Map<UUID, Long>> cooldowns; // playerUUID -> npcUUID -> timestamp

    public DialogManager(AINPCPlugin plugin) {
        this.plugin = plugin;
        this.cooldowns = new ConcurrentHashMap<>();
    }

    /**
     * Proceseaza un mesaj de la jucator catre NPC
     */
    public CompletableFuture<DialogResult> processMessage(AINPC npc, Player player, String message) {
        return processMessage(new DialogRequest(
            npc,
            player,
            message,
            true,
            true,
            "explicit_interaction",
            1,
            0.0
        ));
    }

    public CompletableFuture<DialogResult> processMessage(DialogRequest request) {
        AINPC npc = request.npc();
        Player player = request.player();
        String message = request.message();

        // Verifica cooldown
        if (isOnCooldown(player.getUniqueId(), npc.getUuid())) {
            return CompletableFuture.completedFuture(DialogResult.cooldown());
        }

        // Seteaza cooldown
        setCooldown(player.getUniqueId(), npc.getUuid());

        return CompletableFuture.supplyAsync(() -> {
            // Obtine istoricul conversatiei
            List<OpenAIService.DialogHistory> history = getRecentHistory(npc, player, 5);
            
            // Obtine amintiri relevante
            List<String> memories = plugin.getMemoryManager().getRelevantMemories(npc, player, message, 5);
            int totalMemoryCount = plugin.getMemoryManager().getMemoryCount(npc, player);
            double weightedMemoryImpact = plugin.getMemoryManager().getTotalEmotionalImpact(npc, player);
            
            // Obtine relatia
            OpenAIService.NPCRelationship relationship = getRelationship(npc, player);
            
            return new DialogContext(
                history,
                memories,
                relationship,
                new PromptDbContext(totalMemoryCount, weightedMemoryImpact)
            );
            
        }).thenCompose(context -> {
            DialogueEngine dialogueEngine = plugin.getDialogueEngine();
            if (dialogueEngine != null) {
                return dialogueEngine.generateResponse(
                    request,
                    context.history,
                    context.memories,
                    context.relationship,
                    context.dbContext
                );
            }

            return plugin.getOpenAIService().generateResponse(
                request,
                context.history,
                context.memories,
                context.relationship,
                context.dbContext
            );
            
        }).thenApply(response -> {
            if (response == null || response.isBlank()) {
                return DialogResult.error();
            }

            String sentiment = plugin.getOpenAIService().analyzeSentimentFast(message);

            // Salveaza dialogul in istoric
            saveDialog(npc, player, message, response);
            
            // Actualizeaza relatia
            updateRelationship(npc, player, sentiment);
            
            // Creeaza amintire daca e important
            createMemoryIfImportant(npc, player, message, sentiment);
            
            // Actualizeaza emotiile bazat pe interactiune
            updateEmotions(npc, player, sentiment);
            
            return DialogResult.success(response);
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Eroare in procesarea dialogului: " + ex.getMessage());
            return DialogResult.error();
        });
    }

    /**
     * Obtine istoricul recent al conversatiei
     */
    public List<OpenAIService.DialogHistory> getRecentHistory(AINPC npc, Player player, int limit) {
        List<OpenAIService.DialogHistory> history = new ArrayList<>();
        
        String sql = """
            SELECT player_message, npc_response, created_at
            FROM dialog_history
            WHERE npc_id = ? AND player_uuid = ?
            ORDER BY created_at DESC
            LIMIT ?
        """;

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            stmt.setInt(1, npc.getDatabaseId());
            stmt.setString(2, player.getUniqueId().toString());
            stmt.setInt(3, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    history.add(new OpenAIService.DialogHistory(
                        rs.getString("player_message"),
                        rs.getString("npc_response"),
                        rs.getTimestamp("created_at").getTime()
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Eroare la obtinerea istoricului: " + e.getMessage());
        }

        // Inverseaza pentru ordine cronologica
        Collections.reverse(history);
        return history;
    }

    /**
     * Salveaza un dialog in baza de date
     */
    public void saveDialog(AINPC npc, Player player, String playerMessage, String npcResponse) {
        String sql = """
            INSERT INTO dialog_history (npc_id, player_uuid, player_message, npc_response, emotion_state)
            VALUES (?, ?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            stmt.setInt(1, npc.getDatabaseId());
            stmt.setString(2, player.getUniqueId().toString());
            stmt.setString(3, playerMessage);
            stmt.setString(4, npcResponse);
            stmt.setString(5, npc.getEmotions().getDominantEmotion());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Eroare la salvarea dialogului: " + e.getMessage());
        }
    }

    /**
     * Obtine relatia dintre NPC si jucator
     */
    public OpenAIService.NPCRelationship getRelationship(AINPC npc, Player player) {
        String sql = """
            SELECT affection, trust, respect, familiarity, interaction_count, relationship_type
            FROM npc_relationships
            WHERE npc_id = ? AND player_uuid = ?
        """;

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            stmt.setInt(1, npc.getDatabaseId());
            stmt.setString(2, player.getUniqueId().toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    OpenAIService.NPCRelationship rel = new OpenAIService.NPCRelationship();
                    rel.setAffection(rs.getDouble("affection"));
                    rel.setTrust(rs.getDouble("trust"));
                    rel.setRespect(rs.getDouble("respect"));
                    rel.setFamiliarity(rs.getDouble("familiarity"));
                    rel.setInteractionCount(rs.getInt("interaction_count"));
                    rel.setRelationshipType(rs.getString("relationship_type"));
                    return rel;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Eroare la obtinerea relatiei: " + e.getMessage());
        }

        return null; // Prima intalnire
    }

    /**
     * Actualizeaza relatia dupa o interactiune
     */
    private void updateRelationship(AINPC npc, Player player, String sentiment) {
        double affectionChange = 0;
        double trustChange = 0;
        double respectChange = 0;

        switch (sentiment) {
            case "positive", "compliment" -> {
                affectionChange = 0.05;
                trustChange = 0.03;
            }
            case "greeting" -> {
                affectionChange = 0.02;
                trustChange = 0.01;
            }
            case "negative", "insult" -> {
                affectionChange = -0.1;
                trustChange = -0.05;
                respectChange = -0.05;
            }
            case "threat" -> {
                affectionChange = -0.15;
                trustChange = -0.2;
                respectChange = -0.1;
            }
        }

        // Aplica modificarile in baza de date
        String sql = """
            INSERT INTO npc_relationships 
            (npc_id, player_uuid, player_name, affection, trust, respect, familiarity, interaction_count, last_interaction, relationship_type)
            VALUES (?, ?, ?, ?, ?, ?, 0.1, 1, CURRENT_TIMESTAMP, 'acquaintance')
            ON CONFLICT(npc_id, player_uuid) DO UPDATE SET
                affection = MIN(1.0, MAX(-1.0, affection + ?)),
                trust = MIN(1.0, MAX(-1.0, trust + ?)),
                respect = MIN(1.0, MAX(-1.0, respect + ?)),
                familiarity = MIN(1.0, familiarity + 0.01),
                interaction_count = interaction_count + 1,
                last_interaction = CURRENT_TIMESTAMP,
                relationship_type = CASE
                    WHEN affection + ? > 0.7 THEN 'close_friend'
                    WHEN affection + ? > 0.4 THEN 'friend'
                    WHEN affection + ? > 0.1 THEN 'acquaintance'
                    WHEN affection + ? < -0.5 THEN 'enemy'
                    WHEN affection + ? < -0.2 THEN 'rival'
                    ELSE 'stranger'
                END
        """;

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            stmt.setInt(1, npc.getDatabaseId());
            stmt.setString(2, player.getUniqueId().toString());
            stmt.setString(3, player.getName());
            stmt.setDouble(4, affectionChange);
            stmt.setDouble(5, trustChange);
            stmt.setDouble(6, respectChange);
            stmt.setDouble(7, affectionChange);
            stmt.setDouble(8, trustChange);
            stmt.setDouble(9, respectChange);
            stmt.setDouble(10, affectionChange);
            stmt.setDouble(11, affectionChange);
            stmt.setDouble(12, affectionChange);
            stmt.setDouble(13, affectionChange);
            stmt.setDouble(14, affectionChange);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Eroare la actualizarea relatiei: " + e.getMessage());
        }
    }

    /**
     * Creeaza o amintire daca interactiunea este importanta
     */
    private void createMemoryIfImportant(AINPC npc, Player player, String playerMessage, String sentiment) {
        int importance = switch (sentiment) {
            case "insult", "threat" -> 4;
            case "compliment" -> 3;
            case "positive", "negative" -> 2;
            default -> 1;
        };

        // Creeaza amintire doar pentru interactiuni importante
        if (importance >= 2) {
            String content = "Jucatorul " + player.getName() + " mi-a spus: \"" +
                truncate(playerMessage, 100) + "\" (sentiment: " + sentiment + ")";

            plugin.getMemoryManager().createMemory(
                npc, player,
                "dialog",
                content,
                sentimentToEmotionalImpact(sentiment),
                importance
            );
        }
    }

    /**
     * Actualizeaza emotiile NPC-ului bazat pe mesaj
     */
    private void updateEmotions(AINPC npc, Player player, String sentiment) {
        // Obtine multiplicatorul bazat pe relatie
        OpenAIService.NPCRelationship rel = getRelationship(npc, player);
        double multiplier = rel != null ? (0.5 + rel.getFamiliarity() * 0.5) : 0.5;

        // Aplica efectul emotional
        String interactionType = switch (sentiment) {
            case "positive" -> "compliment";
            case "negative" -> "insult";
            case "greeting" -> "greeting";
            case "threat" -> "threat";
            case "compliment" -> "compliment";
            case "insult" -> "insult";
            default -> "greeting";
        };

        npc.getEmotions().applyInteractionEffect(interactionType, multiplier);
        npc.updateDisplayName();

        // Salveaza emotiile
        plugin.getNpcManager().saveEmotions(npc);
    }

    public boolean isOnCooldown(Player player, AINPC npc) {
        return isOnCooldown(player.getUniqueId(), npc.getUuid());
    }

    // Cooldown methods

    private boolean isOnCooldown(UUID playerUuid, UUID npcUuid) {
        Map<UUID, Long> playerCooldowns = cooldowns.get(playerUuid);
        if (playerCooldowns == null) return false;

        Long lastTime = playerCooldowns.get(npcUuid);
        if (lastTime == null) return false;

        long cooldownMs = plugin.getConfig().getInt("npc.message_cooldown", 2) * 1000L;
        return System.currentTimeMillis() - lastTime < cooldownMs;
    }

    private void setCooldown(UUID playerUuid, UUID npcUuid) {
        cooldowns.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
            .put(npcUuid, System.currentTimeMillis());
    }

    // Helper methods

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private double sentimentToEmotionalImpact(String sentiment) {
        return switch (sentiment) {
            case "positive", "compliment" -> 0.5;
            case "greeting" -> 0.1;
            case "negative" -> -0.3;
            case "insult" -> -0.6;
            case "threat" -> -0.8;
            default -> 0.0;
        };
    }

    // Context holder class
    private static class DialogContext {
        final List<OpenAIService.DialogHistory> history;
        final List<String> memories;
        final OpenAIService.NPCRelationship relationship;
        final PromptDbContext dbContext;

        DialogContext(List<OpenAIService.DialogHistory> history, 
                     List<String> memories,
                     OpenAIService.NPCRelationship relationship,
                     PromptDbContext dbContext) {
            this.history = history;
            this.memories = memories;
            this.relationship = relationship;
            this.dbContext = dbContext;
        }
    }

    public record PromptDbContext(
        int totalMemoryCount,
        double weightedMemoryImpact
    ) {
    }

    public static class DialogResult {
        private final DialogStatus status;
        private final String response;

        private DialogResult(DialogStatus status, String response) {
            this.status = status;
            this.response = response;
        }

        public static DialogResult success(String response) {
            return new DialogResult(DialogStatus.SUCCESS, response);
        }

        public static DialogResult cooldown() {
            return new DialogResult(DialogStatus.COOLDOWN, null);
        }

        public static DialogResult error() {
            return new DialogResult(DialogStatus.ERROR, null);
        }

        public DialogStatus getStatus() {
            return status;
        }

        public String getResponse() {
            return response;
        }

        public boolean isSuccess() {
            return status == DialogStatus.SUCCESS;
        }
    }

    public enum DialogStatus {
        SUCCESS,
        COOLDOWN,
        ERROR
    }

    public record DialogRequest(
        AINPC npc,
        Player player,
        String message,
        boolean directAddress,
        boolean explicitConversation,
        String triggerReason,
        int nearbyNpcCount,
        double distanceToNpc
    ) {
    }
}
