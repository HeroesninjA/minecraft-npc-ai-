package ro.ainpc.managers;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.npc.AINPC;
import ro.ainpc.npc.NPCEmotions;

/**
 * Manager pentru sistemul de emotii al NPC-urilor
 */
public class EmotionManager {

    private final AINPCPlugin plugin;

    public EmotionManager(AINPCPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Aplica o emotie unui NPC
     */
    public void applyEmotion(AINPC npc, String emotion, double intensity) {
        npc.getEmotions().applyEmotion(emotion, intensity);
        npc.updateDisplayName();
        
        // Afiseaza particule daca e activat
        if (plugin.getConfig().getBoolean("emotions.show_particles", true)) {
            showEmotionParticles(npc, emotion, intensity);
        }
        
        // Salveaza in baza de date
        plugin.getNpcManager().saveEmotions(npc);
        
        plugin.debug("Emotie aplicata pentru " + npc.getName() + ": " + emotion + " (" + intensity + ")");
    }

    /**
     * Decrementeaza emotiile tuturor NPC-urilor (revenire la starea neutra)
     */
    public void decayEmotions() {
        double decayRate = plugin.getConfig().getDouble("emotions.change_rate", 0.1);
        
        for (AINPC npc : plugin.getNpcManager().getAllNPCs()) {
            npc.getEmotions().decay(decayRate);
            npc.updateDisplayName();
        }
        
        plugin.debug("Decay emotii aplicat pentru toate NPC-urile.");
    }

    /**
     * Afiseaza particule vizuale pentru emotie
     */
    public void showEmotionParticles(AINPC npc, String emotion, double intensity) {
        if (!npc.isSpawned()) return;
        
        Location loc = npc.getLocation();
        if (loc == null) return;
        
        World world = loc.getWorld();
        if (world == null) return;
        
        // Locatia deasupra capului NPC-ului
        Location particleLoc = loc.clone().add(0, 2.2, 0);
        
        Particle particle;
        int count = (int) (5 + intensity * 10);
        
        switch (emotion.toLowerCase()) {
            case "happiness", "bucurie" -> {
                // Inimi verzi/galbene
                particle = Particle.HEART;
                world.spawnParticle(particle, particleLoc, count, 0.3, 0.3, 0.3);
            }
            case "sadness", "tristete" -> {
                // Particule de ploaie/lacrimi
                particle = Particle.FALLING_WATER;
                world.spawnParticle(particle, particleLoc, count * 2, 0.2, 0.1, 0.2);
            }
            case "anger", "furie" -> {
                // Fum rosu/foc
                particle = Particle.SMOKE;
                world.spawnParticle(particle, particleLoc, count, 0.2, 0.2, 0.2, 0.02);
                // Adauga si flacari mici
                if (intensity > 0.5) {
                    world.spawnParticle(Particle.FLAME, particleLoc, count / 2, 0.2, 0.2, 0.2, 0.01);
                }
            }
            case "fear", "frica" -> {
                // Particule de panica
                particle = Particle.WITCH;
                world.spawnParticle(particle, particleLoc, count, 0.3, 0.3, 0.3);
            }
            case "surprise", "surpriza" -> {
                // Stele/scantei
                particle = Particle.END_ROD;
                world.spawnParticle(particle, particleLoc, count, 0.3, 0.3, 0.3, 0.05);
            }
            case "disgust", "dezgust" -> {
                // Fum verde
                particle = Particle.HAPPY_VILLAGER;
                world.spawnParticle(particle, particleLoc, count, 0.3, 0.2, 0.3);
            }
            case "trust", "incredere" -> {
                // Particule aurii
                particle = Particle.ENCHANT;
                world.spawnParticle(particle, particleLoc.add(0, 0.5, 0), count * 2, 0.5, 0.5, 0.5, 0.5);
            }
            case "anticipation", "anticipare" -> {
                // Particule de portal
                particle = Particle.PORTAL;
                world.spawnParticle(particle, particleLoc, count, 0.3, 0.3, 0.3, 0.1);
            }
        }
    }

    /**
     * Obtine descrierea emotiei in romana
     */
    public String getEmotionDescription(AINPC npc) {
        NPCEmotions emotions = npc.getEmotions();
        String dominant = emotions.getDominantEmotion();
        double intensity = emotions.getEmotionValue(dominant);
        
        String emotionName = getEmotionNameRomanian(dominant);
        String intensityDesc = getIntensityDescription(intensity);
        
        return npc.getName() + " se simte " + intensityDesc + " " + emotionName;
    }

    /**
     * Obtine starea emotionala completa
     */
    public String getFullEmotionReport(AINPC npc) {
        StringBuilder sb = new StringBuilder();
        NPCEmotions emotions = npc.getEmotions();
        
        sb.append("&6=== Stare Emotionala: ").append(npc.getName()).append(" ===\n\n");
        
        // Emotie dominanta
        String dominant = emotions.getDominantEmotion();
        sb.append("&eEmotie dominanta: &f").append(getEmotionNameRomanian(dominant)).append("\n\n");
        
        // Toate emotiile
        sb.append("&eDetalii:\n");
        appendEmotionBar(sb, "Bucurie", emotions.getHappiness(), "&a");
        appendEmotionBar(sb, "Tristete", emotions.getSadness(), "&9");
        appendEmotionBar(sb, "Furie", emotions.getAnger(), "&c");
        appendEmotionBar(sb, "Frica", emotions.getFear(), "&5");
        appendEmotionBar(sb, "Surpriza", emotions.getSurprise(), "&e");
        appendEmotionBar(sb, "Dezgust", emotions.getDisgust(), "&2");
        appendEmotionBar(sb, "Incredere", emotions.getTrust(), "&b");
        appendEmotionBar(sb, "Anticipare", emotions.getAnticipation(), "&6");
        
        return sb.toString();
    }

    private void appendEmotionBar(StringBuilder sb, String name, double value, String color) {
        sb.append("&7").append(String.format("%-12s", name)).append(": ");
        sb.append(color);
        int filled = (int) (value * 10);
        sb.append("█".repeat(filled));
        sb.append("&8");
        sb.append("░".repeat(10 - filled));
        sb.append(" &f").append(String.format("%.0f%%", value * 100)).append("\n");
    }

    /**
     * Calculeaza raspunsul emotional la un eveniment
     */
    public void processEvent(AINPC npc, String eventType, double intensity) {
        switch (eventType.toLowerCase()) {
            case "player_approach" -> {
                // Jucator se apropie
                double extraversion = npc.getPersonality().getExtraversion();
                if (extraversion > 0.5) {
                    applyEmotion(npc, "happiness", 0.1 * intensity);
                } else {
                    applyEmotion(npc, "anticipation", 0.1 * intensity);
                }
            }
            case "player_leave" -> {
                // Jucator pleaca
                double agreeableness = npc.getPersonality().getAgreeableness();
                if (agreeableness > 0.6) {
                    applyEmotion(npc, "sadness", 0.05 * intensity);
                }
            }
            case "combat_nearby" -> {
                // Lupta in apropiere
                double neuroticism = npc.getPersonality().getNeuroticism();
                applyEmotion(npc, "fear", 0.2 * neuroticism * intensity);
            }
            case "weather_storm" -> {
                // Furtuna
                double neuroticism = npc.getPersonality().getNeuroticism();
                if (neuroticism > 0.5) {
                    applyEmotion(npc, "fear", 0.1 * intensity);
                }
            }
            case "daytime" -> {
                // Zi
                applyEmotion(npc, "happiness", 0.05 * intensity);
            }
            case "nighttime" -> {
                // Noapte
                double neuroticism = npc.getPersonality().getNeuroticism();
                if (neuroticism > 0.6) {
                    applyEmotion(npc, "fear", 0.05 * intensity);
                }
            }
        }
    }

    /**
     * Seteaza emotia dominanta direct
     */
    public void setMood(AINPC npc, String emotion, double intensity) {
        // Reseteaza toate emotiile
        NPCEmotions emotions = npc.getEmotions();
        emotions.setHappiness(emotion.equals("happiness") ? intensity : 0.3);
        emotions.setSadness(emotion.equals("sadness") ? intensity : 0.0);
        emotions.setAnger(emotion.equals("anger") ? intensity : 0.0);
        emotions.setFear(emotion.equals("fear") ? intensity : 0.0);
        emotions.setSurprise(emotion.equals("surprise") ? intensity : 0.0);
        emotions.setDisgust(emotion.equals("disgust") ? intensity : 0.0);
        emotions.setTrust(emotion.equals("trust") ? intensity : 0.5);
        emotions.setAnticipation(emotion.equals("anticipation") ? intensity : 0.3);
        
        npc.updateDisplayName();
        showEmotionParticles(npc, emotion, intensity);
        plugin.getNpcManager().saveEmotions(npc);
    }

    // Helper methods

    private String getEmotionNameRomanian(String emotion) {
        return switch (emotion.toLowerCase()) {
            case "happiness" -> "fericit";
            case "sadness" -> "trist";
            case "anger" -> "furios";
            case "fear" -> "speriat";
            case "surprise" -> "surprins";
            case "disgust" -> "dezgustat";
            case "trust" -> "increzator";
            case "anticipation" -> "nerabdator";
            case "neutral" -> "neutru";
            default -> emotion;
        };
    }

    private String getIntensityDescription(double value) {
        if (value > 0.8) return "extrem de";
        if (value > 0.6) return "foarte";
        if (value > 0.4) return "destul de";
        if (value > 0.2) return "putin";
        return "";
    }
}
