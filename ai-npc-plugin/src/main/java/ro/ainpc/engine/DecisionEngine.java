package ro.ainpc.engine;

import ro.ainpc.AINPCPlugin;
import ro.ainpc.npc.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Motor de decizie pentru NPC-uri
 * Foloseste un sistem de scoring bazat pe: traits + emotii + memorie + context + random mic
 * Formula: action_score = base + trait_modifier + emotion_modifier + memory_modifier + context_modifier + random(0-10)
 */
public class DecisionEngine {

    private final AINPCPlugin plugin;
    
    // Cache pentru scoruri calculate recent
    private final Map<UUID, Map<NPCAction, Integer>> scoreCache;
    private final Map<UUID, Long> lastDecisionTime;
    
    // Timpul minim intre decizii (ms)
    private static final long DECISION_COOLDOWN = 1000;

    public DecisionEngine(AINPCPlugin plugin) {
        this.plugin = plugin;
        this.scoreCache = new ConcurrentHashMap<>();
        this.lastDecisionTime = new ConcurrentHashMap<>();
    }

    /**
     * Decide cea mai buna actiune pentru un NPC bazat pe context
     */
    public NPCAction decideAction(AINPC npc, NPCContext context) {
        // Verifica cooldown
        long now = System.currentTimeMillis();
        Long lastTime = lastDecisionTime.get(npc.getUuid());
        if (lastTime != null && now - lastTime < DECISION_COOLDOWN) {
            // Returneaza actiunea din cache sau DO_NOTHING
            Map<NPCAction, Integer> cached = scoreCache.get(npc.getUuid());
            if (cached != null && !cached.isEmpty()) {
                return getHighestScoringAction(cached);
            }
            return NPCAction.DO_NOTHING;
        }
        
        lastDecisionTime.put(npc.getUuid(), now);
        
        // Calculeaza scorurile pentru toate actiunile relevante
        Map<NPCAction, Integer> scores = calculateAllScores(npc, context);
        scoreCache.put(npc.getUuid(), scores);
        
        // Alege actiunea cu cel mai mare scor
        NPCAction bestAction = getHighestScoringAction(scores);
        
        plugin.debug("NPC " + npc.getName() + " decide: " + bestAction.getDisplayName() + 
                     " (scor: " + scores.get(bestAction) + ")");
        
        return bestAction;
    }

    /**
     * Calculeaza scorurile pentru toate actiunile posibile
     */
    private Map<NPCAction, Integer> calculateAllScores(AINPC npc, NPCContext context) {
        Map<NPCAction, Integer> scores = new EnumMap<>(NPCAction.class);
        
        // Filtreaza actiunile bazat pe starea curenta
        List<NPCAction> possibleActions = filterActionsByState(npc.getCurrentState());
        
        for (NPCAction action : possibleActions) {
            int score = calculateActionScore(npc, context, action);
            scores.put(action, score);
        }
        
        return scores;
    }

    /**
     * Calculeaza scorul pentru o actiune specifica
     * Formula: base + trait + emotion + memory + context + random
     */
    private int calculateActionScore(AINPC npc, NPCContext context, NPCAction action) {
        int score = action.getBaseScore();
        
        // Modificator de personalitate (traits)
        score += getTraitModifier(npc.getPersonality(), action);
        
        // Modificator de emotii
        score += getEmotionModifier(npc.getEmotions(), action);
        
        // Modificator de memorie (relatii cu jucatorul)
        score += getMemoryModifier(npc, context, action);
        
        // Modificator de context (situatie curenta)
        score += getContextModifier(context, action);
        
        // Random mic pentru variatie (0-10)
        score += new Random().nextInt(11);
        
        // Scorul nu poate fi negativ
        return Math.max(0, score);
    }

    /**
     * Modificator bazat pe trasaturi de personalitate
     */
    private int getTraitModifier(NPCPersonality personality, NPCAction action) {
        int modifier = 0;
        
        // Extraversion
        if (action.isSocialState() || action == NPCAction.GREET || action == NPCAction.SOCIALIZE) {
            modifier += (int) ((personality.getExtraversion() - 0.5) * 30);
        }
        
        // Agreeableness
        if (action.isFriendly()) {
            modifier += (int) ((personality.getAgreeableness() - 0.5) * 25);
        }
        if (action.isAggressive()) {
            modifier -= (int) (personality.getAgreeableness() * 20);
        }
        
        // Conscientiousness
        if (action.getCategory() == NPCAction.ActionCategory.WORK) {
            modifier += (int) ((personality.getConscientiousness() - 0.5) * 30);
        }
        
        // Neuroticism
        if (action == NPCAction.FLEE || action == NPCAction.HIDE) {
            modifier += (int) (personality.getNeuroticism() * 20);
        }
        if (action == NPCAction.ATTACK || action == NPCAction.THREATEN) {
            modifier -= (int) (personality.getNeuroticism() * 10);
        }
        
        // Openness
        if (action == NPCAction.INVESTIGATE || action == NPCAction.OBSERVE) {
            modifier += (int) ((personality.getOpenness() - 0.5) * 25);
        }
        
        return modifier;
    }

