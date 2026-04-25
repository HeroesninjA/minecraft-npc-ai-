package ro.ainpc.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.managers.FamilyMemberRecord;
import ro.ainpc.npc.AINPC;
import ro.ainpc.topology.TopologyConsensus;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Serviciu pentru comunicarea cu OpenAI Responses API.
 */
public class OpenAIService {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final AINPCPlugin plugin;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;
    private final int writeTimeoutSeconds;
    private final int maxOutputTokens;
    private final double temperature;
    private final boolean storeResponses;
    private volatile long offlineRetryAfterMillis;
    private volatile boolean offlineMode;
    private volatile ConnectionStatus lastConnectionStatus;

    public OpenAIService(AINPCPlugin plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
        this.baseUrl = sanitizeBaseUrl(plugin.getConfig().getString("openai.base_url", "https://api.openai.com/v1"));
        this.apiKey = sanitizeSecret(
            plugin.getConfig().getString("openai.api_key", ""),
            System.getenv("OPENAI_API_KEY")
        );
        this.model = plugin.getConfig().getString("openai.model", "gpt-5.4-nano");
        this.connectTimeoutSeconds = plugin.getConfig().getInt("openai.connect_timeout", 10);
        this.readTimeoutSeconds = plugin.getConfig().getInt("openai.read_timeout", 120);
        this.writeTimeoutSeconds = plugin.getConfig().getInt("openai.write_timeout", 30);
        this.maxOutputTokens = plugin.getConfig().getInt("openai.max_output_tokens", 150);
        this.temperature = plugin.getConfig().getDouble("openai.temperature", 0.7D);
        this.storeResponses = plugin.getConfig().getBoolean("openai.store", false);

        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
            .build();
        this.lastConnectionStatus = ConnectionStatus.unchecked(model, List.of(baseUrl));

        logConfigurationDiagnostics();
    }

    public CompletableFuture<String> generateResponse(DialogManager.DialogRequest request,
                                                      List<DialogHistory> recentHistory,
                                                      List<String> relevantMemories,
                                                      NPCRelationship relationship,
                                                      DialogManager.PromptDbContext dbContext) {
        return capturePromptSnapshot(request).thenCompose(snapshot ->
            CompletableFuture.supplyAsync(() -> {
                if (isInOfflineBackoffWindow()) {
                    diagInfo("Sar peste request catre OpenAI din cauza backoff-ului activ. Mai sunt "
                        + Math.max(0L, (offlineRetryAfterMillis - System.currentTimeMillis()) / 1000L) + "s.");
                    return generateFallbackResponse(snapshot);
                }

                try {
                    String prompt = buildPrompt(snapshot, recentHistory, relevantMemories, relationship, dbContext);
                    String response = callOpenAI(prompt, snapshot.npcName());
                    clearOfflineState();
                    return response;
                } catch (Exception e) {
                    handleGenerationFailure(e);
                    return generateFallbackResponse(snapshot);
                }
            })
        );
    }

