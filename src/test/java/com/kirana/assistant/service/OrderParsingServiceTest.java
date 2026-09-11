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
}
