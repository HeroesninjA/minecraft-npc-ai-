package ro.ainpc.npc;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Contextul curent al unui NPC - informatii despre mediu si situatie
 * Folosit pentru luarea deciziilor si generarea dialogului
 */
public class NPCContext {

    // NPC-ul asociat
    private final AINPC npc;
    
    // Timp si locatie
    private long worldTime;
    private String timeOfDay; // MORNING, AFTERNOON, EVENING, NIGHT
    private String weather; // CLEAR, RAIN, THUNDER, SNOW
    private String biome;
    private boolean isIndoors;
    
    // Entitati din apropiere
    private List<Player> nearbyPlayers;
    private List<AINPC> nearbyNPCs;
    private int nearbyHostileMobs;
    private int nearbyPassiveMobs;
    
    // Interactiune curenta
    private Player interactingPlayer;
    private AINPC interactingNPC;
    private String lastPlayerMessage;
    private long lastInteractionTime;
    
    // Stare fizica
    private double healthPercent;
    private int hungerLevel;
    private boolean isHurt;
    private boolean isInDanger;
    
    // Stare sociala
    private boolean isAtHome;
    private boolean isAtWork;
    private boolean isFamilyNearby;
    private boolean isFriendsNearby;
    
    // Evenimente recente
    private List<String> recentEvents;
    private String lastSignificantEvent;
    
    // Informatii despre relatia cu jucatorul curent
    private int relationshipLevel;
    private String relationshipStatus; // STRANGER, ACQUAINTANCE, FRIEND, CLOSE_FRIEND, ENEMY
    private List<String> sharedMemories;

    public NPCContext(AINPC npc) {
        this.npc = npc;
        this.nearbyPlayers = new ArrayList<>();
        this.nearbyNPCs = new ArrayList<>();
        this.recentEvents = new ArrayList<>();
        this.sharedMemories = new ArrayList<>();
        this.healthPercent = 100.0;
        this.hungerLevel = 100;
        this.relationshipStatus = "STRANGER";
    }

    /**
     * Actualizeaza contextul bazat pe lumea curenta
     */
    public void updateFromWorld(World world, Location npcLocation) {
        if (world == null || npcLocation == null) return;
        
        // Timp
        this.worldTime = world.getTime();
        this.timeOfDay = calculateTimeOfDay(worldTime);
        
        // Vreme
        if (world.hasStorm()) {
            this.weather = world.isThundering() ? "THUNDER" : "RAIN";
        } else {
            this.weather = "CLEAR";
        }
        
        // Biom
        this.biome = npcLocation.getBlock().getBiome().toString().toUpperCase(Locale.ROOT);
        
        // Verifica daca e in interior (simplificat)
        this.isIndoors = npcLocation.getBlock().getLightFromSky() < 10;
        
        // Entitati din apropiere
        updateNearbyEntities(npcLocation);
    }

    /**
     * Calculeaza momentul zilei bazat pe tick-uri
     */
    private String calculateTimeOfDay(long time) {
        // Minecraft: 0 = rasarit, 6000 = amiaza, 12000 = apus, 18000 = miezul noptii
        if (time >= 0 && time < 6000) return "MORNING";
        if (time >= 6000 && time < 12000) return "AFTERNOON";
        if (time >= 12000 && time < 18000) return "EVENING";
        return "NIGHT";
    }

    /**
     * Actualizeaza lista entitatilor din apropiere
     */
    private void updateNearbyEntities(Location location) {
        nearbyPlayers.clear();
        nearbyHostileMobs = 0;
        nearbyPassiveMobs = 0;
        
        double range = 20.0;
        
        location.getWorld().getNearbyEntities(location, range, range, range).forEach(entity -> {
            if (entity instanceof Player player) {
                nearbyPlayers.add(player);
            } else if (isHostileMob(entity.getType().name())) {
                nearbyHostileMobs++;
            } else if (isPassiveMob(entity.getType().name())) {
                nearbyPassiveMobs++;
            }
        });
        
        // Actualizeaza starea de pericol
        this.isInDanger = nearbyHostileMobs > 0 || isHurt;
    }

    private boolean isHostileMob(String type) {
        return switch (type) {
            case "ZOMBIE", "SKELETON", "CREEPER", "SPIDER", "ENDERMAN", 
                 "WITCH", "PILLAGER", "VINDICATOR", "RAVAGER", "WARDEN" -> true;
            default -> false;
        };
    }

