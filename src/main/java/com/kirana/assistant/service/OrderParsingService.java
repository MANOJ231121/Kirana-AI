package com.kirana.assistant.service;

import com.kirana.assistant.model.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OrderParsingService {

    private static final Logger log = LoggerFactory.getLogger(OrderParsingService.class);

    private static final Pattern QUANTITY_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(kg|kilo|gm|gram|litre|liter|l|pack|packet|bottle|piece|pcs|pieces)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b(\\d+)\\b");
    private static final Pattern HINDI_NUMBER_PATTERN =
            Pattern.compile("\\b(ek|do|teen|char|chaar|paanch|panch|cheh|chhah|saat|aath|nau|das)\\b",
                    Pattern.CASE_INSENSITIVE);
    /** Splits "1 kg Atta and 2 kg Chini" / "atta, chini aur dal" into fragments. */
    private static final Pattern FRAGMENT_SPLIT = Pattern.compile(
            "\\s*(?:,|&|\\bplus\\b|\\bor\\b|\\band\\b|\\baur\\b|\\btatha\\b|\\s+with\\s+)\\s*",
            Pattern.CASE_INSENSITIVE);

    private static final java.util.Map<String, Double> HINDI_NUMBERS = java.util.Map.ofEntries(
            java.util.Map.entry("ek", 1.0), java.util.Map.entry("do", 2.0),
            java.util.Map.entry("teen", 3.0), java.util.Map.entry("char", 4.0),
            java.util.Map.entry("chaar", 4.0), java.util.Map.entry("paanch", 5.0),
            java.util.Map.entry("panch", 5.0), java.util.Map.entry("cheh", 6.0),
            java.util.Map.entry("chhah", 6.0), java.util.Map.entry("saat", 7.0),
            java.util.Map.entry("aath", 8.0), java.util.Map.entry("nau", 9.0),
            java.util.Map.entry("das", 10.0));

    /** Spoken aliases → catalog names (typos, Hindi names, plurals). */
    private static final java.util.Map<String, String> ALIASES = java.util.Map.ofEntries(
            java.util.Map.entry("maggie", "Maggi"), java.util.Map.entry("maggy", "Maggi"),
            java.util.Map.entry("noodles", "Maggi"),
            java.util.Map.entry("shappo", "Shampoo"), java.util.Map.entry("shampu", "Shampoo"),
            java.util.Map.entry("shampoo", "Shampoo"),
            java.util.Map.entry("chawal", "Rice"), java.util.Map.entry("chaval", "Rice"),
            java.util.Map.entry("aloo", "Potato"), java.util.Map.entry("alu", "Potato"),
            java.util.Map.entry("pyaaz", "Onion"), java.util.Map.entry("pyaaj", "Onion"),
            java.util.Map.entry("tamatar", "Tomato"), java.util.Map.entry("tamaatar", "Tomato"),
            java.util.Map.entry("chini", "Sugar"), java.util.Map.entry("cheeni", "Sugar"),
            java.util.Map.entry("namak", "Salt"), java.util.Map.entry("tel", "Oil"),
            java.util.Map.entry("doodh", "Milk"), java.util.Map.entry("dudh", "Milk"),
            java.util.Map.entry("anda", "Eggs"), java.util.Map.entry("ande", "Eggs"),
            java.util.Map.entry("egg", "Eggs"), java.util.Map.entry("eggs", "Eggs"),
            java.util.Map.entry("chai", "Tea"), java.util.Map.entry("chaay", "Tea"),
            java.util.Map.entry("makhan", "Butter"), java.util.Map.entry("makkhan", "Butter"),
            java.util.Map.entry("atta", "Atta"), java.util.Map.entry("aata", "Atta"),
            java.util.Map.entry("dal", "Dal"), java.util.Map.entry("daal", "Dal"),
            java.util.Map.entry("biscuits", "Biscuit"), java.util.Map.entry("biskit", "Biscuit"),
            java.util.Map.entry("bread", "Bread"), java.util.Map.entry("detergent", "Detergent"));

    /**
     * Parse a grocery list text into structured order items.
     * Handles formats like:
     * "2 kg atta"
     * "1 litre milk"
     * "1 kg Atta and 2 kg Chini"
     * "do kilo chawal, teen maggi"
     * "shappo"
     */
    public List<OrderItem> parseList(String message) {
        log.info("Parsing grocery list: {}", message);
        List<OrderItem> items = new ArrayList<>();
        if (message == null || message.trim().isEmpty()) {
            return items;
        }

        String[] lines = message.split("\n");
        for (String line : lines) {
            for (String fragment : splitFragments(line)) {
                OrderItem item = parseLine(fragment);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    private List<String> splitFragments(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) {
            return out;
        }
        for (String part : FRAGMENT_SPLIT.split(line.trim())) {
            String p = part.trim().replaceAll("^[.\\-•]+", "").trim();
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        if (out.isEmpty() && !line.trim().isEmpty()) {
            out.add(line.trim());
        }
        return out;
    }

    private OrderItem parseLine(String line) {
        line = line.replaceAll("[\\d]+\\.\\s*", "").trim();
        // Shorthand: "1k atta" means 1 kg in kirana speech (k attached to digits).
        line = line.replaceAll("(?i)\\b(\\d+(?:\\.\\d+)?)k\\b", "$1 kg").trim();
        // Leading wake word is not an item: "Siri, 1 kg atta".
        line = line.replaceAll("(?i)^\\s*(siri|kirana)\\b[:,\\s]*", "").trim();
        if (line.isEmpty()) {
            return null;
        }

        Matcher quantityMatcher = QUANTITY_PATTERN.matcher(line);
        double quantity = 1.0;
        String unit = "pc";
        boolean matchedDigits = false;

        if (quantityMatcher.find()) {
            matchedDigits = true;
            quantity = Double.parseDouble(quantityMatcher.group(1));
            String unitWord = quantityMatcher.group(2).toLowerCase();
            unit = normalizeUnit(unitWord);
        } else {
            Matcher numMatcher = NUMBER_PATTERN.matcher(line);
            if (numMatcher.find()) {
                matchedDigits = true;
                quantity = Integer.parseInt(numMatcher.group(1));
            } else {
                // Hindi number words: "do kilo chawal", "teen maggi"
                Matcher hindiMatcher = HINDI_NUMBER_PATTERN.matcher(line);
                if (hindiMatcher.find()) {
                    quantity = HINDI_NUMBERS.getOrDefault(
                            hindiMatcher.group(1).toLowerCase(), 1.0);
                    // Bare unit next to a Hindi number: "do kilo chawal" -> kg
                    Matcher bareUnit = Pattern.compile(
                            "(?i)\\b(kg|kilo|gm|gram|litre|liter|l|pack|packet|bottle|piece|pcs|pieces)\\b")
                            .matcher(line);
                    if (bareUnit.find()) {
                        unit = normalizeUnit(bareUnit.group(1).toLowerCase());
                    }
                }
            }
        }

        String name = line.replaceAll("(?i)\\d+(\\.\\d+)?\\s*(kg|kilo|gm|gram|litre|liter|l|pack|packet|bottle|piece|pcs|pieces)\\b", "")
                .replaceAll("\\d+", "")
                .replaceAll("(?i)\\b(ek|do|teen|char|chaar|paanch|panch|cheh|chhah|saat|aath|nau|das)\\b", "");
        if (!matchedDigits) {
            // "do kilo chawal" leaves a bare "kilo" behind — drop unit words.
            name = name.replaceAll("(?i)\\b(kg|kilo|gm|gram|litre|liter|l|pack|packet|bottle|piece|pcs|pieces)\\b", "");
        }
        name = name
                // Command filler, not items: "atta dalna list mai", "mujhe dedo"
                .replaceAll("(?i)\\b(dalna|dalo|dena|dedo|lena|lo|add|karo|kar|chahiye|chahida|please|kripya|list|mein|mai|mujhe|muje|mera|meri|mere|ko|ke|liye|thoda|zara|bhej|bhejo|lao|laa|de|bhaiya|bhai)\\b", "")
                .replaceAll("[^\\p{L}\\s]|^\\s+|\\s+$", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (name.isEmpty()) {
            return null;
        }

        String alias = ALIASES.get(name.toLowerCase());
        if (alias != null) {
            name = alias;
        } else {
            name = name.substring(0, 1).toUpperCase() + (name.length() > 1 ? name.substring(1) : "");
        }

        log.debug("Parsed item: {} x {} {}", name, quantity, unit);
        return new OrderItem(name, quantity, unit);
    }

    private String normalizeUnit(String unitWord) {
        switch (unitWord) {
            case "kg":
            case "kilo":
                return "kg";
            case "gm":
            case "gram":
                return "gm";
            case "litre":
            case "liter":
            case "l":
                return "litre";
            case "pack":
            case "packet":
                return "packet";
            case "bottle":
                return "bottle";
            case "piece":
            case "pcs":
            case "pieces":
                return "piece";
            default:
                return "pc";
        }
    }
}
