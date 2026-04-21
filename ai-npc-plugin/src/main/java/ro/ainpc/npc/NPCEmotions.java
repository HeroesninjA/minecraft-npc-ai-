package ro.ainpc.npc;

import java.util.HashMap;
import java.util.Map;

/**
 * Sistemul de emotii al NPC-ului bazat pe modelul Plutchik
 */
public class NPCEmotions {

    // Emotii primare (0.0 - 1.0)
    private double happiness;     // Bucurie
    private double sadness;       // Tristete
    private double anger;         // Furie
    private double fear;          // Frica
    private double surprise;      // Surpriza
    private double disgust;       // Dezgust
    private double trust;         // Incredere
    private double anticipation;  // Anticipare

    private long lastUpdated;

    public NPCEmotions() {
        // Stare emotionala neutra/pozitiva default
        this.happiness = 0.5;
        this.sadness = 0.0;
        this.anger = 0.0;
        this.fear = 0.0;
        this.surprise = 0.0;
        this.disgust = 0.0;
        this.trust = 0.5;
        this.anticipation = 0.3;
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Aplica o emotie cu o anumita intensitate
     */
    public void applyEmotion(String emotion, double intensity) {
        intensity = Math.max(0.0, Math.min(1.0, intensity));
        
        switch (emotion.toLowerCase()) {
            case "happiness", "bucurie", "joy" -> {
                happiness = blend(happiness, intensity);
                sadness = decay(sadness, intensity * 0.5);
            }
            case "sadness", "tristete", "sorrow" -> {
                sadness = blend(sadness, intensity);
                happiness = decay(happiness, intensity * 0.5);
            }
            case "anger", "furie", "rage" -> {
                anger = blend(anger, intensity);
                fear = decay(fear, intensity * 0.3);
            }
            case "fear", "frica", "terror" -> {
                fear = blend(fear, intensity);
                anger = decay(anger, intensity * 0.3);
            }
            case "surprise", "surpriza", "amazement" -> {
                surprise = blend(surprise, intensity);
            }
            case "disgust", "dezgust", "loathing" -> {
                disgust = blend(disgust, intensity);
                trust = decay(trust, intensity * 0.5);
            }
            case "trust", "incredere", "admiration" -> {
                trust = blend(trust, intensity);
                disgust = decay(disgust, intensity * 0.5);
                fear = decay(fear, intensity * 0.3);
            }
            case "anticipation", "anticipare", "vigilance" -> {
                anticipation = blend(anticipation, intensity);
            }
        }
        
        lastUpdated = System.currentTimeMillis();
        normalizeEmotions();
    }

    /**
     * Aplica emotii bazate pe tipul de interactiune
     */
    public void applyInteractionEffect(String interactionType, double multiplier) {
        switch (interactionType.toLowerCase()) {
            case "greeting", "salut" -> {
                applyEmotion("happiness", 0.1 * multiplier);
                applyEmotion("trust", 0.05 * multiplier);
            }
            case "compliment", "compliment" -> {
                applyEmotion("happiness", 0.2 * multiplier);
                applyEmotion("trust", 0.1 * multiplier);
            }
            case "insult", "insulta" -> {
                applyEmotion("anger", 0.3 * multiplier);
                applyEmotion("sadness", 0.1 * multiplier);
            }
            case "gift", "cadou" -> {
                applyEmotion("happiness", 0.3 * multiplier);
                applyEmotion("surprise", 0.2 * multiplier);
                applyEmotion("trust", 0.15 * multiplier);
            }
            case "threat", "amenintare" -> {
                applyEmotion("fear", 0.4 * multiplier);
                applyEmotion("anger", 0.2 * multiplier);
            }
            case "help", "ajutor" -> {
                applyEmotion("happiness", 0.15 * multiplier);
                applyEmotion("trust", 0.2 * multiplier);
            }
            case "ignore", "ignorare" -> {
                applyEmotion("sadness", 0.1 * multiplier);
            }
            case "joke", "gluma" -> {
                applyEmotion("happiness", 0.15 * multiplier);
                applyEmotion("surprise", 0.1 * multiplier);
            }
            case "secret", "secret" -> {
                applyEmotion("trust", 0.2 * multiplier);
                applyEmotion("anticipation", 0.15 * multiplier);
            }
            case "betrayal", "tradare" -> {
                applyEmotion("anger", 0.4 * multiplier);
                applyEmotion("sadness", 0.3 * multiplier);
                applyEmotion("disgust", 0.2 * multiplier);
            }
        }
    }

    /**
     * Decrementeaza emotiile in timp (revenire la neutral)
     */
    public void decay(double rate) {
        happiness = decayTowards(happiness, 0.5, rate);
        sadness = decayTowards(sadness, 0.0, rate);
        anger = decayTowards(anger, 0.0, rate);
        fear = decayTowards(fear, 0.0, rate);
        surprise = decayTowards(surprise, 0.0, rate * 2); // Surpriza scade mai repede
        disgust = decayTowards(disgust, 0.0, rate);
        trust = decayTowards(trust, 0.5, rate * 0.5); // Increderea se schimba mai greu
        anticipation = decayTowards(anticipation, 0.3, rate);
        
        lastUpdated = System.currentTimeMillis();
    }

    /**
     * Obtine emotia dominanta
     */
    public String getDominantEmotion() {
        Map<String, Double> emotions = getEmotionMap();
        
        String dominant = "neutral";
        double maxValue = 0.0;
        
        for (Map.Entry<String, Double> entry : emotions.entrySet()) {
            // Happiness si trust au un prag mai mare pentru a fi "dominante"
            double threshold = (entry.getKey().equals("happiness") || entry.getKey().equals("trust")) ? 0.6 : 0.3;
            
            if (entry.getValue() > threshold && entry.getValue() > maxValue) {
                maxValue = entry.getValue();
                dominant = entry.getKey();
            }
        }
        
        return dominant;
    }

    /**
     * Obtine culoarea asociata emotiei dominante
     */
    public String getDominantEmotionColor() {
        String emotion = getDominantEmotion();
        return getEmotionColor(emotion);
    }

    /**
     * Obtine culoarea pentru o emotie
     */
    public static String getEmotionColor(String emotion) {
        return switch (emotion.toLowerCase()) {
            case "happiness", "bucurie" -> "\u00A7a"; // Verde
            case "sadness", "tristete" -> "\u00A79"; // Albastru
            case "anger", "furie" -> "\u00A7c"; // Rosu
            case "fear", "frica" -> "\u00A75"; // Mov
            case "surprise", "surpriza" -> "\u00A7e"; // Galben
            case "disgust", "dezgust" -> "\u00A72"; // Verde inchis
            case "trust", "incredere" -> "\u00A7b"; // Cyan
            case "anticipation", "anticipare" -> "\u00A76"; // Portocaliu
            default -> "\u00A7f"; // Alb (neutral)
        };
    }

    /**
     * Obtine descrierea starii emotionale
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        String dominant = getDominantEmotion();
        
        sb.append("Emotie dominanta: ").append(getEmotionNameRomanian(dominant)).append("\n");
        
        Map<String, Double> emotions = getEmotionMap();
        for (Map.Entry<String, Double> entry : emotions.entrySet()) {
            if (entry.getValue() > 0.2) {
                sb.append("- ").append(getEmotionNameRomanian(entry.getKey()))
                  .append(": ").append(getIntensityDescription(entry.getValue())).append("\n");
            }
        }
        
        return sb.toString();
    }

    /**
     * Obtine descrierea scurta a starii emotionale pentru prompt
     */
    public String getShortDescription() {
        String dominant = getDominantEmotion();
        double intensity = getEmotionValue(dominant);
        
        String intensityWord = intensity > 0.7 ? "foarte " : (intensity > 0.4 ? "" : "putin ");
        return intensityWord + getEmotionNameRomanian(dominant);
    }

    /**
     * Converteste numele emotiei in romana
     */
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

    /**
     * Obtine descrierea intensitatii
     */
    private String getIntensityDescription(double value) {
        if (value > 0.8) return "foarte puternic";
        if (value > 0.6) return "puternic";
        if (value > 0.4) return "moderat";
        if (value > 0.2) return "slab";
        return "foarte slab";
    }

    /**
     * Obtine valoarea unei emotii dupa nume
     */
    public double getEmotionValue(String emotion) {
        return switch (emotion.toLowerCase()) {
            case "happiness" -> happiness;
            case "sadness" -> sadness;
            case "anger" -> anger;
            case "fear" -> fear;
            case "surprise" -> surprise;
            case "disgust" -> disgust;
            case "trust" -> trust;
            case "anticipation" -> anticipation;
            default -> 0.0;
        };
    }

    /**
     * Obtine harta emotiilor
     */
    public Map<String, Double> getEmotionMap() {
        Map<String, Double> map = new HashMap<>();
        map.put("happiness", happiness);
        map.put("sadness", sadness);
        map.put("anger", anger);
        map.put("fear", fear);
        map.put("surprise", surprise);
        map.put("disgust", disgust);
        map.put("trust", trust);
        map.put("anticipation", anticipation);
        return map;
    }

    // Metode helper private

    private double blend(double current, double target) {
        return current + (target - current) * 0.3;
    }

    private double decay(double value, double amount) {
        return Math.max(0.0, value - amount);
    }

    private double decayTowards(double current, double target, double rate) {
        if (Math.abs(current - target) < 0.01) return target;
        return current + (target - current) * rate;
    }

    private void normalizeEmotions() {
        // Asigura ca valorile sunt in intervalul [0, 1]
        happiness = clamp(happiness);
        sadness = clamp(sadness);
        anger = clamp(anger);
        fear = clamp(fear);
        surprise = clamp(surprise);
        disgust = clamp(disgust);
        trust = clamp(trust);
        anticipation = clamp(anticipation);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    // Getters si Setters

    public double getHappiness() {
        return happiness;
    }

    public void setHappiness(double happiness) {
        this.happiness = clamp(happiness);
    }

    public double getSadness() {
        return sadness;
    }

    public void setSadness(double sadness) {
        this.sadness = clamp(sadness);
    }

    public double getAnger() {
        return anger;
    }

    public void setAnger(double anger) {
        this.anger = clamp(anger);
    }

    public double getFear() {
        return fear;
    }

    public void setFear(double fear) {
        this.fear = clamp(fear);
    }

    public double getSurprise() {
        return surprise;
    }

    public void setSurprise(double surprise) {
        this.surprise = clamp(surprise);
    }

    public double getDisgust() {
        return disgust;
    }

    public void setDisgust(double disgust) {
        this.disgust = clamp(disgust);
    }

    public double getTrust() {
        return trust;
    }

    public void setTrust(double trust) {
        this.trust = clamp(trust);
    }

    public double getAnticipation() {
        return anticipation;
    }

    public void setAnticipation(double anticipation) {
        this.anticipation = clamp(anticipation);
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
