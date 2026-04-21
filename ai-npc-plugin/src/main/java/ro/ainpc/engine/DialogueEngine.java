package ro.ainpc.engine;

import org.bukkit.entity.Player;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.ai.OllamaService;
import ro.ainpc.managers.MemoryManager;
import ro.ainpc.npc.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Motor de dialog pentru NPC-uri
 * Combina template-uri locale cu reformulare AI
 * Model: selectezi intentie -> alegi template -> adaugi variatie -> optional LLM reformuleaza
 */
public class DialogueEngine {

    private final AINPCPlugin plugin;
    private final OllamaService ollamaService;
    private final MemoryManager memoryManager;
    
    // Template-uri de dialog pe categorii
    private final Map<DialogueIntent, List<String>> templates;
    
    // Cache pentru raspunsuri recente (evita repetitii)
    private final Map<UUID, List<String>> recentResponses;

    public DialogueEngine(AINPCPlugin plugin, OllamaService ollamaService, MemoryManager memoryManager) {
        this.plugin = plugin;
        this.ollamaService = ollamaService;
        this.memoryManager = memoryManager;
        this.templates = new EnumMap<>(DialogueIntent.class);
        this.recentResponses = new HashMap<>();
        
        loadTemplates();
    }

    /**
     * Incarca template-urile de dialog
     */
    private void loadTemplates() {
        // GREET - Salutari
        templates.put(DialogueIntent.GREET, Arrays.asList(
            "Buna ziua, {player}!",
            "Salut! Ce te aduce pe aici?",
            "A, {player}! Ma bucur sa te vad.",
            "Bine ai venit, calatorule!",
            "Hei! Cum mai esti?",
            "Noroc, {player}! E o zi frumoasa, nu-i asa?"
        ));
        
        // GREET_FRIEND - Salutari pentru prieteni
        templates.put(DialogueIntent.GREET_FRIEND, Arrays.asList(
            "Ah, {player}! Ce bine ca te vad din nou!",
            "Prietenul meu! Cum ai fost?",
            "{player}! Tocmai ma gandeam la tine!",
            "Hei, vechiul meu prieten! Intra, intra!",
            "Ce surpriza placuta! {player} in persoana!"
        ));
        
        // GREET_STRANGER - Salutari pentru straini
        templates.put(DialogueIntent.GREET_STRANGER, Arrays.asList(
            "Hmm? Cine esti tu?",
            "Nu cred ca ne-am mai intalnit...",
            "Un calator nou? Interesant.",
            "Salut, strainule. Cu ce te pot ajuta?",
            "Nu te cunosc, dar bine ai venit."
        ));
        
        // FAREWELL - La revedere
        templates.put(DialogueIntent.FAREWELL, Arrays.asList(
            "Pa, {player}! Drum bun!",
            "Ne vedem curand!",
            "Ai grija de tine!",
            "La revedere! Sa mai treci pe aici!",
            "Ramas bun, calatorule!"
        ));
        
        // TRADE - Comert
        templates.put(DialogueIntent.TRADE, Arrays.asList(
            "Ce doresti sa cumperi?",
            "Am marfa buna azi!",
            "Hai sa vedem ce am pe aici...",
            "Preturi bune pentru prieteni!",
            "Ai venit sa faci afaceri? Perfect!"
        ));
        
        // WARN - Avertizare
        templates.put(DialogueIntent.WARN, Arrays.asList(
            "Ai grija! Am auzit ca e periculos pe aici.",
            "Fii atent, {player}. Nu e totul asa cum pare.",
            "Ti-as recomanda sa nu mergi singur noaptea...",
            "Am vazut creaturi ciudate prin padure. Fii prudent!",
            "Daca ma intrebi pe mine, as sta departe de acolo."
        ));
        
        // HELP - Ajutor
        templates.put(DialogueIntent.HELP, Arrays.asList(
            "Cu ce te pot ajuta?",
            "Ai nevoie de ceva?",
            "Spune-mi cum te pot ajuta.",
            "Sunt aici daca ai nevoie de mine.",
            "Ce pot face pentru tine?"
        ));
        
        // THANK - Multumire
        templates.put(DialogueIntent.THANK, Arrays.asList(
            "Multumesc frumos, {player}!",
            "Apreciez foarte mult!",
            "Esti prea bun cu mine!",
            "Nu stiu cum sa-ti multumesc!",
            "Chiar mi-ai facut ziua mai buna!"
        ));
        
        // ANGRY - Furios
        templates.put(DialogueIntent.ANGRY, Arrays.asList(
            "Ce vrei?! Nu am timp de prostii!",
            "Lasa-ma in pace!",
            "Nu sunt dispus sa vorbesc acum.",
            "Hmph! De ce ar trebui sa-ti raspund?",
            "Pleaca de aici inainte sa ma enervezi mai tare!"
        ));
        
        // SAD - Trist
        templates.put(DialogueIntent.SAD, Arrays.asList(
            "*oftez* Nu e cea mai buna zi pentru mine...",
            "Ma simt cam prost azi...",
            "Scuza-ma, nu prea am chef de vorba...",
            "Viata e grea uneori, stii?",
            "Am trecut prin momente mai bune..."
        ));
        
        // HAPPY - Fericit
        templates.put(DialogueIntent.HAPPY, Arrays.asList(
            "Ce zi minunata! Cum esti, {player}?",
            "Ma simt fantastic azi!",
            "Totul merge perfect! Si pentru tine?",
            "Viata e frumoasa, nu-i asa?",
            "Sunt atat de fericit/fericita ca te vad!"
        ));
        
        // SCARED - Speriat
        templates.put(DialogueIntent.SCARED, Arrays.asList(
            "C-cine e acolo?! A, tu esti, {player}...",
            "M-ai speriat! Ce vrei?",
            "Nu e sigur aici... ar trebui sa plecam...",
            "Ai auzit zgomotul ala? Ce a fost?",
            "Nu-mi place deloc atmosfera asta..."
        ));
        
        // WORK_TALK - Despre munca
        templates.put(DialogueIntent.WORK_TALK, Arrays.asList(
            "Munca merge bine azi.",
            "Sunt ocupat cu treburile, dar pot vorbi putin.",
            "Ai nevoie de serviciile mele?",
            "Meseria mea e {occupation}. Cu ce te pot ajuta?",
            "Am mult de lucru, dar pentru tine fac timp."
        ));
        
        // FAMILY_TALK - Despre familie
        templates.put(DialogueIntent.FAMILY_TALK, Arrays.asList(
            "Familia mea e totul pentru mine.",
            "Ai intrebat de {family_member}? E bine, multumesc!",
            "Copiii cresc repede... timpul zboara.",
            "Sotul/Sotia mea e cea mai buna persoana pe care o cunosc.",
            "Familia e cel mai important lucru in viata."
        ));
        
        // GOSSIP - Barfa
        templates.put(DialogueIntent.GOSSIP, Arrays.asList(
            "Ai auzit ce s-a intamplat cu {npc_name}?",
            "Nu spune nimanui, dar am auzit ca...",
            "Intre noi fie vorba...",
            "Stii ce se zice prin sat?",
            "Am aflat ceva interesant zilele trecute..."
        ));
        
        // STORY - Poveste
        templates.put(DialogueIntent.STORY, Arrays.asList(
            "Lasa-ma sa-ti povestesc ceva...",
            "Mi-am amintit de o intamplare...",
            "Odata, cand eram tanar/tanara...",
            "Stii legenda despre acest loc?",
            "Hai sa-ti spun o poveste interesanta..."
        ));
        
        // CONFUSED - Confuz (pentru intrebari din afara lumii)
        templates.put(DialogueIntent.CONFUSED, Arrays.asList(
            "Ce e aia? Nu inteleg...",
            "Hmm, nu am auzit niciodata de asa ceva.",
            "Vorbesti in ghicitori? Explica-mi.",
            "E un fel de... magie? Nu pricep.",
            "Suna ciudat. Ce vrei sa zici?"
        ));
        
        // CONFUSED cu context profesional
        templates.put(DialogueIntent.CONFUSED_PROFESSIONAL, Arrays.asList(
            "Ce e aia? Un fel de {professional_item}?",
            "Nu stiu ce inseamna, dar suna ca {professional_guess}.",
            "In meseria mea nu folosim asa ceva...",
            "Daca e ca un {professional_tool}, poate pot ajuta.",
            "Nu sunt sigur, dar pot incerca sa-ti fac ceva similar."
        ));
        
        // QUEST_OFFER - Ofera misiune
        templates.put(DialogueIntent.QUEST_OFFER, Arrays.asList(
            "Ai putea sa ma ajuti cu ceva?",
            "Am o problema si cred ca tu esti persoana potrivita.",
            "Daca ai timp, am nevoie de ajutorul tau.",
            "Te-ai oferi sa faci ceva pentru mine?",
            "Am o sarcina care s-ar potrivi aventurierului ca tine."
        ));
        
        // REFUSE - Refuz
        templates.put(DialogueIntent.REFUSE, Arrays.asList(
            "Nu, multumesc.",
            "Nu pot face asta.",
            "Imi pare rau, dar nu.",
            "Nu e posibil acum.",
            "Refuz sa fac asta."
        ));
        
        // AGREE - Acord
        templates.put(DialogueIntent.AGREE, Arrays.asList(
            "Da, sigur!",
            "Bineinteles!",
            "Cu placere!",
            "Consider ca e o idee buna.",
            "Sunt de acord!"
        ));
    }

