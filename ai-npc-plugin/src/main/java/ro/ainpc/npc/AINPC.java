package ro.ainpc.npc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import ro.ainpc.AINPCPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reprezinta un NPC cu inteligenta artificiala
 */
public class AINPC {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private final AINPCPlugin plugin;
    
    // Identificare
    private int databaseId;
    private UUID uuid;
    private String name;
    private String displayName;
    
    // Locatie
    private String worldName;
    private double x, y, z;
    private float yaw, pitch;
    
    // Aspect
    private String skinTexture;
    private String skinSignature;
    
    // Caracteristici
    private String backstory;
    private String occupation;
    private int age;
    private String gender;
    
    // Componente
    private NPCPersonality personality;
    private NPCEmotions emotions;
    private NPCContext context;
    private NPCState currentState;
    
    // Traits din Feature Packs
    private List<String> traits;
    
    // Entitate Bukkit
    private Entity bukkitEntity;
    private boolean spawned;

    public AINPC(AINPCPlugin plugin) {
        this.plugin = plugin;
        this.uuid = UUID.randomUUID();
        this.personality = new NPCPersonality();
        this.emotions = new NPCEmotions();
        this.context = new NPCContext(this);
        this.currentState = NPCState.IDLE;
        this.traits = new ArrayList<>();
        this.spawned = false;
        this.age = 30;
        this.gender = "male";
    }