    /**
     * Modificator bazat pe emotiile curente
     */
    private int getEmotionModifier(NPCEmotions emotions, NPCAction action) {
        int modifier = 0;
        
        // Fericire
        double happiness = emotions.getHappiness();
        if (action.isFriendly() || action == NPCAction.CELEBRATE || action == NPCAction.LAUGH) {
            modifier += (int) (happiness * 20);
        }
        
        // Tristete
        double sadness = emotions.getSadness();
        if (action == NPCAction.CRY || action == NPCAction.MOURN) {
            modifier += (int) (sadness * 25);
        }
        if (action.isSocialState()) {
            modifier -= (int) (sadness * 15);
        }
        
        // Furie
        double anger = emotions.getAnger();
        if (action.isAggressive() || action == NPCAction.ARGUE) {
            modifier += (int) (anger * 30);
        }
        if (action.isFriendly()) {
            modifier -= (int) (anger * 20);
        }
        
        // Frica
        double fear = emotions.getFear();
        if (action == NPCAction.FLEE || action == NPCAction.HIDE || action == NPCAction.CALL_HELP) {
            modifier += (int) (fear * 35);
        }
        if (action == NPCAction.ATTACK || action == NPCAction.INVESTIGATE) {
            modifier -= (int) (fear * 25);
        }
        
        // Surpriza
        double surprise = emotions.getSurprise();
        if (action == NPCAction.INVESTIGATE || action == NPCAction.OBSERVE) {
            modifier += (int) (surprise * 20);
        }
        
        // Dezgust
        double disgust = emotions.getDisgust();
        if (action == NPCAction.INSULT || action == NPCAction.ARGUE) {
            modifier += (int) (disgust * 15);
        }
        
        return modifier;
    }

    /**
     * Modificator bazat pe memorie si relatii
     */
    private int getMemoryModifier(AINPC npc, NPCContext context, NPCAction action) {
        int modifier = 0;
        
        if (context.getInteractingPlayer() == null) return modifier;
        
        int relationshipLevel = context.getRelationshipLevel();
        
        // Relatii pozitive favorizeaza actiuni prietenoase
        if (action.isFriendly()) {
            modifier += relationshipLevel / 5;
        }
        
        // Relatii negative favorizeaza actiuni negative
        if (action.isAggressive()) {
            if (relationshipLevel < -25) {
                modifier += Math.abs(relationshipLevel) / 3;
            } else {
                modifier -= relationshipLevel / 3;
            }
        }
        
        // Verifica amintiri specifice
        String status = context.getRelationshipStatus();
        switch (status) {
            case "ENEMY":
                if (action == NPCAction.ATTACK || action == NPCAction.THREATEN) modifier += 30;
                if (action == NPCAction.FLEE) modifier += 20;
                if (action.isFriendly()) modifier -= 40;
                break;
            case "CLOSE_FRIEND":
                if (action.isFriendly()) modifier += 25;
                if (action == NPCAction.TELL_STORY || action == NPCAction.SHARE_NEWS) modifier += 20;
                if (action.isAggressive()) modifier -= 50;
                break;
            case "FAMILY":
            case "SPOUSE":
                if (action.isFriendly()) modifier += 35;
                if (action == NPCAction.DEFEND) modifier += 40;
                if (action.isAggressive()) modifier -= 60;
                break;
        }
        
        return modifier;
    }

