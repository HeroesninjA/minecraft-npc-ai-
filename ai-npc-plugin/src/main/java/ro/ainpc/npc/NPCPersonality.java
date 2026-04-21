package ro.ainpc.npc;

import java.util.Random;

/**
 * Personalitatea NPC-ului bazata pe modelul Big Five (OCEAN)
 */
public class NPCPersonality {

    // Big Five Personality Traits (0.0 - 1.0)
    private double openness;          // Deschidere catre experiente noi
    private double conscientiousness; // Constiinciozitate, organizare
    private double extraversion;      // Extraversie, sociabilitate
    private double agreeableness;     // Agreabilitate, amabilitate
    private double neuroticism;       // Neuroticism, instabilitate emotionala

    private static final Random random = new Random();

    public NPCPersonality() {
        // Valori default medii
        this.openness = 0.5;
        this.conscientiousness = 0.5;
        this.extraversion = 0.5;
        this.agreeableness = 0.5;
        this.neuroticism = 0.5;
    }

    public NPCPersonality(double openness, double conscientiousness, double extraversion, 
                          double agreeableness, double neuroticism) {
        this.openness = clamp(openness);
        this.conscientiousness = clamp(conscientiousness);
        this.extraversion = clamp(extraversion);
        this.agreeableness = clamp(agreeableness);
        this.neuroticism = clamp(neuroticism);
    }

    /**
     * Genereaza o personalitate aleatorie realista
     */
    public static NPCPersonality generateRandom() {
        NPCPersonality personality = new NPCPersonality();
        
        // Genereaza valori cu distributie normala centrata pe 0.5
        personality.openness = generateTrait();
        personality.conscientiousness = generateTrait();
        personality.extraversion = generateTrait();
        personality.agreeableness = generateTrait();
        personality.neuroticism = generateTrait();
        
        return personality;
    }

    /**
     * Genereaza un trait cu distributie normala
     */
    private static double generateTrait() {
        // Media 0.5, deviatie standard 0.2
        double value = random.nextGaussian() * 0.2 + 0.5;
        return clamp(value);
    }

    /**
     * Creeaza o personalitate bazata pe un arhetip
     */
    public static NPCPersonality fromArchetype(String archetype) {
        return switch (archetype.toLowerCase()) {
            case "hero", "erou" -> new NPCPersonality(0.7, 0.8, 0.7, 0.8, 0.2);
            case "villain", "raufacator" -> new NPCPersonality(0.5, 0.6, 0.4, 0.1, 0.7);
            case "sage", "intelept" -> new NPCPersonality(0.9, 0.7, 0.4, 0.7, 0.3);
            case "jester", "bufon" -> new NPCPersonality(0.8, 0.3, 0.9, 0.7, 0.4);
            case "caregiver", "ingrijitor" -> new NPCPersonality(0.5, 0.7, 0.6, 0.9, 0.4);
            case "explorer", "explorator" -> new NPCPersonality(0.9, 0.4, 0.6, 0.5, 0.3);
            case "rebel", "rebel" -> new NPCPersonality(0.7, 0.3, 0.5, 0.3, 0.6);
            case "lover", "romantic" -> new NPCPersonality(0.7, 0.5, 0.7, 0.8, 0.5);
            case "creator", "creator" -> new NPCPersonality(0.9, 0.6, 0.5, 0.6, 0.4);
            case "ruler", "conducator" -> new NPCPersonality(0.5, 0.9, 0.6, 0.4, 0.3);
            case "magician", "magician" -> new NPCPersonality(0.9, 0.5, 0.5, 0.5, 0.4);
            case "innocent", "inocent" -> new NPCPersonality(0.4, 0.5, 0.6, 0.9, 0.2);
            case "orphan", "orfan" -> new NPCPersonality(0.4, 0.5, 0.4, 0.6, 0.7);
            case "warrior", "razboinic" -> new NPCPersonality(0.4, 0.8, 0.5, 0.3, 0.4);
            case "merchant", "negustor" -> new NPCPersonality(0.6, 0.7, 0.8, 0.5, 0.3);
            default -> generateRandom();
        };
    }

