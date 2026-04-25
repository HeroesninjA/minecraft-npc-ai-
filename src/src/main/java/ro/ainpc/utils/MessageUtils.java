package ro.ainpc.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ro.ainpc.AINPCPlugin;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageUtils {

    private final AINPCPlugin plugin;
    private final MiniMessage miniMessage;
    private final LegacyComponentSerializer legacySerializer;

    public MessageUtils(AINPCPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.legacySerializer = LegacyComponentSerializer.legacyAmpersand();
    }

    /**
     * Trimite un mesaj din configuratie catre un jucator
     */
    public void sendMessage(CommandSender sender, String messageKey) {
        sendMessage(sender, messageKey, Map.of());
    }

    /**
     * Trimite un mesaj din configuratie cu placeholdere
     */
    public void sendMessage(CommandSender sender, String messageKey, Map<String, String> placeholders) {
        String message = plugin.getConfig().getString("messages." + messageKey, messageKey);
        message = replacePlaceholders(message, placeholders);
        sender.sendMessage(colorize(message));
    }

    /**
     * Trimite un mesaj custom catre un jucator
     */
    public void send(CommandSender sender, String message) {
        sender.sendMessage(colorize(message));
    }

    /**
     * Trimite un mesaj de la un NPC catre un jucator
     */
    public void sendNPCMessage(Player player, String npcName, String message) {
        String prefix = plugin.getConfig().getString("dialog.prefix", "&6[NPC] &e");
        boolean showName = plugin.getConfig().getBoolean("dialog.show_name", true);
        
        String fullMessage;
        if (showName) {
            fullMessage = prefix + "&f" + npcName + "&7: &f" + message;
        } else {
            fullMessage = prefix + message;
        }
        
        player.sendMessage(colorize(fullMessage));
    }

    /**
     * Coloreaza un mesaj folosind coduri & si MiniMessage
     */
    public Component colorize(String message) {
        // Converteste codurile & in componente
        return legacySerializer.deserialize(message);
    }

    /**
     * Inlocuieste placeholderele in mesaj
     */
    public String replacePlaceholders(String message, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return message;
    }

    /**
     * Formateaza timpul in format citibil
     */
    public String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + " secunde";
        } else if (seconds < 3600) {
            return (seconds / 60) + " minute";
        } else if (seconds < 86400) {
            return (seconds / 3600) + " ore";
        } else {
            return (seconds / 86400) + " zile";
        }
    }

    /**
     * Formateaza un procent (0.0 - 1.0) in text
     */
    public String formatPercentage(double value) {
        return String.format("%.0f%%", value * 100);
    }

    /**
     * Creeaza o bara de progres vizuala
     */
    public String createProgressBar(double value, int length, String filledChar, String emptyChar, 
                                     String filledColor, String emptyColor) {
        int filled = (int) Math.round(value * length);
        int empty = length - filled;
        
        StringBuilder bar = new StringBuilder();
        bar.append(filledColor);
        for (int i = 0; i < filled; i++) {
            bar.append(filledChar);
        }
        bar.append(emptyColor);
        for (int i = 0; i < empty; i++) {
            bar.append(emptyChar);
        }
        
        return bar.toString();
    }

    /**
     * Bara de progres default
     */
    public String createProgressBar(double value) {
        return createProgressBar(value, 10, "█", "░", "&a", "&7");
    }

    /**
     * Obtine culoarea emotiei
     */
    public String getEmotionColor(String emotion) {
        return switch (emotion.toLowerCase()) {
            case "happiness", "bucurie" -> "&a";
            case "sadness", "tristete" -> "&9";
            case "anger", "furie" -> "&c";
            case "fear", "frica" -> "&5";
            case "surprise", "surpriza" -> "&e";
            case "disgust", "dezgust" -> "&2";
            case "trust", "incredere" -> "&b";
            case "anticipation", "anticipare" -> "&6";
            default -> "&f";
        };
    }

    /**
     * Obtine numele emotiei in romana
     */
    public String getEmotionName(String emotion) {
        return switch (emotion.toLowerCase()) {
            case "happiness" -> "Bucurie";
            case "sadness" -> "Tristete";
            case "anger" -> "Furie";
            case "fear" -> "Frica";
            case "surprise" -> "Surpriza";
            case "disgust" -> "Dezgust";
            case "trust" -> "Incredere";
            case "anticipation" -> "Anticipare";
            default -> emotion;
        };
    }

    /**
     * Obtine numele relatiei in romana
     */
    public String getRelationshipName(String relationship) {
        return switch (relationship.toLowerCase()) {
            case "stranger" -> "Strain";
            case "acquaintance" -> "Cunoscut";
            case "friend" -> "Prieten";
            case "close_friend" -> "Prieten apropiat";
            case "best_friend" -> "Cel mai bun prieten";
            case "rival" -> "Rival";
            case "enemy" -> "Dusman";
            case "lover" -> "Iubit/a";
            case "spouse" -> "Sot/Sotie";
            default -> relationship;
        };
    }

    /**
     * Obtine numele relatiei de familie in romana
     */
    public String getFamilyRelationName(String relation) {
        return switch (relation.toLowerCase()) {
            case "father" -> "Tata";
            case "mother" -> "Mama";
            case "son" -> "Fiu";
            case "daughter" -> "Fiica";
            case "brother" -> "Frate";
            case "sister" -> "Sora";
            case "spouse" -> "Sot/Sotie";
            case "grandfather" -> "Bunic";
            case "grandmother" -> "Bunica";
            case "uncle" -> "Unchi";
            case "aunt" -> "Matusa";
            case "cousin" -> "Var/Verisoara";
            default -> relation;
        };
    }
}
