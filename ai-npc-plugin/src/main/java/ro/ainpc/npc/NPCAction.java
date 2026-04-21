package ro.ainpc.npc;

/**
 * Actiunile posibile pe care un NPC le poate face
 * Folosit in sistemul de scoring pentru decizii
 */
public enum NPCAction {
    
    // Actiuni de baza
    DO_NOTHING("Nu face nimic", 0),
    WALK_RANDOM("Plimbare", 5),
    WALK_TO_TARGET("Mers spre tinta", 10),
    RUN_TO_TARGET("Alergat spre tinta", 15),
    
    // Actiuni de interactiune
    GREET("Saluta", 20),
    TALK("Vorbeste", 25),
    LISTEN("Asculta", 15),
    TRADE("Negociaza", 30),
    GIVE_ITEM("Ofera obiect", 25),
    RECEIVE_ITEM("Primeste obiect", 20),
    
    // Actiuni de munca
    START_WORK("Incepe munca", 35),
    CONTINUE_WORK("Continua munca", 30),
    FINISH_WORK("Termina munca", 10),
    CRAFT("Creeaza", 40),
    FARM("Fermiereaza", 35),
    MINE("Mineaza", 35),
    FISH("Pescuieste", 30),
    
    // Actiuni sociale
    SOCIALIZE("Socializeaza", 25),
    TELL_STORY("Povesteste", 30),
    SHARE_NEWS("Impartaseste stiri", 25),
    GOSSIP("Barfeste", 20),
    FLIRT("Flirteaza", 15),
    ARGUE("Se cearta", 20),
    APOLOGIZE("Se scuza", 25),
    THANK("Multumeste", 30),
    COMPLIMENT("Complimenteaza", 25),
    INSULT("Insulta", 10),
    
    // Actiuni de combat
    ATTACK("Ataca", 50),
    DEFEND("Se apara", 45),
    FLEE("Fuge", 60),
    CALL_HELP("Cere ajutor", 55),
    SURRENDER("Se preda", 40),
    THREATEN("Ameninta", 30),
    WARN("Avertizeaza", 35),
    
    // Actiuni de ingrijire
    EAT("Mananca", 35),
    DRINK("Bea", 30),
    SLEEP("Doarme", 40),
    REST("Se odihneste", 25),
    HEAL("Se vindeca", 45),
    
    // Actiuni emotionale
    CRY("Plange", 15),
    LAUGH("Rade", 20),
    CELEBRATE("Sarbatoreste", 30),
    MOURN("Jeleste", 25),
    PRAY("Se roaga", 20),
    MEDITATE("Mediteaza", 15),
    
    // Actiuni de explorare
    INVESTIGATE("Investigheaza", 35),
    OBSERVE("Observa", 20),
    HIDE("Se ascunde", 40),
    SEARCH("Cauta", 30),
    
    // Actiuni de scenarii
    OFFER_QUEST("Ofera misiune", 50),
    GIVE_REWARD("Ofera recompensa", 45),
    FOLLOW_PLAYER("Urmareste jucator", 35),
    WAIT_FOR_PLAYER("Asteapta jucator", 20),
    GUIDE_PLAYER("Ghideaza jucator", 40),
    
    // Actiuni de memorie
    REMEMBER("Isi aminteste", 25),
    FORGET("Uita", 10),
    RECOGNIZE("Recunoaste", 30);

    private final String displayName;
    private final int baseScore;

    NPCAction(String displayName, int baseScore) {
        this.displayName = displayName;
        this.baseScore = baseScore;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getBaseScore() {
        return baseScore;
    }

    /**
     * Verifica daca actiunea necesita o tinta (jucator sau NPC)
     */
    public boolean requiresTarget() {
        return switch (this) {
            case TALK, GREET, TRADE, GIVE_ITEM, RECEIVE_ITEM, ATTACK, DEFEND, 
                 FOLLOW_PLAYER, GUIDE_PLAYER, THANK, APOLOGIZE, FLIRT, INSULT,
                 THREATEN, WARN, RECOGNIZE -> true;
            default -> false;
        };
    }

    /**
     * Verifica daca actiunea este una agresiva
     */
    public boolean isAggressive() {
        return switch (this) {
            case ATTACK, THREATEN, INSULT, ARGUE -> true;
            default -> false;
        };
    }

    /**
     * Verifica daca actiunea este una prietenoasa
     */
    public boolean isFriendly() {
        return switch (this) {
            case GREET, THANK, COMPLIMENT, GIVE_ITEM, CELEBRATE,
                 OFFER_QUEST, GIVE_REWARD, GUIDE_PLAYER, SHARE_NEWS, TELL_STORY -> true;
            default -> false;
        };
    }

    /**
     * Verifica daca actiunea este de natura sociala.
     * Pastram numele metodei pentru compatibilitate cu DecisionEngine.
     */
    public boolean isSocialState() {
        return switch (getCategory()) {
            case INTERACTION, SOCIAL -> true;
            default -> false;
        };
    }

    /**
     * Verifica daca actiunea este legata de munca.
     * Pastram numele metodei pentru compatibilitate cu DecisionEngine.
     */
    public boolean isWorkState() {
        return getCategory() == ActionCategory.WORK;
    }

    /**
     * Obtine categoria actiunii
     */
    public ActionCategory getCategory() {
        return switch (this) {
            case DO_NOTHING, WALK_RANDOM, WALK_TO_TARGET, RUN_TO_TARGET -> ActionCategory.MOVEMENT;
            case GREET, TALK, LISTEN, TRADE, GIVE_ITEM, RECEIVE_ITEM -> ActionCategory.INTERACTION;
            case START_WORK, CONTINUE_WORK, FINISH_WORK, CRAFT, FARM, MINE, FISH -> ActionCategory.WORK;
            case SOCIALIZE, TELL_STORY, SHARE_NEWS, GOSSIP, FLIRT, ARGUE, APOLOGIZE, 
                 THANK, COMPLIMENT, INSULT -> ActionCategory.SOCIAL;
            case ATTACK, DEFEND, FLEE, CALL_HELP, SURRENDER, THREATEN, WARN -> ActionCategory.COMBAT;
            case EAT, DRINK, SLEEP, REST, HEAL -> ActionCategory.SELF_CARE;
            case CRY, LAUGH, CELEBRATE, MOURN, PRAY, MEDITATE -> ActionCategory.EMOTIONAL;
            case INVESTIGATE, OBSERVE, HIDE, SEARCH -> ActionCategory.EXPLORATION;
            case OFFER_QUEST, GIVE_REWARD, FOLLOW_PLAYER, WAIT_FOR_PLAYER, GUIDE_PLAYER -> ActionCategory.SCENARIO;
            case REMEMBER, FORGET, RECOGNIZE -> ActionCategory.MEMORY;
        };
    }

    // Actiune extra pentru compatibilitate
    public static final NPCAction HELP = GIVE_ITEM;

    public enum ActionCategory {
        MOVEMENT("Miscare"),
        INTERACTION("Interactiune"),
        WORK("Munca"),
        SOCIAL("Social"),
        COMBAT("Combat"),
        SELF_CARE("Ingrijire"),
        EMOTIONAL("Emotional"),
        EXPLORATION("Explorare"),
        SCENARIO("Scenarii"),
        MEMORY("Memorie");

        private final String displayName;

        ActionCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
