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
        log.info("Seeding sample inventory with product image URLs...");

        inventoryItemRepository.save(new InventoryItem("Atta", true, 55.0, "Grains", "Maida, Besan",
                "https://images.unsplash.com/photo-1574323347407-f5e1ad6d020b?w=400&auto=format&fit=crop&q=80"));
        inventoryItemRepository.save(new InventoryItem("Rice", true, 70.0, "Grains", "Poha, Daliya",
                "https://images.unsplash.com/photo-1586201375761-83865001e31c?w=400&auto=format&fit=crop&q=80"));
        inventoryItemRepository.save(new InventoryItem("Dal", true, 95.0, "Grains", "Chana, Rajma",
                "https://images.unsplash.com/photo-1546833999-b9f581a1996d?w=400&auto=format&fit=crop&q=80"));
        inventoryItemRepository.save(new InventoryItem("Milk", true, 60.0, "Dairy", "Curd, Buttermilk",
                "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=400&auto=format&fit=crop&q=80"));
        inventoryItemRepository.save(new InventoryItem("Ghee", true, 620.0, "Dairy", "Butter, Oil",
                "https://images.unsplash.com/photo-1631451095765-2c91616fc9e6?w=400&auto=format&fit=crop&q=80"));
        inventoryItemRepository.save(new InventoryItem("Sugar", true, 45.0, "Staples", "Jaggery, Honey",
                "https://images.unsplash.com/photo-1622484210800-8851b576f9d2?w=400&auto=format&fit=crop&q=80"));
        inventoryItemRepository.save(new InventoryItem("Salt", true, 20.0, "Staples", null,
                "https://images.unsplash.com/photo-1615485290382-441e4d049cb5?w=400&auto=format&fit=crop&q=80"));
        inventoryItemRepository.save(new InventoryItem("Tea", true, 180.0, "Beverages", "Coffee",
                "https://images.unsplash.com/photo-1576092768241-dec231879fc3?w=400&auto=format&fit=crop&q=80"));
        inventoryItemRepository.save(new InventoryItem("Maggi", true, 14.0, "Snacks", "Top Ramen, Yippee",
                "https://images.unsplash.com/photo-1612927601601-6638404737ce?w=400&auto=format&fit=crop&q=80"));
        inventoryItemRepository.save(new InventoryItem("Biscuit", true, 30.0, "Snacks", "Parle-G, Marie",
                "https://images.unsplash.com/photo-1558961363-fa8fdf82db35?w=400&auto=format&fit=crop&q=80"));
        inventoryItemRepository.save(new InventoryItem("Eggs", true, 6.5, "Dairy", null,
                "https://images.unsplash.com/photo-1506976785307-8732e854ad03?w=400&auto=format&fit=crop&q=80"));
        inventoryItemRepository.save(new InventoryItem("Bread", true, 32.0, "Bakery", "Pav, Bun",
                "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400&auto=format&fit=crop&q=80"));
        inventoryItemRepository.save(new InventoryItem("Oil", true, 130.0, "Staples", "Ghee, Butter",
                "https://images.unsplash.com/photo-1474979266404-7eaacbcd87c5?w=400&auto=format&fit=crop&q=80"));
        inventoryItemRepository.save(new InventoryItem("Onion", true, 40.0, "Vegetables", null,
                "https://images.unsplash.com/photo-1618512496248-a07fe83aa8cb?w=400&auto=format&fit=crop&q=80"));
        inventoryItemRepository.save(new InventoryItem("Potato", true, 25.0, "Vegetables", null,
                "https://images.unsplash.com/photo-1518977676601-b53f82aba655?w=400&auto=format&fit=crop&q=80"));
        inventoryItemRepository.save(new InventoryItem("Tomato", true, 35.0, "Vegetables", null,
                "https://images.unsplash.com/photo-1592924357228-91a4daadcfea?w=400&auto=format&fit=crop&q=80"));
        inventoryItemRepository.save(new InventoryItem("Shampoo", false, 99.0, "Personal Care", "Sabun",
                "https://images.unsplash.com/photo-1535585209827-a15fcdbc4c2d?w=400&auto=format&fit=crop&q=80"));
        inventoryItemRepository.save(new InventoryItem("Detergent", true, 150.0, "Household", "Washing Powder",
                "https://images.unsplash.com/photo-1585842378054-ee2e52f94ba2?w=400&auto=format&fit=crop&q=80"));

        log.info("Seeded {} inventory items with images", inventoryItemRepository.count());
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
