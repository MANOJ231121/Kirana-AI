package com.kirana.assistant.service;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.kirana.assistant.model.OrderItem;

/** Recognition coverage for call/storefront speech: multi-item, Hindi numbers, aliases. */
class OrderParsingServiceTest {

    private final OrderParsingService parser = new OrderParsingService();

    private OrderItem one(List<OrderItem> items, String name) {
        return items.stream()
                .filter(i -> i.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing item: " + name + " in " + items));
    }

    @Test
    void multiItemSentenceSplits() {
        List<OrderItem> items = parser.parseList("1 kg Atta and 2 kg Chini");
        assertEquals(2, items.size());
        assertEquals(1.0, one(items, "Atta").getQuantity());
        assertEquals(2.0, one(items, "Sugar").getQuantity());
        assertEquals("kg", one(items, "Atta").getUnit());
    }

    @Test
    void hindiNumbersAndAliases() {
        List<OrderItem> items = parser.parseList("do kilo chawal, teen maggi");
        assertEquals(2, items.size());
        assertEquals(2.0, one(items, "Rice").getQuantity());
        assertEquals(3.0, one(items, "Maggi").getQuantity());
    }

    @Test
    void typoAliasesNormalize() {
        List<OrderItem> items = parser.parseList("1 shappo and 2 aloo");
        assertEquals("Shampoo", one(items, "Shampoo").getName());
        assertEquals("Potato", one(items, "Potato").getName());
    }

    @Test
    void shorthandKMeansKg() {
        List<OrderItem> items = parser.parseList("1kg atta dalna list mai");
        OrderItem atta = one(items, "Atta");
        assertEquals(1.0, atta.getQuantity());
        assertEquals("kg", atta.getUnit());
    }

    @Test
    void singleWordsDefaultToOne() {
        List<OrderItem> items = parser.parseList("butter");
        assertEquals(1, items.size());
        assertEquals(1.0, items.get(0).getQuantity());
    }

    @Test
    void devanagariQuantitiesAndUnits() {
        List<OrderItem> items = parser.parseList("एक किलो आटा और दो किलो चीनी");
        assertEquals(2, items.size());
        assertEquals(1.0, one(items, "Atta").getQuantity());
        assertEquals(2.0, one(items, "Sugar").getQuantity());
        assertEquals("kg", one(items, "Atta").getUnit());
        assertEquals("kg", one(items, "Sugar").getUnit());
    }

    @Test
    void devanagariDigitsAndHalfKilo() {
        List<OrderItem> items = parser.parseList("२ किग्रा अंडे");
        OrderItem eggs = one(items, "Eggs");
        assertEquals(2.0, eggs.getQuantity());
        assertEquals("kg", eggs.getUnit());
    }

    @Test
    void devanagariMixedSentence() {
        List<OrderItem> items = parser.parseList("तोड़ दो किलो चावल, तीन मैगी पैकेट");
        assertEquals(2, items.size());
        assertEquals(2.0, one(items, "Rice").getQuantity());
        assertEquals(3.0, one(items, "Maggi").getQuantity());
    }

    @Test
    void knownListDropsGarbledSpeech() {
        // Garbled STT fragments and address words must not survive as items.
        List<OrderItem> items = parser.parseKnownList(
                "कज अतत पर एक इनट कज नह नह कल चन करन ह मझ शास्त्री नगर गली नंबर और ek kilo atta");
        assertEquals(1, items.size());
        assertEquals("Atta", items.get(0).getName());
        assertEquals(1.0, items.get(0).getQuantity());
    }
}
