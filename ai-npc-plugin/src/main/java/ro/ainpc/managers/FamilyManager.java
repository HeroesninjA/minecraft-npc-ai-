package ro.ainpc.managers;

import ro.ainpc.AINPCPlugin;
import ro.ainpc.ai.OllamaService.FamilyMember;
import ro.ainpc.npc.AINPC;

import java.sql.*;
import java.util.*;

/**
 * Manager pentru sistemul de familie al NPC-urilor
 */
public class FamilyManager {

    private final AINPCPlugin plugin;
    private final Random random;
    
    // Liste de nume pentru generare
    private static final String[] MALE_NAMES = {
        "Ion", "Andrei", "Mihai", "Alexandru", "Stefan", "Cristian", "Adrian", "Florin",
        "Gheorghe", "Vasile", "Dumitru", "Nicolae", "Marin", "Constantin", "Petru",
        "Radu", "Vlad", "Tudor", "Bogdan", "Catalin", "Daniel", "Gabriel", "Ionut",
        "Lucian", "Marcel", "Ovidiu", "Paul", "Robert", "Sergiu", "Victor"
    };
    
    private static final String[] FEMALE_NAMES = {
        "Maria", "Ana", "Elena", "Ioana", "Andreea", "Cristina", "Alexandra", "Daniela",
        "Gabriela", "Laura", "Mihaela", "Monica", "Raluca", "Simona", "Valentina",
        "Adriana", "Alina", "Carmen", "Diana", "Eva", "Florina", "Irina", "Julia",
        "Larisa", "Madalina", "Nicoleta", "Oana", "Paula", "Roxana", "Teodora"
    };

