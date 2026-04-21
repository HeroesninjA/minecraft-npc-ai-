package ro.ainpc.managers;

import org.bukkit.entity.Player;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.npc.AINPC;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Manager pentru sistemul de amintiri al NPC-urilor
 */
public class MemoryManager {

    private final AINPCPlugin plugin;

    public MemoryManager(AINPCPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Creeaza o amintire noua
     */
    public void createMemory(AINPC npc, Player player, String memoryType, 
                            String content, double emotionalImpact, int importance) {
        // Calculeaza data de expirare bazat pe importanta
        int decayDays = plugin.getConfig().getInt("npc.memory_decay_days", 30);
        int expirationDays = decayDays * importance; // Amintirile importante dureaza mai mult

        String sql = """
            INSERT INTO npc_memories 
            (npc_id, player_uuid, player_name, memory_type, content, emotional_impact, importance, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now', '+' || ? || ' days'))
        """;

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            stmt.setInt(1, npc.getDatabaseId());
            stmt.setString(2, player.getUniqueId().toString());
            stmt.setString(3, player.getName());
            stmt.setString(4, memoryType);
            stmt.setString(5, content);
            stmt.setDouble(6, emotionalImpact);
            stmt.setInt(7, importance);
            stmt.setInt(8, expirationDays);
            stmt.executeUpdate();

            plugin.debug("Amintire creata pentru " + npc.getName() + " despre " + player.getName());

            // Verifica si curata amintirile daca sunt prea multe
            cleanExcessMemories(npc, player);

        } catch (SQLException e) {
            plugin.getLogger().warning("Eroare la crearea amintirii: " + e.getMessage());
        }
    }

    /**
     * Obtine amintiri relevante pentru un context
     */
    public List<String> getRelevantMemories(AINPC npc, Player player, String context, int limit) {
        List<String> memories = new ArrayList<>();

        // Obtine cele mai importante si recente amintiri
        String sql = """
            SELECT content, memory_type, emotional_impact, importance, created_at
            FROM npc_memories
            WHERE npc_id = ? AND player_uuid = ?
            ORDER BY importance DESC, created_at DESC
            LIMIT ?
        """;

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            stmt.setInt(1, npc.getDatabaseId());
            stmt.setString(2, player.getUniqueId().toString());
            stmt.setInt(3, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String content = rs.getString("content");
                    memories.add(content);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Eroare la obtinerea amintirilor: " + e.getMessage());
        }

        return memories;
    }

    /**
     * Obtine toate amintirile despre un jucator
     */
    public List<Memory> getAllMemories(AINPC npc, Player player) {
        List<Memory> memories = new ArrayList<>();

        String sql = """
            SELECT id, memory_type, content, emotional_impact, importance, created_at
            FROM npc_memories
            WHERE npc_id = ? AND player_uuid = ?
            ORDER BY created_at DESC
        """;

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            stmt.setInt(1, npc.getDatabaseId());
            stmt.setString(2, player.getUniqueId().toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Memory memory = new Memory();
                    memory.setId(rs.getInt("id"));
                    memory.setMemoryType(rs.getString("memory_type"));
                    memory.setContent(rs.getString("content"));
                    memory.setEmotionalImpact(rs.getDouble("emotional_impact"));
                    memory.setImportance(rs.getInt("importance"));
                    memory.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    memories.add(memory);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Eroare la obtinerea amintirilor: " + e.getMessage());
        }

        return memories;
    }

