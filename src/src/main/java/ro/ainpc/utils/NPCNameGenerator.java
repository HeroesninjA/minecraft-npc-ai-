package ro.ainpc.utils;

import java.util.Locale;
import java.util.Random;

public final class NPCNameGenerator {

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

    private NPCNameGenerator() {
    }

    public static String randomName(String gender, Random random) {
        String normalizedGender = gender == null ? "male" : gender.toLowerCase(Locale.ROOT);
        String[] pool = "female".equals(normalizedGender) ? FEMALE_NAMES : MALE_NAMES;
        return pool[random.nextInt(pool.length)];
    }
}