    /**
     * Spawneaza NPC-ul in lume
     */
    public boolean spawn() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Lumea '" + worldName + "' nu a fost gasita pentru NPC: " + name);
            return false;
        }

        Location location = new Location(world, x, y, z, yaw, pitch);
        
        // Folosim Villager ca baza pentru NPC
        bukkitEntity = world.spawn(location, Villager.class, villager -> {
            villager.customName(getColoredDisplayNameComponent());
            villager.setCustomNameVisible(true);
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setCollidable(false);
            villager.setPersistent(true);
            villager.setRemoveWhenFarAway(false);
            
            // Seteaza profesia bazata pe ocupatie
            villager.setProfession(getVillagerProfession());
            villager.setVillagerType(Villager.Type.PLAINS);
        });

        spawned = true;
        plugin.debug("NPC '" + name + "' spawnat la " + location);
        return true;
    }

    /**
     * Despawneaza NPC-ul
     */
    public void despawn() {
        if (bukkitEntity != null && bukkitEntity.isValid()) {
            bukkitEntity.remove();
        }
        bukkitEntity = null;
        spawned = false;
        plugin.debug("NPC '" + name + "' despawnat.");
    }

    /**
     * Teleporteaza NPC-ul la o noua locatie
     */
    public void teleport(Location location) {
        this.worldName = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();

        if (spawned && bukkitEntity != null) {
            bukkitEntity.teleport(location);
        }
    }

    /**
     * Face NPC-ul sa se uite la un jucator
     */
    public void lookAt(Player player) {
        if (bukkitEntity == null || !spawned) return;

        Location npcLoc = bukkitEntity.getLocation();
        Location playerLoc = player.getLocation();

        double dx = playerLoc.getX() - npcLoc.getX();
        double dy = playerLoc.getY() - npcLoc.getY();
        double dz = playerLoc.getZ() - npcLoc.getZ();

        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, distXZ));

        Location newLoc = npcLoc.clone();
        newLoc.setYaw(yaw);
        newLoc.setPitch(pitch);

        bukkitEntity.teleport(newLoc);
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /**
     * Verifica daca un jucator este in raza de interactiune
     */
    public boolean isInRange(Player player) {
        if (!spawned || bukkitEntity == null) return false;
        
        double maxDistance = plugin.getConfig().getDouble("npc.interaction_distance", 5.0);
        return bukkitEntity.getLocation().distance(player.getLocation()) <= maxDistance;
    }

    /**
     * Obtine locatia curenta
     */
    public Location getLocation() {
        if (bukkitEntity != null && spawned) {
            return bukkitEntity.getLocation();
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return new Location(world, x, y, z, yaw, pitch);
        }
        return null;
    }

    /**
     * Converteste ocupatia in profesie de Villager
     */
    private Villager.Profession getVillagerProfession() {
        if (occupation == null) return Villager.Profession.NONE;
        
        return switch (occupation.toLowerCase()) {
            case "fermier", "farmer" -> Villager.Profession.FARMER;
            case "bibliotecar", "librarian" -> Villager.Profession.LIBRARIAN;
            case "preot", "cleric" -> Villager.Profession.CLERIC;
            case "fierar", "blacksmith", "armorer" -> Villager.Profession.ARMORER;
            case "macelar", "butcher" -> Villager.Profession.BUTCHER;
            case "pescar", "fisherman" -> Villager.Profession.FISHERMAN;
            case "cartograf", "cartographer" -> Villager.Profession.CARTOGRAPHER;
            case "pietrar", "mason" -> Villager.Profession.MASON;
            case "tamplar", "fletcher" -> Villager.Profession.FLETCHER;
            case "alchimist", "brewer" -> Villager.Profession.CLERIC;
            case "soldat", "soldier", "guard" -> Villager.Profession.WEAPONSMITH;
            case "miner" -> Villager.Profession.TOOLSMITH;
            case "negustor", "merchant" -> Villager.Profession.MASON;
            default -> Villager.Profession.NITWIT;
        };
    }

    /**
     * Obtine numele colorat pentru afisare
     */
    public String getColoredDisplayName() {
        String emotionColor = emotions.getDominantEmotionColor();
        String nameToShow = displayName != null ? displayName : name;
        return emotionColor + nameToShow;
    }

    public Component getColoredDisplayNameComponent() {
        return LEGACY_SERIALIZER.deserialize(getColoredDisplayName());
    }

    /**
     * Actualizeaza numele afisat bazat pe emotie
     */
    public void updateDisplayName() {
        if (bukkitEntity != null && spawned) {
            bukkitEntity.customName(getColoredDisplayNameComponent());
        }
    }

    /**
     * Schimba starea NPC-ului (State Machine)
     */
    public boolean changeState(NPCState newState) {
        // Verifica daca tranzitia e permisa bazat pe prioritate
        if (currentState.getPriority() > newState.getPriority() && 
            currentState.getPriority() >= 60) {
            // Nu poate intrerupe stari cu prioritate mare
            plugin.debug("NPC " + name + " nu poate trece din " + currentState + " la " + newState);
            return false;
        }
        
        NPCState oldState = this.currentState;
        this.currentState = newState;
        
        plugin.debug("NPC " + name + " stare: " + oldState + " -> " + newState);
        return true;
    }

    /**
     * Actualizeaza contextul NPC-ului
     */
    public void updateContext() {
        if (bukkitEntity != null && spawned) {
            context.updateFromWorld(bukkitEntity.getWorld(), bukkitEntity.getLocation());
        }
    }

    /**
     * Adauga un trait NPC-ului
     */
    public void addTrait(String traitId) {
        if (!traits.contains(traitId)) {
            traits.add(traitId);
        }
    }

    /**
     * Sterge un trait
     */
    public void removeTrait(String traitId) {
        traits.remove(traitId);
    }

    /**
     * Verifica daca NPC-ul are un trait
     */
    public boolean hasTrait(String traitId) {
        return traits.contains(traitId);
    }

    /**
     * Genereaza o descriere completa a NPC-ului pentru AI
     */
    public String generateContextDescription() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("Nume: ").append(name).append("\n");
        sb.append("Varsta: ").append(age).append(" ani\n");
        sb.append("Gen: ").append(gender.equals("male") ? "Barbat" : "Femeie").append("\n");
        
        if (occupation != null && !occupation.isEmpty()) {
            sb.append("Ocupatie: ").append(occupation).append("\n");
        }
        
        if (backstory != null && !backstory.isEmpty()) {
            sb.append("Poveste: ").append(backstory).append("\n");
        }
        
        sb.append("\nPersonalitate:\n");
        sb.append(personality.getDescription()).append("\n");
        
        sb.append("\nStare emotionala curenta:\n");
        sb.append(emotions.getDescription()).append("\n");
        
        sb.append("\nStare curenta: ").append(currentState.getDisplayName()).append("\n");
        
        if (!traits.isEmpty()) {
            sb.append("\nTrasaturi speciale: ").append(String.join(", ", traits)).append("\n");
        }
        
        return sb.toString();
    }

    // Getters si Setters

    public int getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(int databaseId) {
        this.databaseId = databaseId;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public String getSkinTexture() {
        return skinTexture;
    }

    public void setSkinTexture(String skinTexture) {
        this.skinTexture = skinTexture;
    }

    public String getSkinSignature() {
        return skinSignature;
    }

    public void setSkinSignature(String skinSignature) {
        this.skinSignature = skinSignature;
    }

    public String getBackstory() {
        return backstory;
    }

    public void setBackstory(String backstory) {
        this.backstory = backstory;
    }

    public String getOccupation() {
        return occupation;
    }

    public void setOccupation(String occupation) {
        this.occupation = occupation;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public NPCPersonality getPersonality() {
        return personality;
    }

    public void setPersonality(NPCPersonality personality) {
        this.personality = personality;
    }

    public NPCEmotions getEmotions() {
        return emotions;
    }

    public void setEmotions(NPCEmotions emotions) {
        this.emotions = emotions;
    }

    public NPCContext getContext() {
        return context;
    }

    public NPCState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(NPCState currentState) {
        this.currentState = currentState;
    }

    public List<String> getTraits() {
        return traits;
    }

    public void setTraits(List<String> traits) {
        this.traits = traits;
    }

    public Entity getBukkitEntity() {
        return bukkitEntity;
    }

    public boolean isSpawned() {
        return spawned;
    }

    public void setLocation(String worldName, double x, double y, double z, float yaw, float pitch) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }
}
