package ro.ainpc.commands;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.ai.OllamaService.FamilyMember;
import ro.ainpc.npc.AINPC;

import java.util.List;
import java.util.Map;

/**
 * Comanda principala pentru gestionarea NPC-urilor AI
 */
public class AINPCCommand implements CommandExecutor {

    private final AINPCPlugin plugin;

    public AINPCCommand(AINPCPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        return switch (subCommand) {
            case "create" -> handleCreate(sender, args);
            case "delete", "remove" -> handleDelete(sender, args);
            case "info" -> handleInfo(sender, args);
            case "list" -> handleList(sender, args);
            case "family" -> handleFamily(sender, args);
            case "mood", "emotion" -> handleMood(sender, args);
            case "tp", "teleport" -> handleTeleport(sender, args);
            case "reload" -> handleReload(sender);
            case "test" -> handleTest(sender);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    /**
     * /ainpc create <nume> [ocupatie] [varsta] [gen] [arhetip]
     */
    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ainpc.admin")) {
            plugin.getMessageUtils().sendMessage(sender, "no_permission");
            return true;
        }

        if (!(sender instanceof Player player)) {
            plugin.getMessageUtils().send(sender, "&cAceasta comanda poate fi folosita doar de jucatori!");
            return true;
        }

        if (args.length < 2) {
            plugin.getMessageUtils().send(sender, "&cUtilizare: /ainpc create <nume> [ocupatie] [varsta] [gen] [arhetip]");
            return true;
        }

        String name = args[1];
        String occupation = args.length > 2 ? args[2] : null;
        int age = args.length > 3 ? parseInt(args[3], 30) : 30;
        String gender = args.length > 4 ? args[4].toLowerCase() : "male";
        String archetype = args.length > 5 ? args[5] : null;

        // Valideaza genul
        if (!gender.equals("male") && !gender.equals("female")) {
            gender = "male";
        }

        Location location = player.getLocation();
        AINPC npc = plugin.getNpcManager().createNPC(name, location, occupation, null, age, gender, archetype);

        if (npc != null) {
            plugin.getMessageUtils().sendMessage(sender, "npc_created", Map.of("name", name));
            plugin.getMessageUtils().send(sender, "&7ID: &f" + npc.getDatabaseId());
            plugin.getMessageUtils().send(sender, "&7Personalitate: &f" + npc.getPersonality().getDominantTraits());
        } else {
            plugin.getMessageUtils().send(sender, "&cEroare la crearea NPC-ului!");
        }

        return true;
    }

