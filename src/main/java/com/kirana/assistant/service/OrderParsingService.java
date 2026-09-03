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

    private static final Pattern QUANTITY_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(kg|kilo|kg|gm|gram|litre|liter|l|pack|packet|bottle|piece|pcs|pieces)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b(\\d+)\\b");

    /**
     * Parse a grocery list text into structured order items.
     * Handles formats like:
     * "2 kg atta"
     * "1 litre milk"
     * "2 Maggi"
     * "1 bread"
     */
    public List<OrderItem> parseList(String message) {
        log.info("Parsing grocery list: {}", message);
        List<OrderItem> items = new ArrayList<>();
        if (message == null || message.trim().isEmpty()) {
            return items;
        }

        String[] lines = message.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            OrderItem item = parseLine(line);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private OrderItem parseLine(String line) {
        line = line.replaceAll("[\\d]+\\.\\s*", "").trim();

        Matcher quantityMatcher = QUANTITY_PATTERN.matcher(line);
        double quantity = 1.0;
        String unit = "pc";

        if (quantityMatcher.find()) {
            quantity = Double.parseDouble(quantityMatcher.group(1));
            String unitWord = quantityMatcher.group(2).toLowerCase();
            unit = normalizeUnit(unitWord);
        } else {
            Matcher numMatcher = NUMBER_PATTERN.matcher(line);
            if (numMatcher.find()) {
                quantity = Integer.parseInt(numMatcher.group(1));
            }
        }

        String name = line.replaceAll("(?i)\\d+(\\.\\d+)?\\s*(kg|kilo|kg|gm|gram|litre|liter|l|pack|packet|bottle|piece|pcs|pieces)\\b", "")
                .replaceAll("\\d+", "")
                .replaceAll("[^a-zA-Z\\s]|^\\s+|\\s+$", "")
                .trim();

        if (name.isEmpty()) {
            return null;
        }

        name = name.substring(0, 1).toUpperCase() + (name.length() > 1 ? name.substring(1) : "");

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