    /**
     * Selecteaza intentia de dialog bazata pe context
     */
    public DialogueIntent selectIntent(AINPC npc, NPCContext context, String playerMessage) {
        NPCEmotions emotions = npc.getEmotions();
        String dominantEmotion = emotions.getDominantEmotion();
        String relationStatus = context.getRelationshipStatus();
        
        // Verifica daca mesajul e despre ceva necunoscut (din afara lumii)
        if (isOutOfWorldQuestion(playerMessage)) {
            if (npc.getOccupation() != null && !npc.getOccupation().isEmpty()) {
                return DialogueIntent.CONFUSED_PROFESSIONAL;
            }
            return DialogueIntent.CONFUSED;
        }
        
        // Verifica cuvinte cheie in mesajul jucatorului
        String msg = playerMessage.toLowerCase();
        
        if (containsAny(msg, "salut", "buna", "hei", "noroc", "servus")) {
            return selectGreetIntent(relationStatus);
        }
        
        if (containsAny(msg, "pa", "la revedere", "adio", "pe curand")) {
            return DialogueIntent.FAREWELL;
        }
        
        if (containsAny(msg, "cumpara", "vinde", "pret", "comert", "marfa", "afaceri")) {
            return DialogueIntent.TRADE;
        }
        
        if (containsAny(msg, "ajutor", "ajuta", "nevoie")) {
            return DialogueIntent.HELP;
        }
        
        if (containsAny(msg, "multumesc", "mersi", "apreciez")) {
            return DialogueIntent.THANK;
        }
        
        if (containsAny(msg, "familie", "copii", "sot", "sotie", "parinti")) {
            return DialogueIntent.FAMILY_TALK;
        }
        
        if (containsAny(msg, "munca", "meserie", "job", "profesie")) {
            return DialogueIntent.WORK_TALK;
        }
        
        if (containsAny(msg, "poveste", "povesteste", "intamplat")) {
            return DialogueIntent.STORY;
        }
        
        if (containsAny(msg, "stii", "auzit", "zvon", "barfa")) {
            return DialogueIntent.GOSSIP;
        }
        
        if (containsAny(msg, "misiune", "quest", "sarcina", "ajutor")) {
            return DialogueIntent.QUEST_OFFER;
        }
        
        // Selecteaza bazat pe emotia dominanta (NPCEmotions returneaza lowercase)
        return switch (dominantEmotion.toLowerCase()) {
            case "anger" -> DialogueIntent.ANGRY;
            case "sadness" -> DialogueIntent.SAD;
            case "happiness" -> DialogueIntent.HAPPY;
            case "fear" -> DialogueIntent.SCARED;
            default -> DialogueIntent.GREET;
        };
    }

