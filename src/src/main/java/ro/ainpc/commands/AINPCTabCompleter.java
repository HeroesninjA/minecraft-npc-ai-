package ro.ainpc.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.npc.AINPC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tab completer pentru comenzile NPC
 */
public class AINPCTabCompleter implements TabCompleter {

    private final AINPCPlugin plugin;
    
    private static final List<String> SUBCOMMANDS = Arrays.asList(
        "create", "delete", "info", "list", "family", "mood", "tp", "reload", "test"
    );
    
    private static final List<String> OCCUPATIONS = Arrays.asList(
        "fermier", "fierar", "pescar", "negustor", "miner", "tamplar",
        "soldat", "paznic", "brutar", "croitor", "alchimist", "bibliotecar",
        "preot", "cartograf", "macelar"
    );
    
    private static final List<String> GENDERS = Arrays.asList("male", "female");
    
    private static final List<String> ARCHETYPES = Arrays.asList(
        "hero", "villain", "sage", "jester", "caregiver", "explorer",
        "rebel", "lover", "creator", "ruler", "magician", "innocent",
        "orphan", "warrior", "merchant"
    );
    
    private static final List<String> EMOTIONS = Arrays.asList(
        "happiness", "sadness", "anger", "fear", "surprise", "disgust", "trust", "anticipation"
    );

    public AINPCTabCompleter(AINPCPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Primul argument - subcomanda
            completions.addAll(filterStartsWith(SUBCOMMANDS, args[0]));
        } else if (args.length >= 2) {
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "create" -> {
                    switch (args.length) {
                        case 2 -> completions.add("<nume>");
                        case 3 -> completions.addAll(filterStartsWith(OCCUPATIONS, args[2]));
                        case 4 -> completions.addAll(Arrays.asList("20", "25", "30", "40", "50", "60"));
                        case 5 -> completions.addAll(filterStartsWith(GENDERS, args[4]));
                        case 6 -> completions.addAll(filterStartsWith(ARCHETYPES, args[5]));
                    }
                }
                case "delete", "info", "family", "tp" -> {
                    if (args.length == 2) {
                        completions.addAll(getNPCNames(args[1]));
                    }
                }
                case "mood", "emotion" -> {
                    switch (args.length) {
                        case 2 -> completions.addAll(getNPCNames(args[1]));
                        case 3 -> completions.addAll(filterStartsWith(EMOTIONS, args[2]));
                        case 4 -> completions.addAll(Arrays.asList("0.3", "0.5", "0.7", "1.0"));
                    }
                }
            }
        }

        return completions;
    }

    /**
     * Obtine numele tuturor NPC-urilor care incep cu prefixul dat
     */
    private List<String> getNPCNames(String prefix) {
        return plugin.getNpcManager().getAllNPCs().stream()
            .map(AINPC::getName)
            .filter(name -> name.toLowerCase().startsWith(prefix.toLowerCase()))
            .collect(Collectors.toList());
    }

    /**
     * Filtreaza lista pentru a returna doar elementele care incep cu prefixul dat
     */
    private List<String> filterStartsWith(List<String> list, String prefix) {
        return list.stream()
            .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
            .collect(Collectors.toList());
    }
}