    /**
     * Sterge amintirile expirate si in exces
     */
    public void cleanOldMemories() {
        // Sterge amintirile expirate
        String deleteExpired = """
            DELETE FROM npc_memories
            WHERE expires_at IS NOT NULL AND expires_at < datetime('now')
        """;

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(deleteExpired)) {
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                plugin.debug("Sterse " + deleted + " amintiri expirate.");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Eroare la curatarea amintirilor: " + e.getMessage());
        }
    }

    /**
     * Curata amintirile in exces pentru un jucator
     */
    private void cleanExcessMemories(AINPC npc, Player player) {
        int maxMemories = plugin.getConfig().getInt("npc.max_memories_per_player", 50);

        // Pastreaza doar cele mai importante amintiri
        String sql = """
            DELETE FROM npc_memories
            WHERE id IN (
                SELECT id FROM npc_memories
                WHERE npc_id = ? AND player_uuid = ?
                ORDER BY importance ASC, created_at ASC
                LIMIT MAX(0, (
                    SELECT COUNT(*) FROM npc_memories
                    WHERE npc_id = ? AND player_uuid = ?
                ) - ?)
            )
        """;

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            stmt.setInt(1, npc.getDatabaseId());
            stmt.setString(2, player.getUniqueId().toString());
            stmt.setInt(3, npc.getDatabaseId());
            stmt.setString(4, player.getUniqueId().toString());
            stmt.setInt(5, maxMemories);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Eroare la curatarea amintirilor in exces: " + e.getMessage());
        }
    }

    /**
     * Creeaza o amintire de prima intalnire
     */
    public void createFirstMeetingMemory(AINPC npc, Player player) {
        String content = "L-am intalnit pe " + player.getName() + " pentru prima data.";
        createMemory(npc, player, "first_meeting", content, 0.3, 3);
    }

    /**
     * Creeaza o amintire despre un cadou
     */
    public void createGiftMemory(AINPC npc, Player player, String itemName) {
        String content = player.getName() + " mi-a oferit un cadou: " + itemName;
        createMemory(npc, player, "gift", content, 0.5, 4);
    }

    /**
     * Creeaza o amintire despre o tradare sau conflict
     */
    public void createBetrayalMemory(AINPC npc, Player player, String reason) {
        String content = player.getName() + " m-a tradat: " + reason;
        createMemory(npc, player, "betrayal", content, -0.8, 5);
    }

    /**
     * Creeaza o amintire despre ajutor
     */
    public void createHelpMemory(AINPC npc, Player player, String helpType) {
        String content = player.getName() + " m-a ajutat: " + helpType;
        createMemory(npc, player, "help", content, 0.4, 3);
    }

    /**
     * Verifica daca NPC-ul are amintiri despre un jucator
     */
    public boolean hasMemoriesOf(AINPC npc, Player player) {
        String sql = "SELECT COUNT(*) FROM npc_memories WHERE npc_id = ? AND player_uuid = ?";

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            stmt.setInt(1, npc.getDatabaseId());
            stmt.setString(2, player.getUniqueId().toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Eroare la verificarea amintirilor: " + e.getMessage());
        }

        return false;
    }

    /**
     * Obtine numarul de amintiri despre un jucator
     */
    public int getMemoryCount(AINPC npc, Player player) {
        String sql = "SELECT COUNT(*) FROM npc_memories WHERE npc_id = ? AND player_uuid = ?";

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            stmt.setInt(1, npc.getDatabaseId());
            stmt.setString(2, player.getUniqueId().toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Eroare la numararea amintirilor: " + e.getMessage());
        }

        return 0;
    }

    /**
     * Obtine suma impactului emotional al amintirilor
     */
    public double getTotalEmotionalImpact(AINPC npc, Player player) {
        String sql = """
            SELECT SUM(emotional_impact * importance) / SUM(importance) as weighted_avg
            FROM npc_memories
            WHERE npc_id = ? AND player_uuid = ?
        """;

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            stmt.setInt(1, npc.getDatabaseId());
            stmt.setString(2, player.getUniqueId().toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("weighted_avg");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Eroare la calcularea impactului emotional: " + e.getMessage());
        }

        return 0.0;
    }

    // Clasa pentru reprezentarea unei amintiri
    public static class Memory {
        private int id;
        private String memoryType;
        private String content;
        private double emotionalImpact;
        private int importance;
        private LocalDateTime createdAt;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        
        public String getMemoryType() { return memoryType; }
        public void setMemoryType(String memoryType) { this.memoryType = memoryType; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public double getEmotionalImpact() { return emotionalImpact; }
        public void setEmotionalImpact(double emotionalImpact) { this.emotionalImpact = emotionalImpact; }
        
        public int getImportance() { return importance; }
        public void setImportance(int importance) { this.importance = importance; }
        
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public String getTypeEmoji() {
            return switch (memoryType) {
                case "first_meeting" -> "👋";
                case "dialog" -> "💬";
                case "gift" -> "🎁";
                case "help" -> "🤝";
                case "betrayal" -> "💔";
                default -> "📝";
            };
        }

        public String getImportanceStars() {
            return "★".repeat(Math.min(5, importance)) + "☆".repeat(Math.max(0, 5 - importance));
        }
    }
}