    /**
     * Selecteaza tipul de salut bazat pe relatie
     */
    private DialogueIntent selectGreetIntent(String relationStatus) {
        return switch (relationStatus) {
            case "CLOSE_FRIEND", "FRIEND", "FAMILY", "SPOUSE" -> DialogueIntent.GREET_FRIEND;
            case "STRANGER" -> DialogueIntent.GREET_STRANGER;
            default -> DialogueIntent.GREET;
        };
    }

    /**
     * Verifica daca intrebarea e despre ceva din afara lumii Minecraft
     */
    private boolean isOutOfWorldQuestion(String message) {
        String msg = message.toLowerCase();
        return containsAny(msg, 
            "telefon", "internet", "computer", "masina", "avion", "televizor",
            "electricitate", "bitcoin", "social media", "facebook", "instagram",
            "email", "website", "app", "aplicatie", "program", "software"
        );
    }

    /**
     * Verifica daca textul contine oricare din cuvintele date
     */
    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    /**
     * Genereaza raspuns de dialog
     */
    public CompletableFuture<String> generateResponse(AINPC npc, NPCContext context, String playerMessage) {
        DialogueIntent intent = selectIntent(npc, context, playerMessage);
        
        // Selecteaza template
        String template = selectTemplate(npc, intent);
        
        // Proceseaza placeholdere
        String processed = processTemplate(template, npc, context);
        
        // Decide daca trimitem la AI pentru reformulare
        boolean useAI = shouldUseAI(intent, context);
        
        if (useAI && ollamaService.isAvailable()) {
            return reformulateWithAI(npc, context, intent, processed, playerMessage);
        }
        
        // Returneaza template-ul procesat
        return CompletableFuture.completedFuture(processed);
    }