    private boolean isPassiveMob(String type) {
        return switch (type) {
            case "COW", "SHEEP", "PIG", "CHICKEN", "HORSE", "DONKEY",
                 "CAT", "DOG", "WOLF", "RABBIT", "FOX" -> true;
            default -> false;
        };
    }

    /**
     * Seteaza jucatorul cu care NPC-ul interactioneaza
     */
    public void setInteractingPlayer(Player player) {
        this.interactingPlayer = player;
        this.lastInteractionTime = System.currentTimeMillis();
    }

    /**
     * Adauga un eveniment recent
     */
    public void addRecentEvent(String event) {
        recentEvents.add(0, event);
        if (recentEvents.size() > 10) {
            recentEvents.remove(recentEvents.size() - 1);
        }
        this.lastSignificantEvent = event;
    }

    /**
     * Genereaza o descriere textuala a contextului pentru AI
     */
    public String generateContextDescription() {
        StringBuilder sb = new StringBuilder();
        
        // Timp si vreme
        sb.append("Este ").append(getTimeDescription()).append(". ");
        sb.append("Vremea: ").append(getWeatherDescription()).append(".\n");
        
        // Locatie
        if (isIndoors) {
            sb.append("Sunt in interior.\n");
        } else {
            sb.append("Sunt afara, in ").append(getBiomeDescription()).append(".\n");
        }
        
        // Entitati din apropiere
        if (!nearbyPlayers.isEmpty()) {
            sb.append("Vad ").append(nearbyPlayers.size()).append(" persoane in apropiere.\n");
        }
        
        if (nearbyHostileMobs > 0) {
            sb.append("ATENTIE: Sunt ").append(nearbyHostileMobs).append(" creaturi periculoase aproape!\n");
        }
        
        // Stare fizica
        if (healthPercent < 50) {
            sb.append("Ma simt slabita/slabit (sanatate scazuta).\n");
        }
        if (hungerLevel < 30) {
            sb.append("Mi-e foame.\n");
        }
        
        // Stare sociala
        if (isFamilyNearby) {
            sb.append("Familia mea e aproape.\n");
        }
        if (isAtWork) {
            sb.append("Sunt la munca.\n");
        } else if (isAtHome) {
            sb.append("Sunt acasa.\n");
        }
        
        // Relatie cu jucatorul
        if (interactingPlayer != null) {
            sb.append("\nVorbesc cu: ").append(interactingPlayer.getName()).append("\n");
            sb.append("Relatia noastra: ").append(getRelationshipDescription()).append("\n");
            
            if (!sharedMemories.isEmpty()) {
                sb.append("Amintiri comune: ").append(String.join(", ", sharedMemories.subList(0, Math.min(3, sharedMemories.size())))).append("\n");
            }
        }
        
        // Evenimente recente
        if (lastSignificantEvent != null) {
            sb.append("Recent: ").append(lastSignificantEvent).append("\n");
        }
        
        return sb.toString();
    }

    private String getTimeDescription() {
        return switch (timeOfDay) {
            case "MORNING" -> "dimineata";
            case "AFTERNOON" -> "dupa-amiaza";
            case "EVENING" -> "seara";
            case "NIGHT" -> "noapte";
            default -> "zi";
        };
    }

    private String getWeatherDescription() {
        return switch (weather) {
            case "RAIN" -> "ploua";
            case "THUNDER" -> "furtuna cu tunete";
            case "SNOW" -> "ninge";
            default -> "senin";
        };
    }

    private String getBiomeDescription() {
        if (biome == null) return "loc necunoscut";
        
        return switch (biome) {
            case "PLAINS" -> "campie";
            case "FOREST" -> "padure";
            case "DARK_FOREST" -> "padure intunecata";
            case "DESERT" -> "desert";
            case "MOUNTAINS", "WINDSWEPT_HILLS" -> "munti";
            case "SWAMP" -> "mlastina";
            case "TAIGA" -> "taiga";
            case "SNOWY_PLAINS" -> "campie inzapezita";
            case "JUNGLE" -> "jungla";
            case "BEACH" -> "plaja";
            case "RIVER" -> "langa rau";
            case "OCEAN" -> "langa ocean";
            case "VILLAGE" -> "sat";
            default -> biome.toLowerCase().replace("_", " ");
        };
    }