    public FamilyManager(AINPCPlugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    /**
     * Genereaza familia pentru un NPC nou
     */
    public void generateFamily(AINPC npc) {
        if (!plugin.getConfig().getBoolean("family.auto_generate", true)) {
            return;
        }

        // Sansa de a avea sot/sotie
        int spouseChance = plugin.getConfig().getInt("family.spouse_chance", 60);
        if (random.nextInt(100) < spouseChance && npc.getAge() >= 20) {
            generateSpouse(npc);
        }

        // Sansa de a avea parinti in viata
        int livingParentsChance = plugin.getConfig().getInt("family.living_parents_chance", 40);
        generateParents(npc, random.nextInt(100) < livingParentsChance);

        // Genereaza copii daca are sot/sotie si varsta potrivita
        if (hasSpouse(npc) && npc.getAge() >= 25) {
            int maxChildren = plugin.getConfig().getInt("family.max_children", 4);
            int numChildren = random.nextInt(maxChildren + 1);
            for (int i = 0; i < numChildren; i++) {
                generateChild(npc);
            }
        }

        // Genereaza frati/surori
        int numSiblings = random.nextInt(4); // 0-3 frati
        for (int i = 0; i < numSiblings; i++) {
            generateSibling(npc);
        }

        plugin.debug("Familie generata pentru " + npc.getName());
    }

    /**
     * Genereaza sot/sotie pentru NPC
     */
    private void generateSpouse(AINPC npc) {
        String spouseGender = npc.getGender().equals("male") ? "female" : "male";
        String spouseName = generateName(spouseGender);
        
        // Varsta apropiata de NPC
        int spouseAge = npc.getAge() + random.nextInt(11) - 5; // +/- 5 ani
        spouseAge = Math.max(18, spouseAge);

        String backstory = "S-au casatorit acum " + (random.nextInt(10) + 1) + " ani.";
        
        addFamilyMember(npc, spouseName, "spouse", true, null, backstory);
    }

    /**
     * Genereaza parintii NPC-ului
     */
    private void generateParents(AINPC npc, boolean alive) {
        // Tatal
        String fatherName = generateName("male");
        boolean fatherAlive = alive && random.nextBoolean();
        String fatherBackstory = fatherAlive ? 
            "Lucreaza ca " + getRandomOccupation() + "." : 
            "A murit acum " + (random.nextInt(20) + 1) + " ani.";
        addFamilyMember(npc, fatherName, "father", fatherAlive, null, fatherBackstory);

        // Mama
        String motherName = generateName("female");
        boolean motherAlive = alive && random.nextBoolean();
        String motherBackstory = motherAlive ? 
            "Se ocupa de " + getRandomActivity() + "." : 
            "A murit acum " + (random.nextInt(20) + 1) + " ani.";
        addFamilyMember(npc, motherName, "mother", motherAlive, null, motherBackstory);
    }

    /**
     * Genereaza un copil pentru NPC
     */
    private void generateChild(AINPC npc) {
        String childGender = random.nextBoolean() ? "male" : "female";
        String childName = generateName(childGender);
        String relationType = childGender.equals("male") ? "son" : "daughter";
        
        // Varsta copilului
        int maxChildAge = npc.getAge() - 18;
        if (maxChildAge < 1) return;
        int childAge = random.nextInt(Math.min(maxChildAge, 25)) + 1;
        
        String backstory = "Are " + childAge + " ani. " + 
            (childAge >= 18 ? "Lucreaza ca " + getRandomOccupation() + "." : "Este inca la scoala.");
        
        addFamilyMember(npc, childName, relationType, true, null, backstory);
    }

    /**
     * Genereaza un frate/sora pentru NPC
     */
    private void generateSibling(AINPC npc) {
        String siblingGender = random.nextBoolean() ? "male" : "female";
        String siblingName = generateName(siblingGender);
        String relationType = siblingGender.equals("male") ? "brother" : "sister";
        
        // Varsta fratelui/surorii
        int siblingAge = npc.getAge() + random.nextInt(21) - 10; // +/- 10 ani
        siblingAge = Math.max(5, siblingAge);
        
        boolean alive = random.nextInt(100) < 90; // 90% sansa sa fie in viata
        
        String backstory = alive ? 
            "Are " + siblingAge + " ani. Locuieste in " + getRandomLocation() + "." :
            "A murit acum " + (random.nextInt(10) + 1) + " ani.";
        
        addFamilyMember(npc, siblingName, relationType, alive, null, backstory);
    }

    /**
     * Adauga un membru al familiei in baza de date
     */
    public void addFamilyMember(AINPC npc, String name, String relationType, 
                                 boolean isAlive, Integer relatedNpcId, String backstory) {
        String sql = """
            INSERT INTO npc_family (npc_id, related_npc_id, related_name, relation_type, is_alive, backstory)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            stmt.setInt(1, npc.getDatabaseId());
            if (relatedNpcId != null) {
                stmt.setInt(2, relatedNpcId);
            } else {
                stmt.setNull(2, Types.INTEGER);
            }
            stmt.setString(3, name);
            stmt.setString(4, relationType);
            stmt.setInt(5, isAlive ? 1 : 0);
            stmt.setString(6, backstory);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Eroare la adaugarea membrului familiei: " + e.getMessage());
        }
    }

    /**
     * Obtine familia unui NPC
     */
    public List<FamilyMember> getFamily(AINPC npc) {
        List<FamilyMember> family = new ArrayList<>();

        String sql = """
            SELECT related_name, relation_type, is_alive, related_npc_id, backstory
            FROM npc_family
            WHERE npc_id = ?
            ORDER BY 
                CASE relation_type
                    WHEN 'spouse' THEN 1
                    WHEN 'father' THEN 2
                    WHEN 'mother' THEN 3
                    WHEN 'son' THEN 4
                    WHEN 'daughter' THEN 5
                    WHEN 'brother' THEN 6
                    WHEN 'sister' THEN 7
                    ELSE 8
                END
        """;

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            stmt.setInt(1, npc.getDatabaseId());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Integer relatedNpcId = rs.getInt("related_npc_id");
                    if (rs.wasNull()) relatedNpcId = null;
                    
                    family.add(new FamilyMember(
                        rs.getString("related_name"),
                        rs.getString("relation_type"),
                        rs.getInt("is_alive") == 1,
                        relatedNpcId
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Eroare la obtinerea familiei: " + e.getMessage());
        }

        return family;
    }

    /**
     * Verifica daca NPC-ul are sot/sotie
     */
    public boolean hasSpouse(AINPC npc) {
        String sql = "SELECT COUNT(*) FROM npc_family WHERE npc_id = ? AND relation_type = 'spouse' AND is_alive = 1";

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            stmt.setInt(1, npc.getDatabaseId());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Eroare la verificarea sotului/sotiei: " + e.getMessage());
        }

        return false;
    }

    /**
     * Obtine un raport formatat al familiei
     */
    public String getFamilyReport(AINPC npc) {
        StringBuilder sb = new StringBuilder();
        List<FamilyMember> family = getFamily(npc);

        sb.append("&6=== Familia lui ").append(npc.getName()).append(" ===\n\n");

        if (family.isEmpty()) {
            sb.append("&7Nu are familie cunoscuta.\n");
            return sb.toString();
        }

        // Grupeaza dupa tip
        Map<String, List<FamilyMember>> grouped = new LinkedHashMap<>();
        for (FamilyMember member : family) {
            grouped.computeIfAbsent(member.getRelationType(), k -> new ArrayList<>()).add(member);
        }

        for (Map.Entry<String, List<FamilyMember>> entry : grouped.entrySet()) {
            String relationName = getRelationNameRomanian(entry.getKey());
            sb.append("&e").append(relationName).append(":\n");
            
            for (FamilyMember member : entry.getValue()) {
                sb.append("  &f- ").append(member.getName());
                if (!member.isAlive()) {
                    sb.append(" &8(decedat)");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Leaga doi NPC-uri ca familie
     */
    public void linkNPCsAsFamily(AINPC npc1, AINPC npc2, String relation1to2, String relation2to1) {
        // NPC1 -> NPC2
        addFamilyMember(npc1, npc2.getName(), relation1to2, true, npc2.getDatabaseId(), null);
        // NPC2 -> NPC1
        addFamilyMember(npc2, npc1.getName(), relation2to1, true, npc1.getDatabaseId(), null);
    }

    /**
     * Sterge familia unui NPC
     */
    public void clearFamily(AINPC npc) {
        String sql = "DELETE FROM npc_family WHERE npc_id = ?";

        try (PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(sql)) {
            stmt.setInt(1, npc.getDatabaseId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Eroare la stergerea familiei: " + e.getMessage());
        }
    }

    // Helper methods pentru generare

    private String generateName(String gender) {
        String[] names = gender.equals("male") ? MALE_NAMES : FEMALE_NAMES;
        return names[random.nextInt(names.length)];
    }

    private String getRandomOccupation() {
        String[] occupations = {
            "fermier", "fierar", "pescar", "negustor", "miner", "tamplar",
            "soldat", "paznic", "brutar", "croitor", "alchimist", "medic"
        };
        return occupations[random.nextInt(occupations.length)];
    }

    private String getRandomActivity() {
        String[] activities = {
            "casa si copii", "gradinarit", "gatit", "tesut",
            "ingrijirea familiei", "comertul local"
        };
        return activities[random.nextInt(activities.length)];
    }

    private String getRandomLocation() {
        String[] locations = {
            "satul vecin", "orasul mare", "peste munti", "langa rau",
            "in padure", "la marginea regatului", "peste mare"
        };
        return locations[random.nextInt(locations.length)];
    }

    private String getRelationNameRomanian(String relation) {
        return switch (relation.toLowerCase()) {
            case "spouse" -> "Sot/Sotie";
            case "father" -> "Tata";
            case "mother" -> "Mama";
            case "son" -> "Fiu";
            case "daughter" -> "Fiica";
            case "brother" -> "Frate";
            case "sister" -> "Sora";
            case "grandfather" -> "Bunic";
            case "grandmother" -> "Bunica";
            default -> relation;
        };
    }
}