    /**
     * Obtine descrierea personalitatii pentru contextul AI
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        
        // Openness
        if (openness > 0.7) {
            sb.append("- Foarte curios si deschis la idei noi, iubeste aventura si creativitatea\n");
        } else if (openness > 0.5) {
            sb.append("- Moderat deschis la experiente noi\n");
        } else if (openness > 0.3) {
            sb.append("- Preferi rutina si traditia\n");
        } else {
            sb.append("- Foarte conservator, rezistent la schimbare\n");
        }
        
        // Conscientiousness
        if (conscientiousness > 0.7) {
            sb.append("- Foarte organizat, disciplinat si de incredere\n");
        } else if (conscientiousness > 0.5) {
            sb.append("- Echilibrat intre spontaneitate si organizare\n");
        } else if (conscientiousness > 0.3) {
            sb.append("- Flexibil dar uneori dezorganizat\n");
        } else {
            sb.append("- Impulsiv si dezorganizat\n");
        }
        
        // Extraversion
        if (extraversion > 0.7) {
            sb.append("- Foarte sociabil, vorbaret si energic\n");
        } else if (extraversion > 0.5) {
            sb.append("- Sociabil dar apreciaza si singuritatea\n");
        } else if (extraversion > 0.3) {
            sb.append("- Rezervat, prefera grupuri mici\n");
        } else {
            sb.append("- Foarte introvertit, evita interactiunile sociale\n");
        }
        
        // Agreeableness
        if (agreeableness > 0.7) {
            sb.append("- Foarte bland, empatic si cooperant\n");
        } else if (agreeableness > 0.5) {
            sb.append("- In general amabil dar poate fi ferm cand e nevoie\n");
        } else if (agreeableness > 0.3) {
            sb.append("- Pragmatic, poate parea rece uneori\n");
        } else {
            sb.append("- Sceptic, competitiv si uneori ostil\n");
        }
        
        // Neuroticism
        if (neuroticism > 0.7) {
            sb.append("- Emotional instabil, se streseaza usor\n");
        } else if (neuroticism > 0.5) {
            sb.append("- Sensibil emotional dar gestioneaza stresul\n");
        } else if (neuroticism > 0.3) {
            sb.append("- In general calm si stabil emotional\n");
        } else {
            sb.append("- Foarte calm, rar afectat de stres\n");
        }
        
        return sb.toString();
    }

    /**
     * Obtine o lista de trasaturi dominante
     */
    public String getDominantTraits() {
        StringBuilder sb = new StringBuilder();
        
        if (openness > 0.6) sb.append("curios, ");
        if (openness < 0.4) sb.append("traditional, ");
        
        if (conscientiousness > 0.6) sb.append("organizat, ");
        if (conscientiousness < 0.4) sb.append("spontan, ");
        
        if (extraversion > 0.6) sb.append("sociabil, ");
        if (extraversion < 0.4) sb.append("introvertit, ");
        
        if (agreeableness > 0.6) sb.append("amabil, ");
        if (agreeableness < 0.4) sb.append("competitiv, ");
        
        if (neuroticism > 0.6) sb.append("emotional, ");
        if (neuroticism < 0.4) sb.append("calm, ");
        
        String result = sb.toString();
        if (result.endsWith(", ")) {
            result = result.substring(0, result.length() - 2);
        }
        
        return result.isEmpty() ? "echilibrat" : result;
    }

    /**
     * Calculeaza cat de bine se potriveste NPC-ul cu un anumit tip de conversatie
     */
    public double getConversationAffinity(String topic) {
        return switch (topic.toLowerCase()) {
            case "adventure", "aventura" -> (openness + extraversion) / 2;
            case "philosophy", "filosofie" -> openness * 0.7 + conscientiousness * 0.3;
            case "gossip", "barfa" -> extraversion * 0.6 + (1 - conscientiousness) * 0.4;
            case "work", "munca" -> conscientiousness;
            case "feelings", "sentimente" -> (agreeableness + neuroticism) / 2;
            case "conflict", "conflict" -> (1 - agreeableness) * 0.6 + neuroticism * 0.4;
            case "humor", "umor" -> extraversion * 0.5 + openness * 0.5;
            default -> 0.5;
        };
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    // Getters si Setters

    public double getOpenness() {
        return openness;
    }

    public void setOpenness(double openness) {
        this.openness = clamp(openness);
    }

    public double getConscientiousness() {
        return conscientiousness;
    }

    public void setConscientiousness(double conscientiousness) {
        this.conscientiousness = clamp(conscientiousness);
    }

    public double getExtraversion() {
        return extraversion;
    }

    public void setExtraversion(double extraversion) {
        this.extraversion = clamp(extraversion);
    }

    public double getAgreeableness() {
        return agreeableness;
    }

    public void setAgreeableness(double agreeableness) {
        this.agreeableness = clamp(agreeableness);
    }

    public double getNeuroticism() {
        return neuroticism;
    }

    public void setNeuroticism(double neuroticism) {
        this.neuroticism = clamp(neuroticism);
    }
}
