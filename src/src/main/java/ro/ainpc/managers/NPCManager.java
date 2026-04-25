package ro.ainpc.managers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.engine.FeaturePackLoader;
import ro.ainpc.npc.AINPC;
import ro.ainpc.npc.NPCEmotions;
import ro.ainpc.npc.NPCPersonality;
import ro.ainpc.utils.NPCNameGenerator;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager pentru toate NPC-urile AI din plugin
 */
public class NPCManager {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private final AINPCPlugin plugin;
    private final Gson gson;
    private final Map<UUID, AINPC> npcsByUuid;
    private final Map<Integer, AINPC> npcsById;
    private final Map<UUID, AINPC> npcsByEntityId;
    private final Map<String, Long> villagePopulationCooldowns;

    public NPCManager(AINPCPlugin plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
        this.npcsByUuid = new ConcurrentHashMap<>();
        this.npcsById = new ConcurrentHashMap<>();
        this.npcsByEntityId = new ConcurrentHashMap<>();
        this.villagePopulationCooldowns = new ConcurrentHashMap<>();
    }

    /**
     * Incarca toate NPC-urile din baza de date
     */
    public void loadAllNPCs() {
        String sql = """
            SELECT n.*,
                   COALESCE(p.openness, 0.5) AS openness,
                   COALESCE(p.conscientiousness, 0.5) AS conscientiousness,
                   COALESCE(p.extraversion, 0.5) AS extraversion,
                   COALESCE(p.agreeableness, 0.5) AS agreeableness,
                   COALESCE(p.neuroticism, 0.5) AS neuroticism,
                   COALESCE(e.happiness, 0.5) AS happiness,
                   COALESCE(e.sadness, 0.0) AS sadness,
                   COALESCE(e.anger, 0.0) AS anger,
                   COALESCE(e.fear, 0.0) AS fear,
                   COALESCE(e.surprise, 0.0) AS surprise,
                   COALESCE(e.disgust, 0.0) AS disgust,
                   COALESCE(e.trust, 0.5) AS trust,
                   COALESCE(e.anticipation, 0.3) AS anticipation,
                   pr.npc_id AS profile_npc_id,
                   COALESCE(pr.profile_source, 'manual') AS profile_source,
                   COALESCE(pr.profile_version, 1) AS profile_version,
                   COALESCE(pr.profile_summary, '') AS profile_summary,
                   COALESCE(pr.profile_data, '{}') AS profile_data
            FROM npcs n
            LEFT JOIN npc_personality p ON n.id = p.npc_id
            LEFT JOIN npc_emotions e ON n.id = e.npc_id
            LEFT JOIN npc_profiles pr ON n.id = pr.npc_id
        """;

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            int count = 0;
            while (rs.next()) {
                AINPC npc = loadNPCFromResultSet(rs);
                if (npc == null) {
                    continue;
                }

                loadTraits(npc);
                registerNPC(npc);
                count++;
            }

            plugin.getLogger().info("Incarcate " + count + " NPC-uri din baza de date.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Eroare la incarcarea NPC-urilor: " + e.getMessage());
        }
    }

    /**
     * Sincronizeaza toti villagerii deja incarcati din lume cu sistemul de NPC-uri.
     */
    public void discoverExistingVillagers() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                ensureVillagerIsNPC(villager);
            }
        }
    }

    /**
     * Dupa ce villagerii existenti au fost asociati, restaureaza doar NPC-urile ramase fara entitate
     * in chunk-uri deja incarcate.
     */
    public void restoreMissingNPCsInLoadedChunks() {
        for (AINPC npc : npcsByUuid.values()) {
            if (npc.isSpawned() || !isChunkLoaded(npc)) {
                continue;
            }

            attachLoadedNPC(npc);
        }
    }

    /**
     * Activeaza NPC-urile persistate care apartin chunk-ului tocmai incarcat.
     */
    public void restoreNPCsForChunk(Chunk chunk) {
        if (chunk == null) {
            return;
        }

        for (AINPC npc : npcsByUuid.values()) {
            if (npc.isSpawned() || !belongsToChunk(npc, chunk)) {
                continue;
            }

            attachLoadedNPC(npc, chunk);
        }
    }

    /**
     * Se asigura ca un villager are profil AI si este cunoscut de manager.
     */
    public AINPC ensureVillagerIsNPC(Villager villager) {
        if (villager == null || !villager.isValid()) {
            return null;
        }

        AINPC existing = getNPCByEntity(villager);
        if (existing != null) {
            existing.attachToVillager(villager);
            registerEntity(existing, villager);
            return existing;
        }

        AINPC byUuid = getNPCByUuid(villager.getUniqueId());
        if (byUuid != null) {
            byUuid.attachToVillager(villager);
            registerEntity(byUuid, villager);
            return byUuid;
        }

        AINPC legacyNpc = findLegacyNPCForVillager(villager);
        if (legacyNpc != null) {
            UUID previousUuid = legacyNpc.getUuid();
            legacyNpc.attachToVillager(villager);
            registerEntity(legacyNpc, villager);
            refreshNpcCache(legacyNpc, previousUuid);
            if (!Objects.equals(previousUuid, legacyNpc.getUuid())) {
                saveNPC(legacyNpc);
            }
            return legacyNpc;
        }

        AINPC equivalentActiveNpc = findEquivalentActiveNPC(villager);
        if (equivalentActiveNpc != null && isLegacyPluginVillager(villager)) {
            plugin.getLogger().warning("Elimin villager duplicat pentru NPC-ul '" + equivalentActiveNpc.getName()
                + "' la " + formatLocation(villager.getLocation()) + ".");
            villager.remove();
            return equivalentActiveNpc;
        }

        AINPC npc = createAutoProfile(villager);
        if (npc != null) {
            registerNPC(npc);
            registerEntity(npc, villager);
        }
        return npc;
    }

    public void refreshVillagerProfile(Villager villager) {
        if (villager == null || !villager.isValid()) {
            return;
        }

        AINPC npc = getNPCByEntity(villager);
        if (npc == null) {
            npc = getNPCByUuid(villager.getUniqueId());
        }
        if (npc == null) {
            npc = ensureVillagerIsNPC(villager);
        }
        if (npc == null) {
            return;
        }

        Random random = createVillagerSeededRandom(villager);
        String resolvedOccupation = resolveOccupationForVillager(villager, random);
        String currentOccupation = npc.getOccupation();

        boolean shouldPromoteOccupation = isGenericOccupation(currentOccupation)
            || !isGenericOccupation(resolvedOccupation);
        boolean occupationChanged = shouldPromoteOccupation
            && resolvedOccupation != null
            && !resolvedOccupation.isBlank()
            && !resolvedOccupation.equalsIgnoreCase(currentOccupation);

        if (occupationChanged) {
            npc.setOccupation(resolvedOccupation);
        }

        if (occupationChanged && "auto".equalsIgnoreCase(npc.getProfileSource())) {
            npc.setBackstory(generateBackstory(npc.getName(), npc.getOccupation(), villager.getProfession()));
            npc.setPersonality(generatePersonalityForOccupation(npc.getOccupation(), villager.getProfession()));
        }

        applyThemeDefaults(npc);

        if (occupationChanged) {
            saveNPC(npc, false);
            plugin.debug("Profilul villagerului '" + npc.getName() + "' a fost actualizat la ocupatia: " + npc.getOccupation());
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
        npc.setProfileSource(rs.getString("profile_source"));
        npc.setProfileVersion(rs.getInt("profile_version"));
        npc.setProfileSummary(rs.getString("profile_summary"));
        npc.setProfileDataJson(rs.getString("profile_data"));
        npc.setProfileCreated(rs.getObject("profile_npc_id") != null);

        NPCPersonality personality = new NPCPersonality(
            rs.getDouble("openness"),
            rs.getDouble("conscientiousness"),
            rs.getDouble("extraversion"),
            rs.getDouble("agreeableness"),
            rs.getDouble("neuroticism")
        );
        npc.setPersonality(personality);

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
        npc.setProfileSource("manual");

        if (archetype != null && !archetype.isEmpty()) {
            npc.setPersonality(NPCPersonality.fromArchetype(archetype));
        } else {
            npc.setPersonality(NPCPersonality.generateRandom());
        }

        applyThemeDefaults(npc);

        if (!npc.spawn()) {
            return null;
        }

        if (saveNPC(npc)) {
            registerNPC(npc);
            if (npc.getBukkitEntity() != null) {
                registerEntity(npc, npc.getBukkitEntity());
            }

            if (plugin.getConfig().getBoolean("family.auto_generate", true)) {
                plugin.getFamilyManager().generateFamily(npc);
            }

            return npc;
        }

        npc.despawn();
        return null;
    }

    /**
     * Salveaza un NPC in baza de date
     */
    public boolean saveNPC(AINPC npc) {
        return saveNPC(npc, true);
    }

    public boolean saveNPC(AINPC npc, boolean syncFromEntity) {
        if (syncFromEntity) {
            npc.syncLocationFromEntity();
        }

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
                UPDATE npcs SET uuid = ?, name = ?, display_name = ?, world = ?, x = ?, y = ?, z = ?,
                                yaw = ?, pitch = ?, skin_texture = ?, skin_signature = ?,
                                backstory = ?, occupation = ?, age = ?, gender = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
            """;
        }

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;

            stmt.setString(i++, npc.getUuid().toString());
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
            }

            return persistProfileData(npc);
        } catch (SQLException e) {
            plugin.getLogger().severe("Eroare la salvarea NPC: " + e.getMessage());
            return false;
        }
    }

    /**
     * Salveaza personalitatea NPC-ului
     */
    private boolean savePersonality(AINPC npc) {
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
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Eroare la salvarea personalitatii: " + e.getMessage());
            return false;
        }
    }

    /**
     * Salveaza emotiile NPC-ului
     */
    public boolean saveEmotions(AINPC npc) {
        boolean emotionsSaved = saveEmotionsRow(npc);
        if (!emotionsSaved) {
            return false;
        }
        return saveProfile(npc);
    }

    private boolean saveEmotionsRow(AINPC npc) {
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
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Eroare la salvarea emotiilor: " + e.getMessage());
            return false;
        }
    }

    private void loadTraits(AINPC npc) throws SQLException {
        String sql = """
            SELECT trait_id
            FROM npc_traits
            WHERE npc_id = ?
            ORDER BY trait_id ASC
        """;

        List<String> traits = new ArrayList<>();
        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            stmt.setInt(1, npc.getDatabaseId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    traits.add(rs.getString("trait_id"));
                }
            }
        }

        npc.setTraits(traits);
    }

    private boolean saveTraits(AINPC npc) {
        String deleteSql = "DELETE FROM npc_traits WHERE npc_id = ?";
        String insertSql = """
            INSERT OR IGNORE INTO npc_traits (npc_id, trait_id)
            VALUES (?, ?)
        """;

        try (PreparedStatement deleteStmt = plugin.getDatabaseManager().prepareStatement(deleteSql)) {
            deleteStmt.setInt(1, npc.getDatabaseId());
            deleteStmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Eroare la stergerea traits pentru profilul NPC: " + e.getMessage());
            return false;
        }

        if (npc.getTraits() == null || npc.getTraits().isEmpty()) {
            return true;
        }

        try (PreparedStatement insertStmt = plugin.getDatabaseManager().prepareStatement(insertSql)) {
            for (String traitId : npc.getTraits()) {
                if (traitId == null || traitId.isBlank()) {
                    continue;
                }
                insertStmt.setInt(1, npc.getDatabaseId());
                insertStmt.setString(2, traitId);
                insertStmt.addBatch();
            }
            insertStmt.executeBatch();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Eroare la salvarea traits pentru profilul NPC: " + e.getMessage());
            return false;
        }
    }

    private boolean saveProfile(AINPC npc) {
        String summary = buildProfileSummary(npc);
        String profileData = buildProfileData(npc);
        String sql = """
            INSERT INTO npc_profiles (npc_id, profile_source, profile_version, profile_summary, profile_data, updated_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(npc_id) DO UPDATE SET
                profile_source = excluded.profile_source,
                profile_version = excluded.profile_version,
                profile_summary = excluded.profile_summary,
                profile_data = excluded.profile_data,
                updated_at = CURRENT_TIMESTAMP
        """;

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            stmt.setInt(1, npc.getDatabaseId());
            stmt.setString(2, npc.getProfileSource());
            stmt.setInt(3, npc.getProfileVersion());
            stmt.setString(4, summary);
            stmt.setString(5, profileData);
            stmt.executeUpdate();

            npc.setProfileSummary(summary);
            npc.setProfileDataJson(profileData);
            npc.setProfileCreated(true);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Eroare la salvarea profilului NPC: " + e.getMessage());
            return false;
        }
    }

    private boolean persistProfileData(AINPC npc) {
        if (npc.getDatabaseId() <= 0) {
            plugin.getLogger().severe("Nu pot salva profilul pentru NPC-ul '" + npc.getName()
                + "' deoarece nu are ID de baza de date.");
            return false;
        }

        boolean personalitySaved = savePersonality(npc);
        boolean emotionsSaved = saveEmotionsRow(npc);
        boolean traitsSaved = saveTraits(npc);
        boolean profileSaved = saveProfile(npc);
        return personalitySaved && emotionsSaved && traitsSaved && profileSaved;
    }

    public int ensureAllNPCsHaveProfiles() {
        int backfilledProfiles = 0;

        for (AINPC npc : npcsByUuid.values()) {
            applyThemeDefaults(npc);
            boolean missingProfile = !npc.isProfileCreated();
            if (persistProfileData(npc) && missingProfile) {
                backfilledProfiles++;
            }
        }

        return backfilledProfiles;
    }

    /**
     * Salveaza toate NPC-urile
     */
    public void saveAllNPCs() {
        saveAllNPCs(true);
    }

    public void saveAllNPCs(boolean syncFromEntity) {
        for (AINPC npc : npcsByUuid.values()) {
            saveNPC(npc, syncFromEntity);
        }
    }

    /**
     * Citeste starea curenta a entitatilor Bukkit pe thread-ul principal.
     */
    public void syncAllNPCEntityState() {
        for (AINPC npc : npcsByUuid.values()) {
            npc.syncLocationFromEntity();
        }
    }

    public void rebalanceLoadedVillages() {
        if (!plugin.getConfig().getBoolean("villagers.auto_repopulate.enabled", true)) {
            return;
        }

        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                rebalanceVillagePopulation(chunk);
            }
        }
    }

    public void rebalanceVillagePopulation(Chunk chunk) {
        if (chunk == null || chunk.getWorld() == null) {
            return;
        }

        if (!plugin.getConfig().getBoolean("villagers.auto_repopulate.enabled", true)) {
            return;
        }

        VillageSnapshot snapshot = analyzeVillage(chunk);
        if (snapshot == null || snapshot.bedLocations().isEmpty()) {
            return;
        }

        int minPopulation = Math.max(2, plugin.getConfig().getInt("villagers.auto_repopulate.min_population", 6));
        int maxPopulation = Math.max(minPopulation, plugin.getConfig().getInt("villagers.auto_repopulate.max_population", 12));
        int maxNewPerCycle = Math.max(1, plugin.getConfig().getInt("villagers.auto_repopulate.max_new_per_cycle", 2));
        int desiredPopulation = Math.max(minPopulation, Math.min(snapshot.bedLocations().size(), maxPopulation));
        if (snapshot.villagerCount() >= desiredPopulation) {
            return;
        }

        long cooldownMillis = Math.max(30L, plugin.getConfig().getLong("villagers.auto_repopulate.cooldown_seconds", 180L)) * 1000L;
        long now = System.currentTimeMillis();
        String villageKey = buildVillageKey(snapshot.center());
        Long lastSpawn = villagePopulationCooldowns.get(villageKey);
        if (lastSpawn != null && now - lastSpawn < cooldownMillis) {
            return;
        }

        int missingVillagers = desiredPopulation - snapshot.villagerCount();
        int spawnCount = Math.min(missingVillagers, maxNewPerCycle);
        int spawned = 0;

        for (int i = 0; i < spawnCount; i++) {
            Location spawnLocation = findVillageSpawnLocation(snapshot, i);
            if (spawnLocation == null) {
                break;
            }

            Villager villager = spawnNaturalVillageVillager(spawnLocation);
            if (villager == null) {
                continue;
            }

            ensureVillagerIsNPC(villager);
            refreshVillagerProfile(villager);
            spawned++;
        }

        if (spawned > 0) {
            villagePopulationCooldowns.put(villageKey, now);
            plugin.debug("Am repopulat satul din " + formatLocation(snapshot.center()) + " cu " + spawned + " villager(i).");
        }
    }

    /**
     * Sterge un NPC
     */
    public boolean deleteNPC(AINPC npc) {
        npc.despawn();

        String sql = "DELETE FROM npcs WHERE id = ?";

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            stmt.setInt(1, npc.getDatabaseId());
            stmt.executeUpdate();

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
     * Reface indexul dupa schimbarea UUID-ului NPC-ului.
     */
    private void refreshNpcCache(AINPC npc, UUID previousUuid) {
        if (previousUuid != null && !previousUuid.equals(npc.getUuid())) {
            npcsByUuid.remove(previousUuid);
        }
        registerNPC(npc);
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

    public void handleEntityDeath(Entity entity) {
        AINPC npc = getNPCByEntity(entity);
        if (npc == null) {
            return;
        }

        npcsByEntityId.remove(entity.getUniqueId());
        npc.markEntityUnavailable();
        plugin.debug("NPC '" + npc.getName() + "' a ramas fara entitate activa dupa moarte.");
    }

    private void attachLoadedNPC(AINPC npc) {
        attachLoadedNPC(npc, null);
    }

    private void attachLoadedNPC(AINPC npc, Chunk preferredChunk) {
        UUID previousUuid = npc.getUuid();
        if (preferredChunk == null && !isChunkLoaded(npc)) {
            plugin.debug("NPC '" + npc.getName() + "' asteapta incarcarea chunk-ului pentru restaurare.");
            return;
        }

        Villager villager = findVillagerForNPC(npc, preferredChunk);

        if (villager != null) {
            npc.attachToVillager(villager);
            registerEntity(npc, villager);
        } else {
            AINPC equivalentActiveNpc = findEquivalentActiveNPC(npc);
            if (equivalentActiveNpc != null) {
                plugin.getLogger().warning("Sar peste spawn pentru NPC-ul '" + npc.getName()
                    + "' deoarece exista deja un NPC activ echivalent: id=" + equivalentActiveNpc.getDatabaseId()
                    + ", nume=" + equivalentActiveNpc.getName() + ".");
                return;
            }

            if (npc.spawn() && npc.getBukkitEntity() != null) {
                registerEntity(npc, npc.getBukkitEntity());
            }
        }

        if (!Objects.equals(previousUuid, npc.getUuid())) {
            refreshNpcCache(npc, previousUuid);
            saveNPC(npc);
        }
    }

    private boolean isChunkLoaded(AINPC npc) {
        World world = plugin.getServer().getWorld(npc.getWorldName());
        if (world == null) {
            return false;
        }

        int chunkX = floorToBlock(npc.getX()) >> 4;
        int chunkZ = floorToBlock(npc.getZ()) >> 4;
        return world.isChunkLoaded(chunkX, chunkZ);
    }

    private boolean belongsToChunk(AINPC npc, Chunk chunk) {
        if (npc.getWorldName() == null || chunk.getWorld() == null) {
            return false;
        }

        if (!npc.getWorldName().equals(chunk.getWorld().getName())) {
            return false;
        }

        int chunkX = floorToBlock(npc.getX()) >> 4;
        int chunkZ = floorToBlock(npc.getZ()) >> 4;
        return chunk.getX() == chunkX && chunk.getZ() == chunkZ;
    }

    private int floorToBlock(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    private Villager findVillagerForNPC(AINPC npc, Chunk preferredChunk) {
        Villager exactMatch = preferredChunk == null
            ? findVillagerByUuid(npc.getUuid())
            : findVillagerByUuid(preferredChunk, npc.getUuid());
        if (exactMatch != null) {
            return exactMatch;
        }

        return preferredChunk == null
            ? findLegacyVillager(npc)
            : findLegacyVillager(npc, preferredChunk);
    }

    private Villager findVillagerByUuid(UUID uuid) {
        for (World world : plugin.getServer().getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (villager.getUniqueId().equals(uuid)) {
                    return villager;
                }
            }
        }
        return null;
    }

    private Villager findVillagerByUuid(Chunk chunk, UUID uuid) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Villager villager && villager.getUniqueId().equals(uuid)) {
                return villager;
            }
        }
        return null;
    }

    private Villager findLegacyVillager(AINPC npc) {
        Location expectedLocation = npc.getLocation();
        if (expectedLocation == null || expectedLocation.getWorld() == null) {
            return null;
        }

        for (Villager villager : expectedLocation.getWorld().getEntitiesByClass(Villager.class)) {
            if (!isLegacyPluginVillager(villager)) {
                continue;
            }

            if (!isSameNpcLocation(expectedLocation, villager.getLocation())) {
                continue;
            }

            String villagerName = getVillagerDisplayName(villager);
            if (namesMatch(npc.getDisplayName(), villagerName) || namesMatch(npc.getName(), villagerName)) {
                return villager;
            }
        }

        return null;
    }

    private Villager findLegacyVillager(AINPC npc, Chunk chunk) {
        Location expectedLocation = npc.getLocation();
        if (expectedLocation == null || expectedLocation.getWorld() == null) {
            return null;
        }

        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof Villager villager)) {
                continue;
            }

            if (!isLegacyPluginVillager(villager)) {
                continue;
            }

            if (!isSameNpcLocation(expectedLocation, villager.getLocation())) {
                continue;
            }

            String villagerName = getVillagerDisplayName(villager);
            if (namesMatch(npc.getDisplayName(), villagerName) || namesMatch(npc.getName(), villagerName)) {
                return villager;
            }
        }

        return null;
    }

    private AINPC findLegacyNPCForVillager(Villager villager) {
        if (!isLegacyPluginVillager(villager)) {
            return null;
        }

        String villagerName = getVillagerDisplayName(villager);
        Location villagerLocation = villager.getLocation();

        for (AINPC npc : npcsByUuid.values()) {
            if (npc.getBukkitEntity() != null && npc.getBukkitEntity().isValid()) {
                continue;
            }

            Location npcLocation = npc.getLocation();
            if (!isSameNpcLocation(npcLocation, villagerLocation)) {
                continue;
            }

            if (namesMatch(npc.getDisplayName(), villagerName) || namesMatch(npc.getName(), villagerName)) {
                return npc;
            }
        }

        return null;
    }

    private boolean isLegacyPluginVillager(Villager villager) {
        return !villager.hasAI() || villager.isInvulnerable() || villager.isSilent();
    }

    private boolean isSameNpcLocation(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) {
            return false;
        }

        if (!first.getWorld().equals(second.getWorld())) {
            return false;
        }

        return first.distanceSquared(second) <= 2.25D;
    }

    private boolean namesMatch(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }

        return expected.equalsIgnoreCase(actual);
    }

    private AINPC findEquivalentActiveNPC(AINPC target) {
        Location targetLocation = target.getLocation();
        if (targetLocation == null) {
            return null;
        }

        for (AINPC candidate : npcsByUuid.values()) {
            if (candidate == target || !candidate.isSpawned()) {
                continue;
            }

            Location candidateLocation = candidate.getLocation();
            if (!isSameNpcLocation(targetLocation, candidateLocation)) {
                continue;
            }

            if (namesMatch(target.getName(), candidate.getName())
                || namesMatch(target.getDisplayName(), candidate.getDisplayName())
                || namesMatch(target.getName(), candidate.getDisplayName())
                || namesMatch(target.getDisplayName(), candidate.getName())) {
                return candidate;
            }
        }

        return null;
    }

    private AINPC findEquivalentActiveNPC(Villager villager) {
        if (villager == null) {
            return null;
        }

        Location villagerLocation = villager.getLocation();
        String villagerName = getVillagerDisplayName(villager);

        for (AINPC candidate : npcsByUuid.values()) {
            if (!candidate.isSpawned()) {
                continue;
            }

            Entity candidateEntity = candidate.getBukkitEntity();
            if (candidateEntity != null && candidateEntity.getUniqueId().equals(villager.getUniqueId())) {
                continue;
            }

            Location candidateLocation = candidate.getLocation();
            if (!isSameNpcLocation(villagerLocation, candidateLocation)) {
                continue;
            }

            if (namesMatch(candidate.getName(), villagerName)
                || namesMatch(candidate.getDisplayName(), villagerName)) {
                return candidate;
            }
        }

        return null;
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "<locatie necunoscuta>";
        }

        return location.getWorld().getName()
            + " "
            + floorToBlock(location.getX()) + ","
            + floorToBlock(location.getY()) + ","
            + floorToBlock(location.getZ());
    }

    private VillageSnapshot analyzeVillage(Chunk chunk) {
        List<Location> anchors = new ArrayList<>();
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Villager villager && villager.isAdult()) {
                anchors.add(villager.getLocation());
            }
        }

        if (anchors.isEmpty()) {
            return null;
        }

        Location center = averageLocation(anchors);
        int radius = Math.max(12, plugin.getConfig().getInt("villagers.auto_repopulate.scan_radius", 24));
        int verticalRadius = Math.max(4, plugin.getConfig().getInt("villagers.auto_repopulate.vertical_radius", 8));
        List<Location> bedLocations = findBedLocations(center, radius, verticalRadius);
        if (bedLocations.isEmpty()) {
            return null;
        }

        int villagerCount = (int) center.getWorld().getNearbyEntities(
            center,
            radius,
            verticalRadius,
            radius,
            entity -> entity instanceof Villager
        ).size();

        return new VillageSnapshot(center, bedLocations, villagerCount);
    }

    private Location averageLocation(List<Location> locations) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        World world = locations.get(0).getWorld();

        for (Location location : locations) {
            x += location.getX();
            y += location.getY();
            z += location.getZ();
        }

        int count = locations.size();
        return new Location(world, x / count, y / count, z / count);
    }

    private List<Location> findBedLocations(Location center, int radius, int verticalRadius) {
        if (center == null || center.getWorld() == null) {
            return Collections.emptyList();
        }

        Set<String> seenBeds = new HashSet<>();
        List<Location> beds = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block block = center.getWorld().getBlockAt(
                        floorToBlock(center.getX()) + dx,
                        floorToBlock(center.getY()) + dy,
                        floorToBlock(center.getZ()) + dz
                    );

                    if (!Tag.BEDS.isTagged(block.getType())) {
                        continue;
                    }

                    BlockData blockData = block.getBlockData();
                    if (blockData instanceof Bed bedData && bedData.getPart() != Bed.Part.HEAD) {
                        continue;
                    }

                    String key = block.getX() + ":" + block.getY() + ":" + block.getZ();
                    if (seenBeds.add(key)) {
                        beds.add(block.getLocation());
                    }
                }
            }
        }

        return beds;
    }

    private Location findVillageSpawnLocation(VillageSnapshot snapshot, int offset) {
        List<Location> beds = snapshot.bedLocations();
        if (beds.isEmpty()) {
            return null;
        }

        Location bed = beds.get(offset % beds.size());
        World world = bed.getWorld();
        if (world == null) {
            return null;
        }

        int[][] candidates = new int[][]{
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}
        };

        for (int[] candidate : candidates) {
            Location spawn = bed.clone().add(candidate[0] + 0.5, 1.0, candidate[1] + 0.5);
            Block feet = world.getBlockAt(floorToBlock(spawn.getX()), floorToBlock(spawn.getY()), floorToBlock(spawn.getZ()));
            Block head = world.getBlockAt(floorToBlock(spawn.getX()), floorToBlock(spawn.getY()) + 1, floorToBlock(spawn.getZ()));
            Block ground = world.getBlockAt(floorToBlock(spawn.getX()), floorToBlock(spawn.getY()) - 1, floorToBlock(spawn.getZ()));
            if (feet.isPassable() && head.isPassable() && !ground.isPassable()) {
                return spawn;
            }
        }

        return snapshot.center().clone().add(0.5, 0.0, 0.5);
    }

    private Villager spawnNaturalVillageVillager(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        try {
            return location.getWorld().spawn(location, Villager.class, villager -> {
                villager.setAdult();
                villager.setProfession(Villager.Profession.NONE);
                villager.setVillagerType(Villager.Type.PLAINS);
                villager.setPersistent(true);
                villager.setRemoveWhenFarAway(false);
            });
        } catch (Exception exception) {
            plugin.getLogger().warning("Nu am putut genera un villager nou pentru sat la "
                + formatLocation(location) + ": " + exception.getMessage());
            return null;
        }
    }

    private String buildVillageKey(Location center) {
        if (center == null || center.getWorld() == null) {
            return "unknown";
        }

        int coarseX = floorToBlock(center.getX()) >> 5;
        int coarseZ = floorToBlock(center.getZ()) >> 5;
        return center.getWorld().getName() + ":" + coarseX + ":" + coarseZ;
    }

    private String getVillagerDisplayName(Villager villager) {
        Component customName = villager.customName();
        if (customName == null) {
            return null;
        }

        String plainName = PLAIN_TEXT.serialize(customName);
        return plainName == null ? null : plainName.trim();
    }

    private record VillageSnapshot(Location center, List<Location> bedLocations, int villagerCount) {
    }

    private AINPC createAutoProfile(Villager villager) {
        AINPC npc = new AINPC(plugin);
        applyAutoProfile(npc, villager);
        npc.attachToVillager(villager);

        if (!saveNPC(npc)) {
            return null;
        }

        plugin.debug("Villager-ul " + npc.getName() + " a primit profil AI automat.");
        return npc;
    }

    private void applyAutoProfile(AINPC npc, Villager villager) {
        Random random = createVillagerSeededRandom(villager);
        String gender = random.nextBoolean() ? "male" : "female";
        String occupation = resolveOccupationForVillager(villager, random);
        String name = getVillagerDisplayName(villager);

        if (name == null || name.isBlank()) {
            name = NPCNameGenerator.randomName(gender, random);
        }

        Location location = villager.getLocation();
        npc.setUuid(villager.getUniqueId());
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
        npc.setAge(villager.isAdult() ? 18 + random.nextInt(43) : 8 + random.nextInt(8));
        npc.setGender(gender);
        npc.setBackstory(generateBackstory(name, occupation, villager.getProfession()));
        npc.setPersonality(generatePersonalityForOccupation(occupation, villager.getProfession()));
        npc.setProfileSource("auto");
        applyThemeDefaults(npc);
    }

    private Random createVillagerSeededRandom(Villager villager) {
        return new Random(villager.getUniqueId().getMostSignificantBits() ^ villager.getUniqueId().getLeastSignificantBits());
    }

    private void applyThemeDefaults(AINPC npc) {
        if (npc == null || plugin.getFeaturePackLoader() == null) {
            return;
        }

        FeaturePackLoader.ProfessionDefinition profession =
            plugin.getFeaturePackLoader().findPrimaryScenarioProfession(npc.getOccupation());
        if (profession == null || profession.getSuggestedTraits().isEmpty()) {
            return;
        }

        if (npc.getTraits() != null && !npc.getTraits().isEmpty()) {
            return;
        }

        List<String> candidates = new ArrayList<>(profession.getSuggestedTraits());
        Random random = new Random(
            npc.getUuid().getMostSignificantBits()
                ^ npc.getUuid().getLeastSignificantBits()
                ^ profession.getId().hashCode()
        );
        Collections.shuffle(candidates, random);

        int traitsToAssign = Math.min(2, candidates.size());
        for (int i = 0; i < traitsToAssign; i++) {
            npc.addTrait(candidates.get(i));
        }
    }

    private String resolveOccupationForVillager(Villager villager, Random random) {
        String mappedOccupation = mapProfessionToOccupation(villager.getProfession());
        if (!isGenericOccupation(mappedOccupation)) {
            return mappedOccupation;
        }

        String inferredOccupation = inferOccupationFromEnvironment(villager);
        if (inferredOccupation != null && !inferredOccupation.isBlank()) {
            return inferredOccupation;
        }

        String themedOccupation = inferOccupationFromPrimaryScenario(random);
        if (themedOccupation != null && !themedOccupation.isBlank()) {
            return themedOccupation;
        }

        return mappedOccupation;
    }

    private String inferOccupationFromEnvironment(Villager villager) {
        Material workstation = findNearbyWorkstation(villager.getLocation(), 4, 2);
        if (workstation == null) {
            return null;
        }

        return switch (workstation) {
            case COMPOSTER -> "fermier";
            case BLAST_FURNACE, SMITHING_TABLE, ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL, GRINDSTONE -> "fierar";
            case BARREL, SMOKER, CAMPFIRE -> "hangiu";
            case BREWING_STAND, CAULDRON -> "vindecator";
            case LECTERN -> "preot";
            case CARTOGRAPHY_TABLE -> "cartograf";
            case STONECUTTER -> "pietrar";
            case FLETCHING_TABLE -> "tamplar";
            case BELL, CHEST -> "negustor";
            default -> null;
        };
    }

    private Material findNearbyWorkstation(Location center, int horizontalRadius, int verticalRadius) {
        if (center == null || center.getWorld() == null) {
            return null;
        }

        Map<Material, Integer> materialWeights = new HashMap<>();
        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    Block block = center.getWorld().getBlockAt(
                        floorToBlock(center.getX()) + dx,
                        floorToBlock(center.getY()) + dy,
                        floorToBlock(center.getZ()) + dz
                    );
                    Material type = block.getType();
                    if (!isWorkstation(type)) {
                        continue;
                    }
                    materialWeights.merge(type, 1, Integer::sum);
                }
            }
        }

        return materialWeights.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    private boolean isWorkstation(Material material) {
        return material == Material.COMPOSTER
            || material == Material.BLAST_FURNACE
            || material == Material.SMITHING_TABLE
            || material == Material.ANVIL
            || material == Material.CHIPPED_ANVIL
            || material == Material.DAMAGED_ANVIL
            || material == Material.GRINDSTONE
            || material == Material.BARREL
            || material == Material.SMOKER
            || material == Material.CAMPFIRE
            || material == Material.BREWING_STAND
            || material == Material.CAULDRON
            || material == Material.LECTERN
            || material == Material.CARTOGRAPHY_TABLE
            || material == Material.STONECUTTER
            || material == Material.FLETCHING_TABLE
            || material == Material.BELL
            || material == Material.CHEST;
    }

    private String inferOccupationFromPrimaryScenario(Random random) {
        if (plugin.getFeaturePackLoader() == null) {
            return null;
        }

        FeaturePackLoader.FeaturePack pack = plugin.getFeaturePackLoader().getPrimaryScenarioPack();
        if (pack == null || pack.getProfessions().isEmpty()) {
            return null;
        }

        List<FeaturePackLoader.ProfessionDefinition> professions = new ArrayList<>(pack.getProfessions());
        Collections.shuffle(professions, random);
        return professions.get(0).getName().toLowerCase(Locale.ROOT);
    }

    private NPCPersonality generatePersonalityForProfession(Villager.Profession profession) {
        if (profession == Villager.Profession.FARMER ||
            profession == Villager.Profession.SHEPHERD ||
            profession == Villager.Profession.BUTCHER) {
            return NPCPersonality.fromArchetype("caregiver");
        }

        if (profession == Villager.Profession.LIBRARIAN ||
            profession == Villager.Profession.CLERIC) {
            return NPCPersonality.fromArchetype("sage");
        }

        if (profession == Villager.Profession.CARTOGRAPHER ||
            profession == Villager.Profession.FISHERMAN) {
            return NPCPersonality.fromArchetype("explorer");
        }

        if (profession == Villager.Profession.ARMORER ||
            profession == Villager.Profession.WEAPONSMITH) {
            return NPCPersonality.fromArchetype("warrior");
        }

        if (profession == Villager.Profession.TOOLSMITH ||
            profession == Villager.Profession.FLETCHER ||
            profession == Villager.Profession.LEATHERWORKER ||
            profession == Villager.Profession.MASON) {
            return NPCPersonality.fromArchetype("creator");
        }

        if (profession == Villager.Profession.NITWIT) {
            return NPCPersonality.fromArchetype("jester");
        }

        return NPCPersonality.generateRandom();
    }

    private NPCPersonality generatePersonalityForOccupation(String occupation, Villager.Profession profession) {
        if (occupation == null || occupation.isBlank() || isGenericOccupation(occupation)) {
            return generatePersonalityForProfession(profession);
        }

        return switch (occupation.toLowerCase(Locale.ROOT)) {
            case "fermier", "pastor", "macelar" -> NPCPersonality.fromArchetype("caregiver");
            case "preot", "vindecator", "bibliotecar" -> NPCPersonality.fromArchetype("sage");
            case "cartograf", "pescar" -> NPCPersonality.fromArchetype("explorer");
            case "fierar", "tamplar", "pietrar", "miner", "mestesugar" -> NPCPersonality.fromArchetype("creator");
            case "soldat", "garda" -> NPCPersonality.fromArchetype("warrior");
            case "negustor", "hangiu" -> NPCPersonality.fromArchetype("merchant");
            case "localnic" -> NPCPersonality.fromArchetype("innocent");
            default -> generatePersonalityForProfession(profession);
        };
    }

    private boolean isGenericOccupation(String occupation) {
        if (occupation == null || occupation.isBlank()) {
            return true;
        }

        String normalized = occupation.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("locuitor")
            || normalized.equals("villager")
            || normalized.equals("resident")
            || normalized.equals("localnic");
    }

    private String mapProfessionToOccupation(Villager.Profession profession) {
        if (profession == Villager.Profession.FARMER) return "fermier";
        if (profession == Villager.Profession.LIBRARIAN) return "bibliotecar";
        if (profession == Villager.Profession.CLERIC) return "preot";
        if (profession == Villager.Profession.ARMORER) return "fierar";
        if (profession == Villager.Profession.BUTCHER) return "macelar";
        if (profession == Villager.Profession.FISHERMAN) return "pescar";
        if (profession == Villager.Profession.CARTOGRAPHER) return "cartograf";
        if (profession == Villager.Profession.MASON) return "pietrar";
        if (profession == Villager.Profession.FLETCHER) return "tamplar";
        if (profession == Villager.Profession.TOOLSMITH) return "miner";
        if (profession == Villager.Profession.WEAPONSMITH) return "soldat";
        if (profession == Villager.Profession.LEATHERWORKER) return "mestesugar";
        if (profession == Villager.Profession.SHEPHERD) return "pastor";
        if (profession == Villager.Profession.NITWIT) return "localnic";
        return "locuitor";
    }

    private String generateBackstory(String name, String occupation, Villager.Profession profession) {
        if (profession == Villager.Profession.FARMER) {
            return name + " lucreaza zilnic campurile satului si stie cand vine vremea recoltei.";
        }

        if (profession == Villager.Profession.LIBRARIAN) {
            return name + " a invatat din multe carti si iubeste povestile vechi ale regiunii.";
        }

        if (profession == Villager.Profession.CLERIC) {
            return name + " tine minte ritualurile satului si ofera sfaturi celor nelinistiti.";
        }

        if (profession == Villager.Profession.ARMORER ||
            profession == Villager.Profession.WEAPONSMITH ||
            profession == Villager.Profession.TOOLSMITH) {
            return name + " isi petrece zilele la atelier, modeland fierul pentru comunitate.";
        }

        if (profession == Villager.Profession.CARTOGRAPHER) {
            return name + " deseneaza drumuri, ruine si locuri uitate pentru cei care pleaca in aventura.";
        }

        if (profession == Villager.Profession.FISHERMAN) {
            return name + " cunoaste raul, lacul si vremea mai bine decat oricine altcineva.";
        }

        if (profession == Villager.Profession.BUTCHER) {
            return name + " se ocupa de proviziile satului si observa rapid orice lipsa din piata.";
        }

        if (profession == Villager.Profession.MASON ||
            profession == Villager.Profession.FLETCHER ||
            profession == Villager.Profession.LEATHERWORKER ||
            profession == Villager.Profession.SHEPHERD) {
            return name + " este un " + occupation + " respectat si isi ia meseria in serios.";
        }

        return name + " este mereu prin sat, ascultand zvonuri si observand tot ce se intampla.";
    }

    private String buildProfileSummary(AINPC npc) {
        List<String> parts = new ArrayList<>();
        String displayName = npc.getName() != null && !npc.getName().isBlank() ? npc.getName() : "Acest NPC";
        String occupation = npc.getOccupation() == null || npc.getOccupation().isBlank()
            ? "locuitor"
            : npc.getOccupation();

        parts.add(displayName + " este " + occupation);

        if (npc.getAge() > 0) {
            parts.add(npc.getAge() + " ani");
        }

        if (npc.getGender() != null && !npc.getGender().isBlank()) {
            parts.add(npc.getGender().equalsIgnoreCase("female") ? "femeie" : "barbat");
        }

        String traits = npc.getPersonality() != null ? npc.getPersonality().getDominantTraits() : "";
        if (traits != null && !traits.isBlank() && !"echilibrat".equalsIgnoreCase(traits)) {
            parts.add("trasaturi dominante: " + traits);
        }

        StringBuilder summary = new StringBuilder(String.join(", ", parts)).append(".");
        if (npc.getBackstory() != null && !npc.getBackstory().isBlank()) {
            summary.append(" ").append(truncateProfileText(npc.getBackstory(), 180));
        }

        return summary.toString();
    }

    private String buildProfileData(AINPC npc) {
        JsonObject profile = new JsonObject();
        profile.addProperty("npc_id", npc.getDatabaseId());
        profile.addProperty("uuid", npc.getUuid() != null ? npc.getUuid().toString() : "");
        profile.addProperty("name", npc.getName());
        profile.addProperty("display_name", npc.getDisplayName());
        profile.addProperty("profile_source", npc.getProfileSource());
        profile.addProperty("profile_version", npc.getProfileVersion());
        profile.addProperty("world", npc.getWorldName());
        profile.addProperty("x", npc.getX());
        profile.addProperty("y", npc.getY());
        profile.addProperty("z", npc.getZ());
        profile.addProperty("yaw", npc.getYaw());
        profile.addProperty("pitch", npc.getPitch());
        profile.addProperty("occupation", npc.getOccupation());
        profile.addProperty("backstory", npc.getBackstory());
        profile.addProperty("age", npc.getAge());
        profile.addProperty("gender", npc.getGender());
        profile.addProperty("current_state",
            npc.getCurrentState() != null ? npc.getCurrentState().name() : "");
        profile.addProperty("spawned", npc.isSpawned());
        profile.addProperty("profile_summary", buildProfileSummary(npc));

        JsonArray traitsArray = new JsonArray();
        if (npc.getTraits() != null) {
            for (String traitId : npc.getTraits()) {
                if (traitId != null && !traitId.isBlank()) {
                    traitsArray.add(traitId);
                }
            }
        }
        profile.add("traits", traitsArray);

        JsonObject personality = new JsonObject();
        if (npc.getPersonality() != null) {
            personality.addProperty("openness", npc.getPersonality().getOpenness());
            personality.addProperty("conscientiousness", npc.getPersonality().getConscientiousness());
            personality.addProperty("extraversion", npc.getPersonality().getExtraversion());
            personality.addProperty("agreeableness", npc.getPersonality().getAgreeableness());
            personality.addProperty("neuroticism", npc.getPersonality().getNeuroticism());
            personality.addProperty("dominant_traits", npc.getPersonality().getDominantTraits());
        }
        profile.add("personality", personality);

        JsonObject emotions = new JsonObject();
        if (npc.getEmotions() != null) {
            emotions.addProperty("happiness", npc.getEmotions().getHappiness());
            emotions.addProperty("sadness", npc.getEmotions().getSadness());
            emotions.addProperty("anger", npc.getEmotions().getAnger());
            emotions.addProperty("fear", npc.getEmotions().getFear());
            emotions.addProperty("surprise", npc.getEmotions().getSurprise());
            emotions.addProperty("disgust", npc.getEmotions().getDisgust());
            emotions.addProperty("trust", npc.getEmotions().getTrust());
            emotions.addProperty("anticipation", npc.getEmotions().getAnticipation());
            emotions.addProperty("short_description", npc.getEmotions().getShortDescription());
        }
        profile.add("emotions", emotions);

        return gson.toJson(profile);
    }

    private String truncateProfileText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }

        String truncated = text.substring(0, Math.max(0, maxLength - 3)).trim();
        if (truncated.endsWith(".")) {
            return truncated;
        }
        return truncated + "...";
    }

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
        if (entity == null) {
            return null;
        }

        AINPC npc = npcsByEntityId.get(entity.getUniqueId());
        if (npc != null) {
            return npc;
        }

        npc = npcsByUuid.get(entity.getUniqueId());
        if (npc != null) {
            npcsByEntityId.put(entity.getUniqueId(), npc);
            return npc;
        }

        for (AINPC candidate : npcsByUuid.values()) {
            if (candidate.getBukkitEntity() != null &&
                candidate.getBukkitEntity().getUniqueId().equals(entity.getUniqueId())) {
                npcsByEntityId.put(entity.getUniqueId(), candidate);
                return candidate;
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

    public List<AINPC> getActiveNPCsNear(Location location, double radius) {
        List<AINPC> nearby = new ArrayList<>();

        for (AINPC npc : npcsByUuid.values()) {
            if (!npc.isSpawned()) {
                continue;
            }

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
