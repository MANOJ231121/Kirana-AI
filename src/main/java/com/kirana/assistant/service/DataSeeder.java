package com.kirana.assistant.service;

import com.kirana.assistant.model.Customer;
import com.kirana.assistant.model.InventoryItem;
import com.kirana.assistant.model.WhatsAppMessage;
import com.kirana.assistant.repository.CustomerRepository;
import com.kirana.assistant.repository.InventoryItemRepository;
import com.kirana.assistant.repository.WhatsAppMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private WhatsAppMessageRepository whatsAppMessageRepository;

    @Value("${SEED_DATA:true}")
    private boolean seedEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void seedIfEmpty() {
        if (!seedEnabled) {
            return;
        }
        seedInventory();
        seedDemoCustomerWithWhatsApp();
    }

    private void seedInventory() {
        if (inventoryItemRepository.count() > 0) {
            return;
        }
        log.info("Seeding sample inventory...");

        inventoryItemRepository.save(new InventoryItem("Atta", true, 55.0, "Grains", "Maida, Besan"));
        inventoryItemRepository.save(new InventoryItem("Rice", true, 70.0, "Grains", "Poha, Daliya"));
        inventoryItemRepository.save(new InventoryItem("Dal", true, 95.0, "Grains", "Chana, Rajma"));
        inventoryItemRepository.save(new InventoryItem("Milk", true, 60.0, "Dairy", "Curd, Buttermilk"));
        inventoryItemRepository.save(new InventoryItem("Ghee", true, 620.0, "Dairy", "Butter, Oil"));
        inventoryItemRepository.save(new InventoryItem("Sugar", true, 45.0, "Staples", "Jaggery, Honey"));
        inventoryItemRepository.save(new InventoryItem("Salt", true, 20.0, "Staples", null));
        inventoryItemRepository.save(new InventoryItem("Tea", true, 180.0, "Beverages", "Coffee"));
        inventoryItemRepository.save(new InventoryItem("Maggi", true, 14.0, "Snacks", "Top Ramen, Yippee"));
        inventoryItemRepository.save(new InventoryItem("Biscuit", true, 30.0, "Snacks", "Parle-G, Marie"));
        inventoryItemRepository.save(new InventoryItem("Eggs", true, 6.5, "Dairy", null));
        inventoryItemRepository.save(new InventoryItem("Bread", true, 32.0, "Bakery", "Pav, Bun"));
        inventoryItemRepository.save(new InventoryItem("Oil", true, 130.0, "Staples", "Ghee, Butter"));
        inventoryItemRepository.save(new InventoryItem("Onion", true, 40.0, "Vegetables", null));
        inventoryItemRepository.save(new InventoryItem("Potato", true, 25.0, "Vegetables", null));
        inventoryItemRepository.save(new InventoryItem("Tomato", true, 35.0, "Vegetables", null));
        inventoryItemRepository.save(new InventoryItem("Shampoo", false, 99.0, "Personal Care", "Sabun"));
        inventoryItemRepository.save(new InventoryItem("Detergent", true, 150.0, "Household", "Washing Powder"));

        log.info("Seeded {} inventory items", inventoryItemRepository.count());
    }

    private void seedDemoCustomerWithWhatsApp() {
        String demoPhone = "+919999999999";
        Customer existing = customerRepository.findByPhoneNumber(demoPhone).orElse(null);
        if (existing == null) {
            existing = customerRepository.save(new Customer(demoPhone, "Rahul Sharma"));
            whatsAppMessageRepository.save(new WhatsAppMessage(
                    existing.getId(), demoPhone,
                    "2 kg atta\n1 litre milk\n1 Maggi\n1 bread\n250 gm sugar\n12 eggs"));
            log.info("Seeded demo customer {} with WhatsApp grocery list", demoPhone);
        }
    }
}
