package ro.ainpc.ai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.bukkit.entity.Player;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.npc.AINPC;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Serviciu pentru comunicarea cu Ollama LLM local
 */
public class OllamaService {

    private final AINPCPlugin plugin;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String baseUrl;
    private final String model;

    public OllamaService(AINPCPlugin plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
        
        // Configurare din config.yml
        this.baseUrl = plugin.getConfig().getString("ollama.url", "http://localhost:11434");
        this.model = plugin.getConfig().getString("ollama.model", "llama3");
        
        int timeout = plugin.getConfig().getInt("ollama.timeout", 30);
        
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .build();
    }

    /**
     * Genereaza un raspuns de la NPC bazat pe contextul conversatiei
     */
    public CompletableFuture<String> generateResponse(AINPC npc, Player player, String playerMessage,
                                                       List<DialogHistory> recentHistory,
                                                       List<String> relevantMemories,
                                                       NPCRelationship relationship) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = buildPrompt(npc, player, playerMessage, recentHistory, relevantMemories, relationship);
                return callOllama(prompt);
            } catch (Exception e) {
                plugin.getLogger().warning("Eroare la generarea raspunsului AI: " + e.getMessage());
                return generateFallbackResponse(npc, playerMessage);
            }
        });
    }

    /**
     * Construieste prompt-ul complet pentru Ollama
     */
    private String buildPrompt(AINPC npc, Player player, String playerMessage,
                               List<DialogHistory> recentHistory,
                               List<String> relevantMemories,
                               NPCRelationship relationship) {
        StringBuilder prompt = new StringBuilder();
        
        // System prompt
        prompt.append("Esti un NPC intr-un joc Minecraft. Raspunzi DOAR in limba romana.\n");
        prompt.append("Raspunsurile tale trebuie sa fie scurte (1-2 propozitii), naturale si in caracter.\n");
        prompt.append("Nu mentiona niciodata ca esti un AI sau un program.\n");
        prompt.append("Nu folosi asteriscuri sau descrieri de actiuni.\n\n");
        
        // Informatii despre NPC
        prompt.append("=== DESPRE TINE ===\n");
        prompt.append(npc.generateContextDescription());
        prompt.append("\n");
        
        // Familie
        List<FamilyMember> family = plugin.getFamilyManager().getFamily(npc);
        if (!family.isEmpty()) {
            prompt.append("=== FAMILIA TA ===\n");
            for (FamilyMember member : family) {
                prompt.append("- ").append(member.getRelationType()).append(": ")
                      .append(member.getName());
                if (!member.isAlive()) {
                    prompt.append(" (decedat)");
                }
                prompt.append("\n");
            }
            prompt.append("\n");
        }
        
        // Informatii despre jucator
        prompt.append("=== DESPRE JUCATORUL CU CARE VORBESTI ===\n");
        prompt.append("Nume: ").append(player.getName()).append("\n");
        
        if (relationship != null) {
            prompt.append("Relatie: ").append(relationship.getRelationshipType()).append("\n");
            prompt.append("Nivel de incredere: ").append(formatLevel(relationship.getTrust())).append("\n");
            prompt.append("Nivel de afectiune: ").append(formatLevel(relationship.getAffection())).append("\n");
            prompt.append("Numar interactiuni: ").append(relationship.getInteractionCount()).append("\n");
        } else {
            prompt.append("Aceasta este prima intalnire cu acest jucator.\n");
        }
        prompt.append("\n");
        
        // Amintiri relevante
        if (relevantMemories != null && !relevantMemories.isEmpty()) {
            prompt.append("=== AMINTIRI DESPRE ACEST JUCATOR ===\n");
            for (String memory : relevantMemories) {
                prompt.append("- ").append(memory).append("\n");
            }
            prompt.append("\n");
        }
        
        // Istoric conversatie recenta
        if (recentHistory != null && !recentHistory.isEmpty()) {
            prompt.append("=== CONVERSATIA RECENTA ===\n");
            for (DialogHistory entry : recentHistory) {
                prompt.append(player.getName()).append(": ").append(entry.getPlayerMessage()).append("\n");
                prompt.append(npc.getName()).append(": ").append(entry.getNpcResponse()).append("\n");
            }
            prompt.append("\n");
        }
        
        // Mesajul curent
        prompt.append("=== MESAJUL JUCATORULUI ===\n");
        prompt.append(player.getName()).append(": ").append(playerMessage).append("\n\n");
        
        // Instructiuni finale
        prompt.append("Raspunde ca ").append(npc.getName()).append(" in romana, scurt si natural.\n");
        prompt.append("Tine cont de starea ta emotionala (").append(npc.getEmotions().getShortDescription()).append(").\n");
        prompt.append(npc.getName()).append(": ");
        
        return prompt.toString();
    }

    /**
     * Apeleaza API-ul Ollama
     */
    private String callOllama(String prompt) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("prompt", prompt);
        requestBody.addProperty("stream", false);
        
        // Parametri de generare
        JsonObject options = new JsonObject();
        options.addProperty("temperature", plugin.getConfig().getDouble("ollama.temperature", 0.7));
        options.addProperty("num_predict", plugin.getConfig().getInt("ollama.max_tokens", 150));
        options.addProperty("stop", "\n");
        requestBody.add("options", options);

        RequestBody body = RequestBody.create(
            gson.toJson(requestBody),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(baseUrl + "/api/generate")
            .post(body)
            .build();

        plugin.debug("Trimitere cerere Ollama: " + prompt.substring(0, Math.min(100, prompt.length())) + "...");

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Ollama a returnat eroare: " + response.code());
            }

            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            String generatedText = jsonResponse.get("response").getAsString().trim();
            
            // Curata raspunsul
            generatedText = cleanResponse(generatedText);
            
            plugin.debug("Raspuns Ollama: " + generatedText);
            
            return generatedText;
        }
    }

    /**
     * Curata si formateaza raspunsul
     */
    private String cleanResponse(String response) {
        // Elimina ghilimele de la inceput/sfarsit
        response = response.replaceAll("^\"|\"$", "");
        
        // Elimina prefixul cu numele NPC daca exista
        if (response.contains(":")) {
            int colonIndex = response.indexOf(":");
            if (colonIndex < 30) { // Doar daca e la inceput
                response = response.substring(colonIndex + 1).trim();
            }
        }
        
        // Limiteaza lungimea
        if (response.length() > 200) {
            int lastPeriod = response.lastIndexOf(".", 200);
            if (lastPeriod > 50) {
                response = response.substring(0, lastPeriod + 1);
            } else {
                response = response.substring(0, 200) + "...";
            }
        }
        
        return response;
    }

    /**
     * Genereaza un raspuns de backup cand Ollama nu e disponibil
     */
    private String generateFallbackResponse(AINPC npc, String playerMessage) {
        String emotion = npc.getEmotions().getDominantEmotion();
        String[] responses;
        
        // Raspunsuri bazate pe emotie
        switch (emotion) {
            case "happiness" -> responses = new String[]{
                "Ce bucurie sa te vad!",
                "Ma bucur sa stam de vorba.",
                "Hm, interesant ce spui tu."
            };
            case "sadness" -> responses = new String[]{
                "*oftez* Nu e cea mai buna zi pentru mine...",
                "Ma simt cam trist astazi.",
                "Imi pare rau, nu sunt in apele mele."
            };
            case "anger" -> responses = new String[]{
                "Nu am chef de vorba acum.",
                "Ce vrei?",
                "Lasa-ma in pace."
            };
            case "fear" -> responses = new String[]{
                "Cine-i acolo? Ah, tu esti...",
                "M-ai speriat!",
                "Nu stiu daca ar trebui sa vorbim aici..."
            };
            default -> responses = new String[]{
                "Hmm, inteleg.",
                "Asa-i.",
                "Ce mai faci pe aici?"
            };
        }
        
        return responses[(int) (Math.random() * responses.length)];
    }

    /**
     * Verifica daca Ollama este disponibil
     */
    public CompletableFuture<Boolean> checkConnection() {
        return CompletableFuture.supplyAsync(() -> {
            Request request = new Request.Builder()
                .url(baseUrl + "/api/tags")
                .get()
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            } catch (IOException e) {
                return false;
            }
        });
    }

    /**
     * Verifica sincron daca Ollama este disponibil (pentru verificari rapide)
     */
    public boolean isAvailable() {
        Request request = new Request.Builder()
            .url(baseUrl + "/api/tags")
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Genereaza un raspuns asincron (wrapper simplificat)
     */
    public CompletableFuture<String> generateAsync(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return callOllama(prompt);
            } catch (IOException e) {
                plugin.getLogger().warning("Eroare la generarea AI: " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * Analizeaza sentimentul unui mesaj
     */
    public CompletableFuture<String> analyzeSentiment(String message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = """
                    Analizeaza sentimentul urmatorului mesaj si raspunde cu UN SINGUR CUVANT 
                    din urmatoarele: positive, negative, neutral, question, greeting, insult, compliment, threat
                    
                    Mesaj: "%s"
                    
                    Sentiment:""".formatted(message);
                
                String result = callOllama(prompt).toLowerCase().trim();
                
                // Extrage primul cuvant valid
                String[] validSentiments = {"positive", "negative", "neutral", "question", "greeting", "insult", "compliment", "threat"};
                for (String sentiment : validSentiments) {
                    if (result.contains(sentiment)) {
                        return sentiment;
                    }
                }
                
                return "neutral";
                
            } catch (Exception e) {
                return "neutral";
            }
        });
    }

    private String formatLevel(double value) {
        if (value > 0.8) return "foarte ridicat";
        if (value > 0.6) return "ridicat";
        if (value > 0.4) return "mediu";
        if (value > 0.2) return "scazut";
        return "foarte scazut";
    }

    // Clase interne pentru structuri de date

    public static class DialogHistory {
        private final String playerMessage;
        private final String npcResponse;
        private final long timestamp;

        public DialogHistory(String playerMessage, String npcResponse, long timestamp) {
            this.playerMessage = playerMessage;
            this.npcResponse = npcResponse;
            this.timestamp = timestamp;
        }

        public String getPlayerMessage() { return playerMessage; }
        public String getNpcResponse() { return npcResponse; }
        public long getTimestamp() { return timestamp; }
    }

    public static class NPCRelationship {
        private double affection;
        private double trust;
        private double respect;
        private double familiarity;
        private int interactionCount;
        private String relationshipType;

        public double getAffection() { return affection; }
        public void setAffection(double affection) { this.affection = affection; }
        
        public double getTrust() { return trust; }
        public void setTrust(double trust) { this.trust = trust; }
        
        public double getRespect() { return respect; }
        public void setRespect(double respect) { this.respect = respect; }
        
        public double getFamiliarity() { return familiarity; }
        public void setFamiliarity(double familiarity) { this.familiarity = familiarity; }
        
        public int getInteractionCount() { return interactionCount; }
        public void setInteractionCount(int interactionCount) { this.interactionCount = interactionCount; }
        
        public String getRelationshipType() { return relationshipType; }
        public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }
    }

    public static class FamilyMember {
        private final String name;
        private final String relationType;
        private final boolean alive;
        private final Integer relatedNpcId;

        public FamilyMember(String name, String relationType, boolean alive, Integer relatedNpcId) {
            this.name = name;
            this.relationType = relationType;
            this.alive = alive;
            this.relatedNpcId = relatedNpcId;
        }

        public String getName() { return name; }
        public String getRelationType() { return relationType; }
        public boolean isAlive() { return alive; }
        public Integer getRelatedNpcId() { return relatedNpcId; }
    }
}