    /**
     * Selecteaza un template evitand repetitiile
     */
    private String selectTemplate(AINPC npc, DialogueIntent intent) {
        List<String> intentTemplates = templates.get(intent);
        if (intentTemplates == null || intentTemplates.isEmpty()) {
            intentTemplates = templates.get(DialogueIntent.GREET);
        }
        
        List<String> recent = recentResponses.computeIfAbsent(npc.getUuid(), k -> new ArrayList<>());
        
        // Filtreaza template-urile folosite recent
        List<String> available = intentTemplates.stream()
            .filter(t -> !recent.contains(t))
            .toList();
        
        if (available.isEmpty()) {
            recent.clear();
            available = intentTemplates;
        }
        
        // Selecteaza random
        String selected = available.get(new Random().nextInt(available.size()));
        
        // Adauga la recente
        recent.add(selected);
        if (recent.size() > 5) {
            recent.remove(0);
        }
        
        return selected;
    }

    /**
     * Proceseaza placeholderele din template
     */
    private String processTemplate(String template, AINPC npc, NPCContext context) {
        String result = template;
        
        // Player name
        if (context.getInteractingPlayer() != null) {
            result = result.replace("{player}", context.getInteractingPlayer().getName());
        }
        
        // NPC info
        result = result.replace("{occupation}", npc.getOccupation() != null ? npc.getOccupation() : "locuitor");
        result = result.replace("{name}", npc.getName());
        
        // Professional items based on occupation
        String occupation = npc.getOccupation() != null ? npc.getOccupation().toLowerCase() : "";
        String professionalItem = getProfessionalItem(occupation);
        String professionalGuess = getProfessionalGuess(occupation);
        String professionalTool = getProfessionalTool(occupation);
        
        result = result.replace("{professional_item}", professionalItem);
        result = result.replace("{professional_guess}", professionalGuess);
        result = result.replace("{professional_tool}", professionalTool);
        
        // Family member (random)
        result = result.replace("{family_member}", getRandomFamilyMember());
        
        // Other NPC name (placeholder)
        result = result.replace("{npc_name}", "vecinul");
        
        return result;
    }

    private String getProfessionalItem(String occupation) {
        return switch (occupation) {
            case "fierar", "blacksmith" -> "unealta de metal";
            case "fermier", "farmer" -> "samanta sau unelte agricole";
            case "pescar", "fisherman" -> "nada sau plasa";
            case "bibliotecar", "librarian" -> "carte veche";
            case "miner" -> "minereu rar";
            case "soldat", "guard" -> "arma sau armura";
            default -> "obiect ciudat";
        };
    }

