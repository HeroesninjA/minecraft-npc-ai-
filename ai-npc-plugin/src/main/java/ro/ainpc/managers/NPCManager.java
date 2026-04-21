package ro.ainpc.managers;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.npc.AINPC;
import ro.ainpc.npc.NPCEmotions;
import ro.ainpc.npc.NPCPersonality;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager pentru toate NPC-urile AI din plugin
 */
public class NPCManager {

    private final AINPCPlugin plugin;
    private final Map<UUID, AINPC> npcsByUuid;
    private final Map<Integer, AINPC> npcsById;
    private final Map<UUID, AINPC> npcsByEntityId;

    public NPCManager(AINPCPlugin plugin) {
        this.plugin = plugin;
        this.npcsByUuid = new ConcurrentHashMap<>();
        this.npcsById = new ConcurrentHashMap<>();
        this.npcsByEntityId = new ConcurrentHashMap<>();
    }

    /**
     * Incarca toate NPC-urile din baza de date
     */
    public void loadAllNPCs() {
        String sql = """
            SELECT n.*, 
                   p.openness, p.conscientiousness, p.extraversion, p.agreeableness, p.neuroticism,
                   e.happiness, e.sadness, e.anger, e.fear, e.surprise, e.disgust, e.trust, e.anticipation
            FROM npcs n
            LEFT JOIN npc_personality p ON n.id = p.npc_id
            LEFT JOIN npc_emotions e ON n.id = e.npc_id
        """;

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            int count = 0;
            while (rs.next()) {
                AINPC npc = loadNPCFromResultSet(rs);
                if (npc != null) {
                    registerNPC(npc);
                    
                    // Spawneaza NPC-ul
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        npc.spawn();
                    });
                    
                    count++;
                }
            }

            plugin.getLogger().info("Incarcate " + count + " NPC-uri din baza de date.");

        } catch (SQLException e) {
            plugin.getLogger().severe("Eroare la incarcarea NPC-urilor: " + e.getMessage());
        }
    }

    /**
     * Incarca un NPC din ResultSet
     */
    private AINPC loadNPCFromResultSet(ResultSet rs) throws SQLException {
        AINPC npc = new AINPC(plugin);

        npc.setDatabaseId(rs.getInt("id"));
        npc.setUuid(UUID.fromString(rs.getString("uuid")));
        npc.setName(rs.getString("name"));
        npc.setDisplayName(rs.getString("display_name"));
        npc.setLocation(
            rs.getString("world"),
            rs.getDouble("x"),
            rs.getDouble("y"),
            rs.getDouble("z"),
            rs.getFloat("yaw"),
            rs.getFloat("pitch")
        );
        npc.setSkinTexture(rs.getString("skin_texture"));
        npc.setSkinSignature(rs.getString("skin_signature"));
        npc.setBackstory(rs.getString("backstory"));
        npc.setOccupation(rs.getString("occupation"));
        npc.setAge(rs.getInt("age"));
        npc.setGender(rs.getString("gender"));

        // Incarca personalitatea
        NPCPersonality personality = new NPCPersonality(
            rs.getDouble("openness"),
            rs.getDouble("conscientiousness"),
            rs.getDouble("extraversion"),
            rs.getDouble("agreeableness"),
            rs.getDouble("neuroticism")
        );
        npc.setPersonality(personality);

        // Incarca emotiile
        NPCEmotions emotions = new NPCEmotions();
        emotions.setHappiness(rs.getDouble("happiness"));
        emotions.setSadness(rs.getDouble("sadness"));
        emotions.setAnger(rs.getDouble("anger"));
        emotions.setFear(rs.getDouble("fear"));
        emotions.setSurprise(rs.getDouble("surprise"));
        emotions.setDisgust(rs.getDouble("disgust"));
        emotions.setTrust(rs.getDouble("trust"));
        emotions.setAnticipation(rs.getDouble("anticipation"));
        npc.setEmotions(emotions);

        return npc;
    }

    /**
     * Creeaza un NPC nou
     */
    public AINPC createNPC(String name, Location location) {
        return createNPC(name, location, null, null, 30, "male", null);
    }

    /**
     * Creeaza un NPC nou cu toate optiunile
     */
    public AINPC createNPC(String name, Location location, String occupation, 
                           String backstory, int age, String gender, String archetype) {
        AINPC npc = new AINPC(plugin);
        npc.setName(name);
        npc.setDisplayName(name);
        npc.setLocation(
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch()
        );
        npc.setOccupation(occupation);
        npc.setBackstory(backstory);
        npc.setAge(age);
        npc.setGender(gender);

        // Genereaza personalitate
        if (archetype != null && !archetype.isEmpty()) {
            npc.setPersonality(NPCPersonality.fromArchetype(archetype));
        } else {
            npc.setPersonality(NPCPersonality.generateRandom());
        }

        // Salveaza in baza de date
        if (saveNPC(npc)) {
            registerNPC(npc);
            npc.spawn();
            
            // Genereaza familia daca e configurat
            if (plugin.getConfig().getBoolean("family.auto_generate", true)) {
                plugin.getFamilyManager().generateFamily(npc);
            }
            
            return npc;
        }

        return null;
    }

    /**
     * Salveaza un NPC in baza de date
     */
    public boolean saveNPC(AINPC npc) {
        String sql;
        boolean isNew = npc.getDatabaseId() == 0;

        if (isNew) {
            sql = """
                INSERT INTO npcs (uuid, name, display_name, world, x, y, z, yaw, pitch, 
                                  skin_texture, skin_signature, backstory, occupation, age, gender)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        } else {
            sql = """
                UPDATE npcs SET name = ?, display_name = ?, world = ?, x = ?, y = ?, z = ?, 
                                yaw = ?, pitch = ?, skin_texture = ?, skin_signature = ?, 
                                backstory = ?, occupation = ?, age = ?, gender = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
            """;
        }

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;

            if (isNew) {
                stmt.setString(i++, npc.getUuid().toString());
            }

            stmt.setString(i++, npc.getName());
            stmt.setString(i++, npc.getDisplayName());
            stmt.setString(i++, npc.getWorldName());
            stmt.setDouble(i++, npc.getX());
            stmt.setDouble(i++, npc.getY());
            stmt.setDouble(i++, npc.getZ());
            stmt.setFloat(i++, npc.getYaw());
            stmt.setFloat(i++, npc.getPitch());
            stmt.setString(i++, npc.getSkinTexture());
            stmt.setString(i++, npc.getSkinSignature());
            stmt.setString(i++, npc.getBackstory());
            stmt.setString(i++, npc.getOccupation());
            stmt.setInt(i++, npc.getAge());
            stmt.setString(i++, npc.getGender());

            if (!isNew) {
                stmt.setInt(i, npc.getDatabaseId());
            }

            stmt.executeUpdate();

            if (isNew) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        npc.setDatabaseId(rs.getInt(1));
                    }
                }
                
                // Salveaza personalitatea si emotiile
                savePersonality(npc);
                saveEmotions(npc);
            }

            return true;

        } catch (SQLException e) {
            plugin.getLogger().severe("Eroare la salvarea NPC: " + e.getMessage());
            return false;
        }
    }

    /**
     * Salveaza personalitatea NPC-ului
     */
    private void savePersonality(AINPC npc) {
        String sql = """
            INSERT OR REPLACE INTO npc_personality 
            (npc_id, openness, conscientiousness, extraversion, agreeableness, neuroticism)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            NPCPersonality p = npc.getPersonality();
            stmt.setInt(1, npc.getDatabaseId());
            stmt.setDouble(2, p.getOpenness());
            stmt.setDouble(3, p.getConscientiousness());
            stmt.setDouble(4, p.getExtraversion());
            stmt.setDouble(5, p.getAgreeableness());
            stmt.setDouble(6, p.getNeuroticism());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Eroare la salvarea personalitatii: " + e.getMessage());
        }
    }

    /**
     * Salveaza emotiile NPC-ului
     */
    public void saveEmotions(AINPC npc) {
        String sql = """
            INSERT OR REPLACE INTO npc_emotions 
            (npc_id, happiness, sadness, anger, fear, surprise, disgust, trust, anticipation, last_updated)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        """;

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            NPCEmotions e = npc.getEmotions();
            stmt.setInt(1, npc.getDatabaseId());
            stmt.setDouble(2, e.getHappiness());
            stmt.setDouble(3, e.getSadness());
            stmt.setDouble(4, e.getAnger());
            stmt.setDouble(5, e.getFear());
            stmt.setDouble(6, e.getSurprise());
            stmt.setDouble(7, e.getDisgust());
            stmt.setDouble(8, e.getTrust());
            stmt.setDouble(9, e.getAnticipation());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Eroare la salvarea emotiilor: " + e.getMessage());
        }
    }

    /**
     * Salveaza toate NPC-urile
     */
    public void saveAllNPCs() {
        for (AINPC npc : npcsByUuid.values()) {
            saveNPC(npc);
            saveEmotions(npc);
        }
    }

    /**
     * Sterge un NPC
     */
    public boolean deleteNPC(AINPC npc) {
        // Despawneaza
        npc.despawn();
        
        // Sterge din baza de date
        String sql = "DELETE FROM npcs WHERE id = ?";
        
        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            stmt.setInt(1, npc.getDatabaseId());
            stmt.executeUpdate();
            
            // Sterge din cache
            unregisterNPC(npc);
            
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Eroare la stergerea NPC: " + e.getMessage());
            return false;
        }
    }

    /**
     * Inregistreaza un NPC in cache
     */
    private void registerNPC(AINPC npc) {
        npcsByUuid.put(npc.getUuid(), npc);
        npcsById.put(npc.getDatabaseId(), npc);
    }

    /**
     * Scoate un NPC din cache
     */
    private void unregisterNPC(AINPC npc) {
        npcsByUuid.remove(npc.getUuid());
        npcsById.remove(npc.getDatabaseId());
        if (npc.getBukkitEntity() != null) {
            npcsByEntityId.remove(npc.getBukkitEntity().getUniqueId());
        }
    }

    /**
     * Asociaza entitatea Bukkit cu NPC-ul
     */
    public void registerEntity(AINPC npc, Entity entity) {
        npcsByEntityId.put(entity.getUniqueId(), npc);
    }

    // Metode de cautare

    public AINPC getNPCByUuid(UUID uuid) {
        return npcsByUuid.get(uuid);
    }

    public AINPC getNPCByUUID(UUID uuid) {
        return getNPCByUuid(uuid);
    }

    public AINPC getNPCById(int id) {
        return npcsById.get(id);
    }

    public AINPC getNPCByEntity(Entity entity) {
        if (entity == null) return null;
        
        // Cauta dupa UUID-ul entitatii
        AINPC npc = npcsByEntityId.get(entity.getUniqueId());
        if (npc != null) return npc;
        
        // Cauta prin toate NPC-urile
        for (AINPC n : npcsByUuid.values()) {
            if (n.getBukkitEntity() != null && n.getBukkitEntity().getUniqueId().equals(entity.getUniqueId())) {
                npcsByEntityId.put(entity.getUniqueId(), n);
                return n;
            }
        }
        
        return null;
    }

    public AINPC getNPCByName(String name) {
        for (AINPC npc : npcsByUuid.values()) {
            if (npc.getName().equalsIgnoreCase(name)) {
                return npc;
            }
        }
        return null;
    }

    public Collection<AINPC> getAllNPCs() {
        return Collections.unmodifiableCollection(npcsByUuid.values());
    }

    public int getNPCCount() {
        return npcsByUuid.size();
    }

    /**
     * Gaseste NPC-urile din apropierea unei locatii
     */
    public List<AINPC> getNPCsNear(Location location, double radius) {
        List<AINPC> nearby = new ArrayList<>();
        
        for (AINPC npc : npcsByUuid.values()) {
            Location npcLoc = npc.getLocation();
            if (npcLoc != null && npcLoc.getWorld().equals(location.getWorld())) {
                if (npcLoc.distance(location) <= radius) {
                    nearby.add(npc);
                }
            }
        }
        
        return nearby;
    }
}