    private String getRelationshipDescription() {
        return switch (relationshipStatus) {
            case "STRANGER" -> "strain/straina - nu il/o cunosc";
            case "ACQUAINTANCE" -> "cunoscut/cunoscuta - am mai vorbit";
            case "FRIEND" -> "prieten/prietena - ne intelegem bine";
            case "CLOSE_FRIEND" -> "prieten apropiat - am incredere totala";
            case "ENEMY" -> "dusman - nu vreau sa am de-a face";
            case "FAMILY" -> "familie - rudenie de sange";
            case "SPOUSE" -> "sot/sotie - partener de viata";
            default -> "necunoscut";
        };
    }

    // Getters and Setters
    
    public AINPC getNpc() {
        return npc;
    }

    public long getWorldTime() {
        return worldTime;
    }

    public String getTimeOfDay() {
        return timeOfDay;
    }

    public String getWeather() {
        return weather;
    }

    public String getBiome() {
        return biome;
    }

    public boolean isIndoors() {
        return isIndoors;
    }

    public List<Player> getNearbyPlayers() {
        return nearbyPlayers;
    }

    public List<AINPC> getNearbyNPCs() {
        return nearbyNPCs;
    }

    public void setNearbyNPCs(List<AINPC> nearbyNPCs) {
        this.nearbyNPCs = nearbyNPCs;
    }

    public int getNearbyHostileMobs() {
        return nearbyHostileMobs;
    }

    public int getNearbyPassiveMobs() {
        return nearbyPassiveMobs;
    }

    public Player getInteractingPlayer() {
        return interactingPlayer;
    }

    public AINPC getInteractingNPC() {
        return interactingNPC;
    }

    public void setInteractingNPC(AINPC interactingNPC) {
        this.interactingNPC = interactingNPC;
    }

    public String getLastPlayerMessage() {
        return lastPlayerMessage;
    }

    public void setLastPlayerMessage(String lastPlayerMessage) {
        this.lastPlayerMessage = lastPlayerMessage;
    }

    public long getLastInteractionTime() {
        return lastInteractionTime;
    }

    public double getHealthPercent() {
        return healthPercent;
    }

    public void setHealthPercent(double healthPercent) {
        this.healthPercent = healthPercent;
    }

    public int getHungerLevel() {
        return hungerLevel;
    }

    public void setHungerLevel(int hungerLevel) {
        this.hungerLevel = hungerLevel;
    }

    public boolean isHurt() {
        return isHurt;
    }

    public void setHurt(boolean hurt) {
        isHurt = hurt;
    }

    public boolean isInDanger() {
        return isInDanger;
    }

    public void setInDanger(boolean inDanger) {
        isInDanger = inDanger;
    }

    public boolean isAtHome() {
        return isAtHome;
    }

    public void setAtHome(boolean atHome) {
        isAtHome = atHome;
    }

    public boolean isAtWork() {
        return isAtWork;
    }

    public void setAtWork(boolean atWork) {
        isAtWork = atWork;
    }

    public boolean isFamilyNearby() {
        return isFamilyNearby;
    }

    public void setFamilyNearby(boolean familyNearby) {
        isFamilyNearby = familyNearby;
    }

    public boolean isFriendsNearby() {
        return isFriendsNearby;
    }

    public void setFriendsNearby(boolean friendsNearby) {
        isFriendsNearby = friendsNearby;
    }

    public List<String> getRecentEvents() {
        return recentEvents;
    }

    public String getLastSignificantEvent() {
        return lastSignificantEvent;
    }

    public int getRelationshipLevel() {
        return relationshipLevel;
    }

    public void setRelationshipLevel(int relationshipLevel) {
        this.relationshipLevel = relationshipLevel;
        
        // Update status based on level
        if (relationshipLevel < -50) this.relationshipStatus = "ENEMY";
        else if (relationshipLevel < 0) this.relationshipStatus = "STRANGER";
        else if (relationshipLevel < 25) this.relationshipStatus = "ACQUAINTANCE";
        else if (relationshipLevel < 75) this.relationshipStatus = "FRIEND";
        else this.relationshipStatus = "CLOSE_FRIEND";
    }

    public String getRelationshipStatus() {
        return relationshipStatus;
    }

    public void setRelationshipStatus(String relationshipStatus) {
        this.relationshipStatus = relationshipStatus;
    }

    public List<String> getSharedMemories() {
        return sharedMemories;
    }

    public void setSharedMemories(List<String> sharedMemories) {
        this.sharedMemories = sharedMemories;
    }
}