    private String getProfessionalGuess(String occupation) {
        return switch (occupation) {
            case "fierar", "blacksmith" -> "un tip nou de otel";
            case "fermier", "farmer" -> "o planta straina";
            case "pescar", "fisherman" -> "un peste exotic";
            case "bibliotecar", "librarian" -> "cunostinte stravechi";
            case "miner" -> "un cristal magic";
            case "soldat", "guard" -> "o tehnica de lupta";
            default -> "ceva misterios";
        };
    }

    private String getProfessionalTool(String occupation) {
        return switch (occupation) {
            case "fierar", "blacksmith" -> "ciocan";
            case "fermier", "farmer" -> "sapa";
            case "pescar", "fisherman" -> "undita";
            case "bibliotecar", "librarian" -> "condei";
            case "miner" -> "tarnacop";
            case "soldat", "guard" -> "sabie";
            default -> "unealta";
        };
    }

    private String getRandomFamilyMember() {
        String[] members = {"sotul meu", "sotia mea", "copiii", "mama", "tata", "fratele meu", "sora mea"};
        return members[new Random().nextInt(members.length)];
    }

    /**
     * Decide daca sa folosim AI pentru reformulare
     */
    private boolean shouldUseAI(DialogueIntent intent, NPCContext context) {
        // Folosim AI pentru conversatii mai lungi sau complexe
        return switch (intent) {
            case STORY, GOSSIP, CONFUSED, CONFUSED_PROFESSIONAL, QUEST_OFFER -> true;
            default -> context.getLastPlayerMessage() != null && 
                       context.getLastPlayerMessage().length() > 20;
        };
    }

    /**
     * Reformuleaza raspunsul folosind AI
     */
    private CompletableFuture<String> reformulateWithAI(AINPC npc, NPCContext context, 
                                                         DialogueIntent intent, String template, 
                                                         String playerMessage) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Esti un NPC intr-un joc medieval.\n");
        prompt.append(npc.generateContextDescription()).append("\n");
        prompt.append(context.generateContextDescription()).append("\n");
        prompt.append("\nIntentia dialogului: ").append(intent.getDescription()).append("\n");
        prompt.append("Template de baza: ").append(template).append("\n");
        prompt.append("\nJucatorul a spus: ").append(playerMessage).append("\n");
        prompt.append("\nReformuleaza template-ul pastrand sensul dar facandu-l mai natural si specific personajului.");
        prompt.append(" Raspunde DOAR cu replica NPC-ului, maxim 2 propozitii, in romana.");
        
        return ollamaService.generateAsync(prompt.toString())
            .thenApply(response -> {
                if (response == null || response.isEmpty()) {
                    return template;
                }
                return response;
            })
            .exceptionally(e -> {
                plugin.debug("Eroare AI reformulare: " + e.getMessage());
                return template;
            });
    }

    /**
     * Genereaza raspuns rapid (fara AI)
     */
    public String generateQuickResponse(AINPC npc, NPCContext context, DialogueIntent intent) {
        String template = selectTemplate(npc, intent);
        return processTemplate(template, npc, context);
    }

    /**
     * Enum pentru intentiile de dialog
     */
    public enum DialogueIntent {
        GREET("Salut simplu"),
        GREET_FRIEND("Salut prieten"),
        GREET_STRANGER("Salut strain"),
        FAREWELL("La revedere"),
        TRADE("Comert"),
        WARN("Avertizare"),
        HELP("Oferire ajutor"),
        THANK("Multumire"),
        ANGRY("Raspuns furios"),
        SAD("Raspuns trist"),
        HAPPY("Raspuns fericit"),
        SCARED("Raspuns speriat"),
        WORK_TALK("Despre munca"),
        FAMILY_TALK("Despre familie"),
        GOSSIP("Barfa"),
        STORY("Povestire"),
        CONFUSED("Confuz - nu intelege"),
        CONFUSED_PROFESSIONAL("Confuz - interpreteaza prin profesie"),
        QUEST_OFFER("Ofera misiune"),
        REFUSE("Refuz"),
        AGREE("Acord");

        private final String description;

        DialogueIntent(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