    /**
     * Modificator bazat pe contextul curent
     */
    private int getContextModifier(NPCContext context, NPCAction action) {
        int modifier = 0;
        
        // Pericol
        if (context.isInDanger()) {
            if (action == NPCAction.FLEE || action == NPCAction.CALL_HELP) modifier += 50;
            if (action == NPCAction.DEFEND || action == NPCAction.ATTACK) modifier += 30;
            if (action.getCategory() == NPCAction.ActionCategory.SOCIAL) modifier -= 30;
            if (action.getCategory() == NPCAction.ActionCategory.WORK) modifier -= 40;
        }
        
        // Noapte
        if ("NIGHT".equals(context.getTimeOfDay())) {
            if (action == NPCAction.SLEEP || action == NPCAction.REST) modifier += 35;
            if (action.isWorkState()) modifier -= 20;
        }
        
        // Dimineata
        if ("MORNING".equals(context.getTimeOfDay())) {
            if (action.getCategory() == NPCAction.ActionCategory.WORK) modifier += 20;
            if (action == NPCAction.GREET) modifier += 15;
        }
        
        // Vreme rea
        if ("RAIN".equals(context.getWeather()) || "THUNDER".equals(context.getWeather())) {
            if (!context.isIndoors()) {
                if (action == NPCAction.HIDE) modifier += 25;
                if (action.getCategory() == NPCAction.ActionCategory.WORK) modifier -= 15;
            }
        }
        
        // Foame
        if (context.getHungerLevel() < 30) {
            if (action == NPCAction.EAT) modifier += 40;
        }
        
        // Sanatate scazuta
        if (context.getHealthPercent() < 50) {
            if (action == NPCAction.REST || action == NPCAction.HEAL) modifier += 30;
            if (action == NPCAction.FLEE) modifier += 20;
        }
        
        // Familie aproape
        if (context.isFamilyNearby()) {
            if (action == NPCAction.SOCIALIZE || action == NPCAction.TALK) modifier += 25;
        }
        
        // La munca
        if (context.isAtWork()) {
            if (action.getCategory() == NPCAction.ActionCategory.WORK) modifier += 30;
        }
        
        // Acasa
        if (context.isAtHome()) {
            if (action == NPCAction.REST || action == NPCAction.EAT) modifier += 15;
        }
        
        // Jucator in apropiere vorbeste
        if (context.getInteractingPlayer() != null) {
            if (action == NPCAction.TALK || action == NPCAction.LISTEN) modifier += 40;
            if (action == NPCAction.GREET && context.getLastInteractionTime() < 5000) modifier += 30;
        }
        
        return modifier;
    }

    /**
     * Filtreaza actiunile bazat pe starea curenta
     */
    private List<NPCAction> filterActionsByState(NPCState state) {
        List<NPCAction> actions = new ArrayList<>();
        
        // Actiuni mereu disponibile
        actions.add(NPCAction.DO_NOTHING);
        
        switch (state) {
            case IDLE:
                actions.addAll(Arrays.asList(
                    NPCAction.WALK_RANDOM, NPCAction.OBSERVE, NPCAction.GREET,
                    NPCAction.START_WORK, NPCAction.SOCIALIZE, NPCAction.REST,
                    NPCAction.INVESTIGATE
                ));
                break;
            case TALKING:
                actions.addAll(Arrays.asList(
                    NPCAction.TALK, NPCAction.LISTEN, NPCAction.THANK,
                    NPCAction.TELL_STORY, NPCAction.SHARE_NEWS, NPCAction.GOSSIP,
                    NPCAction.COMPLIMENT, NPCAction.ARGUE, NPCAction.APOLOGIZE
                ));
                break;
            case WORKING:
                actions.addAll(Arrays.asList(
                    NPCAction.CONTINUE_WORK, NPCAction.FINISH_WORK,
                    NPCAction.CRAFT, NPCAction.REST
                ));
                break;
            case COMBAT:
                actions.addAll(Arrays.asList(
                    NPCAction.ATTACK, NPCAction.DEFEND, NPCAction.FLEE,
                    NPCAction.CALL_HELP, NPCAction.SURRENDER
                ));
                break;
            case FLEEING:
                actions.addAll(Arrays.asList(
                    NPCAction.FLEE, NPCAction.HIDE, NPCAction.CALL_HELP
                ));
                break;
            default:
                // Pentru alte stari, permite actiuni de baza
                actions.addAll(Arrays.asList(
                    NPCAction.WALK_RANDOM, NPCAction.OBSERVE, NPCAction.TALK,
                    NPCAction.REST
                ));
        }
        
        return actions;
    }

    /**
     * Obtine actiunea cu cel mai mare scor
     */
    private NPCAction getHighestScoringAction(Map<NPCAction, Integer> scores) {
        return scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(NPCAction.DO_NOTHING);
    }

    /**
     * Obtine top N actiuni recomandate
     */
    public List<NPCAction> getTopActions(AINPC npc, NPCContext context, int count) {
        Map<NPCAction, Integer> scores = calculateAllScores(npc, context);
        
        return scores.entrySet().stream()
            .sorted(Map.Entry.<NPCAction, Integer>comparingByValue().reversed())
            .limit(count)
            .map(Map.Entry::getKey)
            .toList();
    }

    /**
     * Obtine scorul pentru o actiune specifica
     */
    public int getActionScore(AINPC npc, NPCContext context, NPCAction action) {
        return calculateActionScore(npc, context, action);
    }
}