    /**
     * /ainpc delete <nume>
     */
    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ainpc.admin")) {
            plugin.getMessageUtils().sendMessage(sender, "no_permission");
            return true;
        }

        if (args.length < 2) {
            plugin.getMessageUtils().send(sender, "&cUtilizare: /ainpc delete <nume>");
            return true;
        }

        String name = args[1];
        AINPC npc = plugin.getNpcManager().getNPCByName(name);

        if (npc == null) {
            plugin.getMessageUtils().sendMessage(sender, "npc_not_found");
            return true;
        }

        if (plugin.getNpcManager().deleteNPC(npc)) {
            plugin.getMessageUtils().sendMessage(sender, "npc_deleted", Map.of("name", name));
        } else {
            plugin.getMessageUtils().send(sender, "&cEroare la stergerea NPC-ului!");
        }

        return true;
    }

    /**
     * /ainpc info [nume]
     */
    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ainpc.info")) {
            plugin.getMessageUtils().sendMessage(sender, "no_permission");
            return true;
        }

        AINPC npc;
        
        if (args.length < 2) {
            // Gaseste NPC-ul cel mai apropiat
            if (!(sender instanceof Player player)) {
                plugin.getMessageUtils().send(sender, "&cSpecifica numele NPC-ului!");
                return true;
            }
            
            List<AINPC> nearby = plugin.getNpcManager().getNPCsNear(player.getLocation(), 10);
            if (nearby.isEmpty()) {
                plugin.getMessageUtils().send(sender, "&cNu exista NPC-uri in apropiere!");
                return true;
            }
            npc = nearby.get(0);
        } else {
            npc = plugin.getNpcManager().getNPCByName(args[1]);
        }

        if (npc == null) {
            plugin.getMessageUtils().sendMessage(sender, "npc_not_found");
            return true;
        }

        // Afiseaza informatii
        plugin.getMessageUtils().send(sender, "&6=== Informatii NPC ===");
        plugin.getMessageUtils().send(sender, "&eNume: &f" + npc.getName());
        plugin.getMessageUtils().send(sender, "&eID: &f" + npc.getDatabaseId());
        plugin.getMessageUtils().send(sender, "&eVarsta: &f" + npc.getAge() + " ani");
        plugin.getMessageUtils().send(sender, "&eGen: &f" + (npc.getGender().equals("male") ? "Barbat" : "Femeie"));
        
        if (npc.getOccupation() != null) {
            plugin.getMessageUtils().send(sender, "&eOcupatie: &f" + npc.getOccupation());
        }
        
        plugin.getMessageUtils().send(sender, "&eLocatie: &f" + formatLocation(npc.getLocation()));
        plugin.getMessageUtils().send(sender, "");
        plugin.getMessageUtils().send(sender, "&ePersonalitate: &f" + npc.getPersonality().getDominantTraits());
        plugin.getMessageUtils().send(sender, "&eEmotie: &f" + npc.getEmotions().getShortDescription());
        
        if (npc.getBackstory() != null) {
            plugin.getMessageUtils().send(sender, "");
            plugin.getMessageUtils().send(sender, "&ePoveste: &f" + npc.getBackstory());
        }

        return true;
    }

    /**
     * /ainpc list
     */
    private boolean handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ainpc.admin")) {
            plugin.getMessageUtils().sendMessage(sender, "no_permission");
            return true;
        }

        var npcs = plugin.getNpcManager().getAllNPCs();
        
        if (npcs.isEmpty()) {
            plugin.getMessageUtils().send(sender, "&7Nu exista NPC-uri create.");
            return true;
        }

        plugin.getMessageUtils().send(sender, "&6=== Lista NPC-uri (" + npcs.size() + ") ===");
        
        for (AINPC npc : npcs) {
            String emotionColor = npc.getEmotions().getDominantEmotionColor();
            String status = npc.isSpawned() ? "&a[ACTIV]" : "&c[INACTIV]";
            
            plugin.getMessageUtils().send(sender, 
                status + " " + emotionColor + npc.getName() + 
                " &7- " + (npc.getOccupation() != null ? npc.getOccupation() : "fara ocupatie") +
                " &8(ID: " + npc.getDatabaseId() + ")"
            );
        }

        return true;
    }

    /**
     * /ainpc family <nume>
     */
    private boolean handleFamily(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ainpc.info")) {
            plugin.getMessageUtils().sendMessage(sender, "no_permission");
            return true;
        }

        if (args.length < 2) {
            plugin.getMessageUtils().send(sender, "&cUtilizare: /ainpc family <nume>");
            return true;
        }

        AINPC npc = plugin.getNpcManager().getNPCByName(args[1]);
        if (npc == null) {
            plugin.getMessageUtils().sendMessage(sender, "npc_not_found");
            return true;
        }

        String report = plugin.getFamilyManager().getFamilyReport(npc);
        plugin.getMessageUtils().send(sender, report);

        return true;
    }

    /**
     * /ainpc mood <nume> <emotie> [intensitate]
     */
    private boolean handleMood(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ainpc.admin")) {
            plugin.getMessageUtils().sendMessage(sender, "no_permission");
            return true;
        }

        if (args.length < 3) {
            plugin.getMessageUtils().send(sender, "&cUtilizare: /ainpc mood <nume> <emotie> [intensitate]");
            plugin.getMessageUtils().send(sender, "&7Emotii: happiness, sadness, anger, fear, surprise, disgust, trust, anticipation");
            return true;
        }

        AINPC npc = plugin.getNpcManager().getNPCByName(args[1]);
        if (npc == null) {
            plugin.getMessageUtils().sendMessage(sender, "npc_not_found");
            return true;
        }

        String emotion = args[2].toLowerCase();
        double intensity = args.length > 3 ? parseDouble(args[3], 0.7) : 0.7;
        intensity = Math.max(0.0, Math.min(1.0, intensity));

        plugin.getEmotionManager().setMood(npc, emotion, intensity);
        
        plugin.getMessageUtils().send(sender, "&aEmotia lui &e" + npc.getName() + 
            " &aa fost setata la &f" + emotion + " &7(" + String.format("%.0f%%", intensity * 100) + ")");

        return true;
    }

    /**
     * /ainpc tp <nume>
     */
    private boolean handleTeleport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ainpc.admin")) {
            plugin.getMessageUtils().sendMessage(sender, "no_permission");
            return true;
        }

        if (!(sender instanceof Player player)) {
            plugin.getMessageUtils().send(sender, "&cAceasta comanda poate fi folosita doar de jucatori!");
            return true;
        }

        if (args.length < 2) {
            plugin.getMessageUtils().send(sender, "&cUtilizare: /ainpc tp <nume>");
            return true;
        }

        AINPC npc = plugin.getNpcManager().getNPCByName(args[1]);
        if (npc == null) {
            plugin.getMessageUtils().sendMessage(sender, "npc_not_found");
            return true;
        }

        Location loc = npc.getLocation();
        if (loc != null) {
            player.teleport(loc);
            plugin.getMessageUtils().send(sender, "&aTeleportat la &e" + npc.getName());
        } else {
            plugin.getMessageUtils().send(sender, "&cNu s-a putut obtine locatia NPC-ului!");
        }

        return true;
    }

    /**
     * /ainpc reload
     */
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("ainpc.admin")) {
            plugin.getMessageUtils().sendMessage(sender, "no_permission");
            return true;
        }

        plugin.reload();
        plugin.getMessageUtils().send(sender, "&aConfiguratia a fost reincarcata!");

        return true;
    }

    /**
     * /ainpc test - testeaza conexiunea Ollama
     */
    private boolean handleTest(CommandSender sender) {
        if (!sender.hasPermission("ainpc.admin")) {
            plugin.getMessageUtils().sendMessage(sender, "no_permission");
            return true;
        }

        plugin.getMessageUtils().send(sender, "&7Testare conexiune Ollama...");

        plugin.getOllamaService().checkConnection().thenAccept(connected -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (connected) {
                    plugin.getMessageUtils().send(sender, "&aOllama este conectat si functional!");
                } else {
                    plugin.getMessageUtils().send(sender, "&cOllama nu este disponibil! Verifica daca serverul Ollama ruleaza.");
                }
            });
        });

        return true;
    }

    /**
     * Afiseaza mesajul de ajutor
     */
    private void sendHelp(CommandSender sender) {
        plugin.getMessageUtils().send(sender, "&6=== AI NPC Plugin - Comenzi ===");
        plugin.getMessageUtils().send(sender, "&e/ainpc create <nume> [ocupatie] [varsta] [gen] [arhetip]");
        plugin.getMessageUtils().send(sender, "&7  Creeaza un NPC nou la locatia ta");
        plugin.getMessageUtils().send(sender, "&e/ainpc delete <nume>");
        plugin.getMessageUtils().send(sender, "&7  Sterge un NPC");
        plugin.getMessageUtils().send(sender, "&e/ainpc info [nume]");
        plugin.getMessageUtils().send(sender, "&7  Afiseaza informatii despre un NPC");
        plugin.getMessageUtils().send(sender, "&e/ainpc list");
        plugin.getMessageUtils().send(sender, "&7  Lista toate NPC-urile");
        plugin.getMessageUtils().send(sender, "&e/ainpc family <nume>");
        plugin.getMessageUtils().send(sender, "&7  Afiseaza familia unui NPC");
        plugin.getMessageUtils().send(sender, "&e/ainpc mood <nume> <emotie> [intensitate]");
        plugin.getMessageUtils().send(sender, "&7  Seteaza emotia unui NPC");
        plugin.getMessageUtils().send(sender, "&e/ainpc tp <nume>");
        plugin.getMessageUtils().send(sender, "&7  Teleporteaza-te la un NPC");
        plugin.getMessageUtils().send(sender, "&e/ainpc test");
        plugin.getMessageUtils().send(sender, "&7  Testeaza conexiunea Ollama");
        plugin.getMessageUtils().send(sender, "&e/ainpc reload");
        plugin.getMessageUtils().send(sender, "&7  Reincarca configuratia");
    }

    // Metode helper

    private String formatLocation(Location loc) {
        if (loc == null) return "necunoscuta";
        return String.format("%s (%.1f, %.1f, %.1f)", 
            loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    private int parseInt(String s, int defaultValue) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double parseDouble(String s, double defaultValue) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
