package ro.ainpc.npc;

/**
 * Starile posibile ale unui NPC - State Machine
 * NPC-ul poate fi intr-o singura stare la un moment dat
 */
public enum NPCState {
    
    // Stari de baza
    IDLE("Asteapta", "NPC-ul nu face nimic special"),
    WALKING("Merge", "NPC-ul se deplaseaza"),
    RUNNING("Alearga", "NPC-ul se deplaseaza rapid"),
    
    // Stari de interactiune
    TALKING("Vorbeste", "NPC-ul este in conversatie"),
    LISTENING("Asculta", "NPC-ul asculta pe cineva"),
    TRADING("Negociaza", "NPC-ul face schimburi comerciale"),
    
    // Stari de munca
    WORKING("Lucreaza", "NPC-ul isi desfasoara meseria"),
    CRAFTING("Creeaza", "NPC-ul creaza obiecte"),
    FARMING("Fermiereaza", "NPC-ul lucreaza pamantul"),
    MINING("Mineaza", "NPC-ul sapa"),
    FISHING("Pescuieste", "NPC-ul pescuieste"),
    
    // Stari sociale
    SOCIALIZING("Socializeaza", "NPC-ul interactioneaza cu alte NPC-uri"),
    CELEBRATING("Sarbatoreste", "NPC-ul participa la o sarbatoare"),
    MOURNING("Jeleste", "NPC-ul este in doliu"),
    ARGUING("Se cearta", "NPC-ul are un conflict verbal"),
    
    // Stari de combat
    COMBAT("In lupta", "NPC-ul lupta activ"),
    FLEEING("Fuge", "NPC-ul fuge de pericol"),
    GUARDING("Pazeste", "NPC-ul pazeste o zona"),
    PATROLLING("Patruleaza", "NPC-ul patruleaza o zona"),
    
    // Stari de odihna
    SLEEPING("Doarme", "NPC-ul doarme"),
    RESTING("Se odihneste", "NPC-ul se odihneste"),
    EATING("Mananca", "NPC-ul mananca"),
    DRINKING("Bea", "NPC-ul bea"),
    
    // Stari emotionale speciale
    PANICKING("Panicheaza", "NPC-ul este cuprins de panica"),
    CURIOUS("Curios", "NPC-ul investigheaza ceva"),
    HIDING("Se ascunde", "NPC-ul se ascunde"),
    PRAYING("Se roaga", "NPC-ul se roaga"),
    
    // Stari de scenarii
    QUEST_GIVING("Ofera misiune", "NPC-ul ofera o misiune"),
    FOLLOWING("Urmareste", "NPC-ul urmareste pe cineva"),
    WAITING("Asteapta", "NPC-ul asteapta ceva sau pe cineva");

    private final String displayName;
    private final String description;

    NPCState(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Verifica daca starea permite interactiunea cu jucatori
     */
    public boolean allowsPlayerInteraction() {
        return switch (this) {
            case SLEEPING, COMBAT, FLEEING, PANICKING, HIDING -> false;
            default -> true;
        };
    }

    /**
     * Verifica daca starea permite miscarea
     */
    public boolean allowsMovement() {
        return switch (this) {
            case SLEEPING, TALKING, TRADING, PRAYING, WORKING, CRAFTING -> false;
            default -> true;
        };
    }

    /**
     * Verifica daca starea este una de munca
     */
    public boolean isWorkState() {
        return switch (this) {
            case WORKING, CRAFTING, FARMING, MINING, FISHING, GUARDING, PATROLLING -> true;
            default -> false;
        };
    }

    /**
     * Verifica daca starea este una sociala
     */
    public boolean isSocialState() {
        return switch (this) {
            case TALKING, LISTENING, SOCIALIZING, CELEBRATING, ARGUING, TRADING -> true;
            default -> false;
        };
    }

    /**
     * Verifica daca starea este una de pericol
     */
    public boolean isDangerState() {
        return switch (this) {
            case COMBAT, FLEEING, PANICKING, HIDING, GUARDING -> true;
            default -> false;
        };
    }

    /**
     * Obtine prioritatea starii (pentru tranzitii)
     * Cu cat mai mare, cu atat mai greu de intrerupt
     */
    public int getPriority() {
        return switch (this) {
            case COMBAT, FLEEING, PANICKING -> 100;
            case SLEEPING -> 80;
            case TALKING, TRADING -> 60;
            case WORKING, CRAFTING -> 40;
            case WALKING, RUNNING, PATROLLING -> 20;
            case IDLE -> 0;
            default -> 30;
        };
    }
}