    private String buildPrompt(PromptSnapshot snapshot,
                               List<DialogHistory> recentHistory,
                               List<String> relevantMemories,
                               NPCRelationship relationship,
                               DialogManager.PromptDbContext dbContext) {
        StringBuilder prompt = new StringBuilder();
        NpcFactResolver.NpcFacts facts = buildNpcFacts(snapshot);

        prompt.append("Esti un NPC intr-un joc Minecraft. Raspunzi DOAR in limba romana.\n");
        prompt.append("Raspunsurile tale trebuie sa fie scurte (1-2 propozitii), naturale si in caracter.\n");
        prompt.append("Nu mentiona niciodata ca esti un AI sau un program.\n");
        prompt.append("Nu folosi asteriscuri sau descrieri de actiuni.\n");
        prompt.append("Conversatia trebuie sa para reala si spontana, nu un scenariu scris.\n");
        prompt.append("Nu forta questuri, povesti, lore sau misiuni daca jucatorul nu le cere explicit.\n");
        prompt.append("Raspunde la ce spune jucatorul acum, ca intr-o conversatie vie.\n");
        prompt.append("NPC_DATA este sursa ta unica de adevar despre personaj.\n");
        prompt.append("Cand jucatorul intreaba cine esti, ce meserie ai, ce faci, cum te simti sau unde esti, foloseste exact campurile relevante din NPC_DATA.\n");
        prompt.append("Daca jucatorul cere mai multe fapte in acelasi mesaj, raspunde la toate in ordinea in care au fost cerute.\n");
        prompt.append("Nu inventa detalii care lipsesc din NPC_DATA sau din context.\n\n");

        prompt.append("=== NPC_DATA ===\n");
        prompt.append("npc_name: ").append(snapshot.npcName()).append("\n");
        prompt.append("npc_profession: ").append(valueOrUnknown(snapshot.occupation())).append("\n");
        prompt.append("npc_emotional_state: ").append(valueOrUnknown(snapshot.emotionShortDescription())).append("\n");
        prompt.append("npc_current_state: ").append(valueOrUnknown(snapshot.currentState())).append("\n");
        prompt.append("npc_current_activity: ").append(valueOrUnknown(snapshot.currentActivity())).append("\n");
        prompt.append("npc_location: ").append(valueOrUnknown(snapshot.locationDescription())).append("\n");
        prompt.append("fact_name_answer: ").append("Sunt ").append(snapshot.npcName()).append(".\n");
        prompt.append("fact_profession_answer: ").append(
            facts.occupation() == null || facts.occupation().isBlank()
                ? "Nu stiu sigur."
                : "Sunt " + facts.occupation() + "."
        ).append("\n");
        prompt.append("fact_state_answer: ").append(
            facts.emotionalState() == null || facts.emotionalState().isBlank()
                ? "Nu stiu sigur."
                : "Ma simt " + facts.emotionalState() + "."
        ).append("\n");
        prompt.append("fact_activity_answer: ").append(
            facts.currentActivity() == null || facts.currentActivity().isBlank()
                ? "Nu stiu sigur."
                : capitalizeSentence(facts.currentActivity()) + "."
        ).append("\n");
        prompt.append("fact_location_answer: ").append(
            facts.locationDescription() == null || facts.locationDescription().isBlank()
                ? "Nu stiu sigur."
                : "Sunt in " + facts.locationDescription() + "."
        ).append("\n\n");

        prompt.append("=== PROFIL PERSISTENT DIN BAZA DE DATE ===\n");
        prompt.append("profil_creat: ").append(snapshot.profileCreated() ? "da" : "nu").append("\n");
        prompt.append("profil_sursa: ").append(valueOrUnknown(snapshot.profileSource())).append("\n");
        prompt.append("profil_versiune: ").append(snapshot.profileVersion()).append("\n");
        prompt.append("profil_rezumat: ").append(valueOrUnknown(snapshot.profileSummary())).append("\n");
        prompt.append("profil_traits: ").append(joinTraits(snapshot.traitIds())).append("\n");
        prompt.append("profil_json_db: ").append(abbreviate(snapshot.profileDataJson(), 1200)).append("\n\n");

        prompt.append("=== DESPRE TINE ===\n");
        prompt.append(snapshot.npcDescription()).append("\n");

        if (!snapshot.environmentDescription().isBlank()) {
            prompt.append("=== MEDIUL TAU IMEDIAT ===\n");
            prompt.append(snapshot.environmentDescription()).append("\n");

            if (!snapshot.topologyConsensusBlock().isBlank()) {
                prompt.append("=== CONSENS TOPOLOGIC ===\n");
                prompt.append(snapshot.topologyConsensusBlock()).append("\n");
            }
        }

        if (!snapshot.familyMembers().isEmpty()) {
            prompt.append("=== FAMILIA TA ===\n");
            for (FamilyMemberSnapshot member : snapshot.familyMembers()) {
                prompt.append("- ").append(member.relationType()).append(": ")
                    .append(member.name());
                if (!member.alive()) {
                    prompt.append(" (decedat)");
                }
                prompt.append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("=== CONTEXTUL CONVERSATIEI ===\n");
        prompt.append("Tip interactiune: ")
            .append(snapshot.explicitConversation() ? "conversatie activa" : "mesaj auzit din proximitate")
            .append("\n");
        prompt.append("Adresare directa: ").append(snapshot.directAddress() ? "da" : "nu").append("\n");
        prompt.append("Motiv selectie: ").append(snapshot.triggerReason()).append("\n");
        prompt.append("Distanta fata de jucator: ")
            .append(String.format(Locale.ROOT, "%.1f", snapshot.distanceToNpc()))
            .append(" blocuri\n");
        prompt.append("NPC-uri in apropiere: ").append(snapshot.nearbyNpcCount()).append("\n\n");

        prompt.append("=== DESPRE JUCATORUL CU CARE VORBESTI ===\n");
        prompt.append("Nume: ").append(snapshot.playerName()).append("\n");

        if (relationship != null) {
            prompt.append("Relatie: ")
                .append(relationship.getRelationshipType() == null ? "necunoscuta" : relationship.getRelationshipType())
                .append("\n");
            prompt.append("Nivel de incredere: ").append(formatLevel(relationship.getTrust())).append("\n");
            prompt.append("Nivel de afectiune: ").append(formatLevel(relationship.getAffection())).append("\n");
            prompt.append("Numar interactiuni: ").append(relationship.getInteractionCount()).append("\n");
        } else {
            prompt.append("Aceasta este prima intalnire cu acest jucator.\n");
        }
        prompt.append("\n");

        prompt.append("=== CONTEXT RELATIONAL DIN BAZA DE DATE ===\n");
        if (dbContext != null) {
            prompt.append("Numar total amintiri despre jucator: ").append(dbContext.totalMemoryCount()).append("\n");
            prompt.append("Impact emotional mediu al amintirilor: ")
                .append(describeMemoryImpact(dbContext.weightedMemoryImpact()))
                .append(" (")
                .append(String.format(Locale.ROOT, "%.2f", dbContext.weightedMemoryImpact()))
                .append(")\n");
        } else {
            prompt.append("Nu exista statistici DB suplimentare pentru acest schimb.\n");
        }
        prompt.append("Numar replici recente extrase din istoric: ")
            .append(recentHistory == null ? 0 : recentHistory.size())
            .append("\n");
        prompt.append("Numar amintiri relevante extrase: ")
            .append(relevantMemories == null ? 0 : relevantMemories.size())
            .append("\n\n");

        if (relevantMemories != null && !relevantMemories.isEmpty()) {
            prompt.append("=== AMINTIRI DESPRE ACEST JUCATOR ===\n");
            for (String memory : relevantMemories) {
                prompt.append("- ").append(memory).append("\n");
            }
            prompt.append("\n");
        }

        if (recentHistory != null && !recentHistory.isEmpty()) {
            prompt.append("=== CONVERSATIA RECENTA ===\n");
            for (DialogHistory entry : recentHistory) {
                prompt.append(snapshot.playerName()).append(": ").append(entry.getPlayerMessage()).append("\n");
                prompt.append(snapshot.npcName()).append(": ").append(entry.getNpcResponse()).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("=== MESAJUL JUCATORULUI ===\n");
        prompt.append(snapshot.playerName()).append(": ").append(snapshot.playerMessage()).append("\n\n");

        prompt.append("Raspunde ca ").append(snapshot.npcName()).append(" in romana, scurt si natural.\n");
        prompt.append("Tine cont de starea ta emotionala (").append(snapshot.emotionShortDescription()).append(").\n");
        prompt.append(snapshot.npcName()).append(": ");

        return prompt.toString();
    }

    private String callOpenAI(String prompt, String expectedSpeakerName) throws IOException {
        if (!hasApiKey()) {
            throw new IOException("Cheia API OpenAI lipseste. Seteaza openai.api_key sau OPENAI_API_KEY.");
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("input", prompt);
        requestBody.addProperty("temperature", temperature);
        requestBody.addProperty("max_output_tokens", maxOutputTokens);
        requestBody.addProperty("store", storeResponses);

        JsonObject textConfig = new JsonObject();
        JsonObject format = new JsonObject();
        format.addProperty("type", "text");
        textConfig.add("format", format);
        requestBody.add("text", textConfig);

        if (isPromptSummaryEnabled()) {
            diagInfo("Cerere Responses: model=" + model
                + ", max_output_tokens=" + maxOutputTokens
                + ", temperature=" + temperature
                + ", prompt_chars=" + prompt.length()
                + ", prompt_preview=\"" + abbreviate(prompt, 180) + "\"");
        }

        Request request = newRequestBuilder(baseUrl + "/responses")
            .post(RequestBody.create(gson.toJson(requestBody), JSON))
            .build();

        plugin.debug("Trimitere cerere OpenAI: " + prompt.substring(0, Math.min(100, prompt.length())) + "...");
        long startedAt = System.nanoTime();

        try (Response response = httpClient.newCall(request).execute()) {
            long elapsedMs = nanosToMillis(startedAt);
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                String errorMessage = extractOpenAIErrorMessage(responseBody);
                diagWarning("Responses esuat: url=" + baseUrl
                    + ", status=" + response.code()
                    + ", ms=" + elapsedMs
                    + ", body=\"" + abbreviate(responseBody, 220) + "\"");
                throw new IOException("OpenAI a returnat HTTP " + response.code()
                    + (errorMessage.isBlank() ? "" : ": " + errorMessage));
            }

            String generatedText = extractGeneratedText(responseBody, expectedSpeakerName);
            diagInfo("Responses reusit: url=" + baseUrl
                + ", ms=" + elapsedMs
                + ", response_chars=" + generatedText.length()
                + (isResponsePreviewEnabled()
                    ? ", response_preview=\"" + abbreviate(generatedText, 180) + "\""
                    : ""));
            plugin.debug("Raspuns OpenAI: " + generatedText);

            return generatedText;
        }
    }

    private Request.Builder newRequestBuilder(String url) {
        Request.Builder builder = new Request.Builder()
            .url(url)
            .header("Accept", "application/json");

        if (hasApiKey()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        return builder;
    }

    private String cleanResponse(String response, String expectedSpeakerName) {
        response = response.replaceAll("^\"|\"$", "").trim();
        response = stripSpeakerPrefix(response, expectedSpeakerName);

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

    private String extractGeneratedText(String responseBody, String expectedSpeakerName) throws IOException {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IOException("OpenAI a returnat un raspuns gol.");
        }

        final JsonObject jsonResponse;
        try {
            jsonResponse = gson.fromJson(responseBody, JsonObject.class);
        } catch (Exception e) {
            throw new IOException("OpenAI a returnat JSON invalid: " + compactExceptionMessage(e), e);
        }

        if (jsonResponse == null) {
            throw new IOException("OpenAI a returnat un JSON gol.");
        }

        JsonElement errorElement = jsonResponse.get("error");
        if (errorElement != null && !errorElement.isJsonNull()) {
            throw new IOException("OpenAI a raportat eroare: " + extractOpenAIErrorMessage(responseBody));
        }

        JsonElement outputTextElement = jsonResponse.get("output_text");
        if (outputTextElement != null && outputTextElement.isJsonPrimitive()) {
            String directText = cleanResponse(outputTextElement.getAsString(), expectedSpeakerName);
            if (!directText.isBlank()) {
                return directText;
            }
        }

        StringBuilder combined = new StringBuilder();
        JsonElement outputElement = jsonResponse.get("output");
        if (outputElement != null && outputElement.isJsonArray()) {
            JsonArray outputs = outputElement.getAsJsonArray();
            for (JsonElement itemElement : outputs) {
                if (!itemElement.isJsonObject()) {
                    continue;
                }

                JsonObject item = itemElement.getAsJsonObject();
                if (!"message".equalsIgnoreCase(safeJsonString(item.get("type")))) {
                    continue;
                }

                JsonElement contentElement = item.get("content");
                if (contentElement == null || !contentElement.isJsonArray()) {
                    continue;
                }

                for (JsonElement contentItemElement : contentElement.getAsJsonArray()) {
                    if (!contentItemElement.isJsonObject()) {
                        continue;
                    }

                    JsonObject contentItem = contentItemElement.getAsJsonObject();
                    String type = safeJsonString(contentItem.get("type"));
                    if (!"output_text".equalsIgnoreCase(type) && !"text".equalsIgnoreCase(type)) {
                        continue;
                    }

                    String text = safeJsonString(contentItem.get("text")).trim();
                    if (text.isBlank()) {
                        continue;
                    }

                    if (combined.length() > 0) {
                        combined.append('\n');
                    }
                    combined.append(text);
                }
            }
        }

        String generatedText = cleanResponse(combined.toString(), expectedSpeakerName);
        if (generatedText.isBlank()) {
            throw new IOException("OpenAI nu a returnat text util in campul output.");
        }

        return generatedText;
    }

    private String extractOpenAIErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }

        try {
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            if (json == null) {
                return abbreviate(responseBody, 200);
            }

            JsonElement errorElement = json.get("error");
            if (errorElement == null || !errorElement.isJsonObject()) {
                return abbreviate(responseBody, 200);
            }

            JsonObject error = errorElement.getAsJsonObject();
            String code = safeJsonString(error.get("code"));
            String type = safeJsonString(error.get("type"));
            String message = safeJsonString(error.get("message"));

            List<String> parts = new ArrayList<>();
            if (!code.isBlank()) {
                parts.add(code);
            } else if (!type.isBlank()) {
                parts.add(type);
            }
            if (!message.isBlank()) {
                parts.add(message);
            }

            return parts.isEmpty() ? abbreviate(responseBody, 200) : String.join(": ", parts);
        } catch (Exception ignored) {
            return abbreviate(responseBody, 200);
        }
    }

    private String safeJsonString(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }

        try {
            return element.getAsString();
        } catch (Exception ignored) {
            return element.toString();
        }
    }

    private String generateFallbackResponse(PromptSnapshot snapshot) {
        String normalized = snapshot.playerMessage() == null ? "" : snapshot.playerMessage().toLowerCase(Locale.ROOT).trim();
        String occupation = snapshot.occupation() != null && !snapshot.occupation().isBlank()
            ? snapshot.occupation()
            : "locuitor";
        String factResponse = NpcFactResolver.resolve(snapshot.playerMessage(), buildNpcFacts(snapshot)).orElse(null);

        if (factResponse != null) {
            return factResponse;
        }

        if (containsAny(normalized, "salut", "buna", "hei", "servus", "noroc")) {
            return pickResponse(snapshot.npcUuid(), normalized, new String[]{
                "Salut. Eu sunt " + snapshot.npcName() + ".",
                "Buna. Cu ce te pot ajuta?",
                "Salut, calatorule. Ce vrei sa afli?"
            });
        }

        if (containsAny(normalized, "multumesc", "mersi", "apreciez")) {
            return pickResponse(snapshot.npcUuid(), normalized, new String[]{
                "Cu placere.",
                "N-ai pentru ce.",
                "Ma bucur ca ti-am fost de folos."
            });
        }

        if (containsAny(normalized, "prost", "idiot", "tampit", "urat", "fraier")) {
            return pickResponse(snapshot.npcUuid(), normalized, new String[]{
                "Vorbeste cu respect daca vrei raspunsuri.",
                "Nu-mi place tonul tau.",
                "Daca vii cu insulte, n-avem ce discuta."
            });
        }

        if (containsAny(normalized, "cumpara", "vinde", "pret", "marfa", "schimb")) {
            return pickResponse(snapshot.npcUuid(), normalized, new String[]{
                "Poate te pot ajuta, depinde ce cauti.",
                "Daca e vorba de " + occupation + ", stiu cate ceva.",
                "Spune clar ce iti trebuie si vedem."
            });
        }

        if (!snapshot.directAddress() && containsAny(normalized, "hmm", "ok", "bine", "da", "nu")) {
            return pickResponse(snapshot.npcUuid(), normalized, new String[]{
                "Te aud.",
                "Daca imi vorbesti mie, spune mai clar.",
                "Sunt aici, daca voiai un raspuns."
            });
        }

        if (normalized.endsWith("?") || containsAny(normalized, "ce ", "cum ", "unde ", "cand ", "de ce", "cine ")) {
            return pickResponse(snapshot.npcUuid(), normalized, new String[]{
                "Nu stiu tot, dar pot incerca sa te ajut.",
                "Din cate stiu eu, raspunsul tine de meseria mea de " + occupation + ".",
                "Intrebare buna. Spune-mi mai exact ce te intereseaza."
            });
        }

        String[] responses = switch (snapshot.dominantEmotion()) {
            case "happiness" -> new String[]{
                "Ce bucurie sa te vad!",
                "Ma bucur sa stam de vorba.",
                "Hm, suna interesant ce spui."
            };
            case "sadness" -> new String[]{
                "Nu e cea mai buna zi pentru mine.",
                "Ma simt cam trist astazi.",
                "Imi pare rau, nu sunt in apele mele."
            };
            case "anger" -> new String[]{
                "Nu am chef de vorba acum.",
                "Spune repede ce vrei.",
                "Lasa-ma sa-mi vad de treaba."
            };
            case "fear" -> new String[]{
                "M-ai speriat putin.",
                "Nu stiu daca e bine sa vorbim aici.",
                "Vorbeste mai incet. Nu-mi place locul asta."
            };
            default -> new String[]{
                "Hmm, inteleg.",
                "Asa e.",
                "Te ascult."
            };
        };

        return pickResponse(snapshot.npcUuid(), normalized, responses);
    }

    public CompletableFuture<Boolean> checkConnection() {
        return diagnoseConnection(false).thenApply(status -> status.isReachable() && status.isModelAvailable());
    }

    public CompletableFuture<ConnectionStatus> diagnoseConnection(boolean ignored) {
        return CompletableFuture.supplyAsync(this::probeConnection);
    }

    public boolean isAvailable() {
        return hasApiKey() && !isInOfflineBackoffWindow();
    }

    private boolean isInOfflineBackoffWindow() {
        return System.currentTimeMillis() < offlineRetryAfterMillis;
    }

    private void clearOfflineState() {
        if (offlineMode) {
            plugin.getLogger().info("OpenAI este din nou disponibil. Reiau raspunsurile AI.");
        }
        offlineMode = false;
        offlineRetryAfterMillis = 0L;
    }

    private void handleOfflineFailure(Exception exception) {
        long retrySeconds = plugin.getConfig().getLong("openai.offline_retry_seconds", 15L);
        offlineRetryAfterMillis = System.currentTimeMillis() + retrySeconds * 1000L;

        if (offlineMode) {
            plugin.debug("OpenAI este indisponibil, raspuns fallback folosit: " + compactExceptionMessage(exception));
            return;
        }

        offlineMode = true;
        plugin.getLogger().warning("OpenAI indisponibil, folosesc fallback local pentru " + retrySeconds
            + "s: " + compactExceptionMessage(exception));
        diagWarning("Fallback activ pana la " + offlineRetryAfterMillis + " ms epoch.");
    }

    private void handleGenerationFailure(Exception exception) {
        if (isReadTimeout(exception)) {
            diagWarning("OpenAI a raspuns prea lent pentru timeout-ul curent: " + compactExceptionMessage(exception));
            plugin.getLogger().warning("OpenAI a depasit timpul de raspuns (" + readTimeoutSeconds
                + "s). Mareste openai.read_timeout sau redu lungimea promptului.");
            return;
        }

        if (shouldEnterOfflineBackoff(exception)) {
            handleOfflineFailure(exception);
            return;
        }

        diagWarning("Cererea catre OpenAI a esuat fara a intra in offline/backoff: "
            + compactExceptionMessage(exception));
        plugin.getLogger().warning("Cerere OpenAI esuata: " + compactExceptionMessage(exception));
    }

    private String compactExceptionMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    private boolean shouldEnterOfflineBackoff(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        if (cause instanceof SocketTimeoutException
            || cause instanceof ConnectException
            || cause instanceof UnknownHostException
            || cause instanceof NoRouteToHostException
            || cause instanceof PortUnreachableException) {
            return true;
        }

        if (cause instanceof SocketException && cause.getMessage() != null) {
            String lower = cause.getMessage().toLowerCase(Locale.ROOT);
            if (lower.contains("connection reset")
                || lower.contains("connection aborted")
                || lower.contains("broken pipe")) {
                return true;
            }
        }

        String message = compactThrowableMessage(throwable);
        return message.contains("401")
            || message.contains("403")
            || message.contains("404")
            || message.contains("429")
            || message.contains("connection refused")
            || message.contains("failed to connect")
            || message.contains("no route to host")
            || message.contains("api key")
            || message.contains("authentication")
            || message.contains("model_not_found");
    }

    private boolean isReadTimeout(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        if (cause instanceof SocketTimeoutException) {
            return true;
        }

        return compactThrowableMessage(throwable).contains("read timed out");
    }

    private String compactThrowableMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null ? "" : message.toLowerCase(Locale.ROOT);
    }

    public CompletableFuture<String> generateAsync(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            if (isInOfflineBackoffWindow()) {
                diagInfo("Sar peste generateAsync din cauza backoff-ului activ.");
                return null;
            }

            try {
                String response = callOpenAI(prompt, null);
                clearOfflineState();
                return response;
            } catch (Exception e) {
                handleGenerationFailure(e);
                return null;
            }
        });
    }

    public void runDiagnosticsAsync(String reason) {
        if (!isDiagnosticsEnabled() && !isStartupDiagnosticsEnabled()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            diagInfo("Pornesc diagnosticare OpenAI. reason=" + reason);
            diagInfo("Config activa: model=" + model
                + ", connect_timeout=" + connectTimeoutSeconds + "s"
                + ", read_timeout=" + readTimeoutSeconds + "s"
                + ", write_timeout=" + writeTimeoutSeconds + "s"
                + ", base_url=" + baseUrl
                + ", api_key_present=" + hasApiKey()
                + ", store=" + storeResponses);

            ConnectionStatus status = probeConnection();
            lastConnectionStatus = status;

            if (status.isReachable() && status.isModelAvailable()) {
                diagInfo("Diagnosticare terminata: OpenAI raspunde pe " + status.getRespondingUrl()
                    + " si modelul este disponibil.");
            } else {
                diagWarning("Diagnosticare terminata: " + status.getSummary());
            }
        });
    }

    public CompletableFuture<String> analyzeSentiment(String message) {
        return CompletableFuture.completedFuture(analyzeSentimentFast(message));
    }

    public String analyzeSentimentFast(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT).trim();
        if (normalized.isBlank()) {
            return "neutral";
        }

        if (containsAny(normalized, "te omor", "te bat", "mori", "kill", "distrug", "iti rup")) {
            return "threat";
        }

        if (containsAny(normalized, "prost", "idiot", "tampit", "urat", "fraier")) {
            return "insult";
        }

        if (containsAny(normalized, "multumesc", "mersi", "apreciez", "respect")) {
            return "compliment";
        }

        if (containsAny(normalized, "salut", "buna", "hei", "servus", "noroc")) {
            return "greeting";
        }

        if (containsAny(normalized, "bravo", "super", "grozav", "minunat", "frumos", "bun")) {
            return "positive";
        }

        if (containsAny(normalized, "rau", "nasol", "groaznic", "suparat", "trist", "urat rau")) {
            return "negative";
        }

        if (normalized.endsWith("?") || containsAny(normalized, "ce ", "cum ", "unde ", "cand ", "de ce", "cine ")) {
            return "question";
        }

        return "neutral";
    }

    private CompletableFuture<PromptSnapshot> capturePromptSnapshot(DialogManager.DialogRequest request) {
        CompletableFuture<PromptSnapshot> future = new CompletableFuture<>();
        Runnable captureTask = () -> {
            try {
                future.complete(createPromptSnapshot(request));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        };

        if (Bukkit.isPrimaryThread()) {
            captureTask.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, captureTask);
        }

        return future;
    }

    private PromptSnapshot createPromptSnapshot(DialogManager.DialogRequest request) {
        AINPC npc = request.npc();
        Player player = request.player();

        String npcDescription = npc.generateContextDescription();
        String environmentDescription = "";
        String topologyConsensusBlock = "";

        if (npc.getContext() != null) {
            environmentDescription = npc.getContext().generateContextDescription();
            if (plugin.getFeaturePackLoader() != null && npc.getContext().getTopologyCategory() != null) {
                TopologyConsensus topologyConsensus = plugin.getFeaturePackLoader()
                    .buildTopologyConsensus(npc.getContext().getTopologyCategory());
                if (topologyConsensus != null) {
                    topologyConsensusBlock = topologyConsensus.toPromptBlock();
                }
            }
        }

        List<FamilyMemberSnapshot> familyMembers = new ArrayList<>();
        if (plugin.getFamilyManager() != null) {
            for (FamilyMemberRecord member : plugin.getFamilyManager().getFamily(npc)) {
                familyMembers.add(new FamilyMemberSnapshot(
                    member.name(),
                    member.relationType(),
                    member.alive()
                ));
            }
        }

        return new PromptSnapshot(
            npc.getUuid() != null ? npc.getUuid() : UUID.randomUUID(),
            npc.getName(),
            npcDescription,
            environmentDescription,
            topologyConsensusBlock,
            familyMembers,
            npc.isProfileCreated(),
            npc.getProfileSource(),
            npc.getProfileVersion(),
            npc.getProfileSummary(),
            npc.getProfileDataJson(),
            npc.getTraits() == null ? List.of() : List.copyOf(npc.getTraits()),
            player != null ? player.getName() : "Jucator",
            request.message(),
            npc.getOccupation() != null ? npc.getOccupation() : "",
            npc.getEmotions().getShortDescription(),
            npc.getEmotions().getDominantEmotion(),
            npc.getCurrentState() != null ? npc.getCurrentState().getDisplayName() : "",
            NpcFactResolver.describeCurrentActivity(npc.getOccupation(), npc.getCurrentState()),
            NpcFactResolver.describeLocation(npc, npc.getContext()),
            request.directAddress(),
            request.explicitConversation(),
            request.triggerReason(),
            request.nearbyNpcCount(),
            request.distanceToNpc()
        );
    }

    private NpcFactResolver.NpcFacts buildNpcFacts(PromptSnapshot snapshot) {
        return new NpcFactResolver.NpcFacts(
            snapshot.npcName(),
            snapshot.occupation(),
            snapshot.emotionShortDescription(),
            snapshot.currentState(),
            snapshot.currentActivity(),
            snapshot.locationDescription()
        );
    }

    private ConnectionStatus probeConnection() {
        if (!hasApiKey()) {
            return ConnectionStatus.unreachable(model, List.of(baseUrl), null, List.of(),
                List.of("Cheia API OpenAI lipseste. Seteaza openai.api_key sau OPENAI_API_KEY."));
        }

        String modelUrl = baseUrl + "/models/" + URLEncoder.encode(model, StandardCharsets.UTF_8);
        long startedAt = System.nanoTime();
        Request request = newRequestBuilder(modelUrl).get().build();

        try (Response response = httpClient.newCall(request).execute()) {
            long elapsedMs = nanosToMillis(startedAt);
            String responseBody = response.body() != null ? response.body().string() : "";
            diagInfo("Probe GET /models/{model}: url=" + modelUrl + ", status=" + response.code() + ", ms=" + elapsedMs);

            if (response.isSuccessful()) {
                JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                String detectedModel = json != null && json.has("id") ? safeJsonString(json.get("id")) : model;
                return ConnectionStatus.reachable(model, List.of(baseUrl), baseUrl, true, List.of(detectedModel), List.of());
            }

            String errorMessage = extractOpenAIErrorMessage(responseBody);
            if (response.code() == 404) {
                return ConnectionStatus.reachable(model, List.of(baseUrl), baseUrl, false, List.of(),
                    List.of(errorMessage.isBlank()
                        ? "Modelul \"" + model + "\" nu a fost gasit."
                        : errorMessage));
            }

            String diagnostic = "HTTP " + response.code()
                + (errorMessage.isBlank() ? "" : " - " + errorMessage);
            return ConnectionStatus.unreachable(model, List.of(baseUrl), baseUrl, List.of(), List.of(diagnostic));
        } catch (IOException e) {
            return ConnectionStatus.unreachable(model, List.of(baseUrl), null, List.of(),
                List.of(compactExceptionMessage(e)));
        }
    }

    private String sanitizeBaseUrl(String configuredBaseUrl) {
        String envBaseUrl = System.getenv("OPENAI_BASE_URL");
        String value = (configuredBaseUrl == null || configuredBaseUrl.isBlank())
            ? (envBaseUrl == null || envBaseUrl.isBlank() ? "https://api.openai.com/v1" : envBaseUrl.trim())
            : configuredBaseUrl.trim();

        if (!value.contains("://")) {
            value = "https://" + value;
        }

        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        return value;
    }

    private String sanitizeSecret(String configuredValue, String envValue) {
        String value = configuredValue == null || configuredValue.isBlank()
            ? envValue
            : configuredValue;
        return value == null ? "" : value.trim();
    }

    private boolean hasApiKey() {
        return !apiKey.isBlank();
    }

    private String formatLevel(double value) {
        if (value > 0.8) return "foarte ridicat";
        if (value > 0.6) return "ridicat";
        if (value > 0.4) return "mediu";
        if (value > 0.2) return "scazut";
        return "foarte scazut";
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String pickResponse(UUID npcUuid, String messageKey, String[] responses) {
        String key = messageKey == null ? "" : messageKey;
        int hash = 31 * npcUuid.hashCode() + key.hashCode();
        int index = Math.floorMod(hash, responses.length);
        return responses[index];
    }

    private String stripSpeakerPrefix(String response, String expectedSpeakerName) {
        int colonIndex = response.indexOf(':');
        if (colonIndex <= 0 || colonIndex > 40) {
            return response;
        }

        String prefix = response.substring(0, colonIndex)
            .replace("\"", "")
            .replace("[", "")
            .replace("]", "")
            .trim();

        if (expectedSpeakerName != null && !expectedSpeakerName.isBlank()
            && prefix.equalsIgnoreCase(expectedSpeakerName)) {
            return response.substring(colonIndex + 1).trim();
        }

        if (prefix.equalsIgnoreCase("npc") || prefix.equalsIgnoreCase("villager")) {
            return response.substring(colonIndex + 1).trim();
        }

        return response;
    }

    private void logConfigurationDiagnostics() {
        if (!isDiagnosticsEnabled()) {
            return;
        }

        diagInfo("Initializare OpenAIService: model=" + model
            + ", connect_timeout=" + connectTimeoutSeconds + "s"
            + ", read_timeout=" + readTimeoutSeconds + "s"
            + ", write_timeout=" + writeTimeoutSeconds + "s"
            + ", base_url=" + baseUrl
            + ", api_key_present=" + hasApiKey()
            + ", startup_diag=" + isStartupDiagnosticsEnabled()
            + ", prompt_summary=" + isPromptSummaryEnabled()
            + ", response_preview=" + isResponsePreviewEnabled()
            + ", store=" + storeResponses);
    }

    private boolean isDiagnosticsEnabled() {
        return plugin.getConfig().getBoolean("openai.diagnostics.enabled", false);
    }

    private boolean isStartupDiagnosticsEnabled() {
        return plugin.getConfig().getBoolean("openai.diagnostics.check_on_startup", false);
    }

    private boolean isPromptSummaryEnabled() {
        return plugin.getConfig().getBoolean("openai.diagnostics.log_prompt_summary", true);
    }

    private boolean isResponsePreviewEnabled() {
        return plugin.getConfig().getBoolean("openai.diagnostics.log_response_preview", true);
    }

    private void diagInfo(String message) {
        if (isDiagnosticsEnabled()) {
            plugin.getLogger().info("[OpenAIDiag] " + message);
        }
    }

    private void diagWarning(String message) {
        if (isDiagnosticsEnabled()) {
            plugin.getLogger().warning("[OpenAIDiag] " + message);
        }
    }

    private long nanosToMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null) {
            return "";
        }

        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "necunoscut" : value;
    }

    private String joinTraits(List<String> traitIds) {
        return traitIds == null || traitIds.isEmpty() ? "niciun trait persistent" : String.join(", ", traitIds);
    }

    private String describeMemoryImpact(double value) {
        if (value >= 0.6D) {
            return "foarte pozitiv";
        }
        if (value >= 0.2D) {
            return "pozitiv";
        }
        if (value <= -0.6D) {
            return "foarte negativ";
        }
        if (value <= -0.2D) {
            return "negativ";
        }
        return "neutru";
    }

    private String capitalizeSentence(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String trimmed = text.trim();
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    public static class ConnectionStatus {
        private final boolean reachable;
        private final String configuredModel;
        private final List<String> triedUrls;
        private final String respondingUrl;
        private final boolean modelAvailable;
        private final List<String> availableModels;
        private final List<String> errors;

        private ConnectionStatus(boolean reachable,
                                 String configuredModel,
                                 List<String> triedUrls,
                                 String respondingUrl,
                                 boolean modelAvailable,
                                 List<String> availableModels,
                                 List<String> errors) {
            this.reachable = reachable;
            this.configuredModel = configuredModel;
            this.triedUrls = List.copyOf(triedUrls);
            this.respondingUrl = respondingUrl;
            this.modelAvailable = modelAvailable;
            this.availableModels = List.copyOf(availableModels);
            this.errors = List.copyOf(errors);
        }

        public static ConnectionStatus unchecked(String configuredModel, List<String> triedUrls) {
            return new ConnectionStatus(false, configuredModel, triedUrls, null, false, List.of(), List.of());
        }

        public static ConnectionStatus reachable(String configuredModel,
                                                 List<String> triedUrls,
                                                 String respondingUrl,
                                                 boolean modelAvailable,
                                                 List<String> availableModels,
                                                 List<String> errors) {
            return new ConnectionStatus(true, configuredModel, triedUrls, respondingUrl, modelAvailable,
                availableModels, errors);
        }

        public static ConnectionStatus unreachable(String configuredModel,
                                                   List<String> triedUrls,
                                                   String respondingUrl,
                                                   List<String> availableModels,
                                                   List<String> errors) {
            return new ConnectionStatus(false, configuredModel, triedUrls, respondingUrl, false,
                availableModels, errors);
        }

        public boolean isReachable() {
            return reachable;
        }

        public boolean isModelAvailable() {
            return modelAvailable;
        }

        public String getRespondingUrl() {
            return respondingUrl;
        }

        public List<String> getAvailableModels() {
            return availableModels;
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getTriedUrls() {
            return triedUrls;
        }

        public String getSummary() {
            if (reachable && modelAvailable) {
                return "OpenAI raspunde pe " + respondingUrl + " si modelul \"" + configuredModel + "\" este disponibil.";
            }

            if (reachable) {
                return "OpenAI raspunde pe " + respondingUrl + ", dar modelul \"" + configuredModel
                    + "\" nu este disponibil. Detalii: "
                    + (errors.isEmpty() ? "<fara detalii suplimentare>" : String.join(" | ", errors));
            }

            return errors.isEmpty()
                ? "Nu am putut contacta OpenAI API."
                : "Nu am putut contacta OpenAI API. Probe: " + String.join(" | ", errors) + ".";
        }
    }

    private record PromptSnapshot(
        UUID npcUuid,
        String npcName,
        String npcDescription,
        String environmentDescription,
        String topologyConsensusBlock,
        List<FamilyMemberSnapshot> familyMembers,
        boolean profileCreated,
        String profileSource,
        int profileVersion,
        String profileSummary,
        String profileDataJson,
        List<String> traitIds,
        String playerName,
        String playerMessage,
        String occupation,
        String emotionShortDescription,
        String dominantEmotion,
        String currentState,
        String currentActivity,
        String locationDescription,
        boolean directAddress,
        boolean explicitConversation,
        String triggerReason,
        int nearbyNpcCount,
        double distanceToNpc
    ) {
    }

    private record FamilyMemberSnapshot(
        String name,
        String relationType,
        boolean alive
    ) {
    }

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
}
