package com.example.ecommerce.config;

import com.example.ecommerce.model.*;
import com.example.ecommerce.repository.CategoryRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.RoleRepository;
import com.example.ecommerce.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.Set;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Seed account credentials. Override with the ADMIN_* / CUSTOMER_* environment
    // variables when deploying; the defaults exist only for local development.
    @Value("${app.seed.admin.username}")
    private String adminUsername;

    @Value("${app.seed.admin.email}")
    private String adminEmail;

    @Value("${app.seed.admin.password}")
    private String adminPassword;

    @Value("${app.seed.customer.username}")
    private String customerUsername;

    @Value("${app.seed.customer.email}")
    private String customerEmail;

    @Value("${app.seed.customer.password}")
    private String customerPassword;

    @Override
    public void run(String... args) throws Exception {
        // 1. Seed Roles
        if (roleRepository.count() == 0) {
            roleRepository.save(new Role(ERole.ROLE_CUSTOMER));
            roleRepository.save(new Role(ERole.ROLE_ADMIN));
            System.out.println("Seeded database roles.");
        }

        // Retrieve Roles for User Seeding
        Role customerRole = roleRepository.findByName(ERole.ROLE_CUSTOMER)
                .orElseThrow(() -> new RuntimeException("Role customer not found"));
        Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
                .orElseThrow(() -> new RuntimeException("Role admin not found"));

        // 2. Seed Users (Admin & Customer)
        if (userRepository.count() == 0) {
            // Seed Admin User
            User admin = new User(
                    adminUsername,
                    adminEmail,
                    passwordEncoder.encode(adminPassword),
                    "InfinityStore",
                    "Administrator"
            );
            admin.setPhone("9999999999");
            admin.setAddresses("InfinityStore Admin Office");
            Set<Role> adminRoles = new HashSet<>();
            adminRoles.add(adminRole);
            adminRoles.add(customerRole);
            admin.setRoles(adminRoles);
            userRepository.save(admin);

            // Seed Customer User
            User customer = new User(
                    customerUsername,
                    customerEmail,
                    passwordEncoder.encode(customerPassword),
                    "John",
                    "Doe"
            );
            customer.setPhone("8888888888");
            customer.setAddresses("221B Demo Street, Bengaluru");
            Set<Role> customerRoles = new HashSet<>();
            customerRoles.add(customerRole);
            customer.setRoles(customerRoles);
            userRepository.save(customer);

            System.out.println("Seeded admin and customer accounts.");
            if ("admin123".equals(adminPassword)) {
                System.out.println("WARNING: the admin account is using the built-in development password. "
                        + "Set the ADMIN_PASSWORD environment variable before exposing this deployment.");
            }
        }

        // 3. Seed Categories & Products
        if (categoryRepository.count() == 0) {
            Category silk = categoryRepository.save(new Category("Silk Sarees", "Traditional handwoven silk sarees for weddings and festive occasions"));
            Category cotton = categoryRepository.save(new Category("Cotton Sarees", "Lightweight breathable handloom cotton sarees for daily and office wear"));
            Category designer = categoryRepository.save(new Category("Designer Sarees", "Contemporary designer sarees with modern drapes and embellishments"));
            Category bridal = categoryRepository.save(new Category("Bridal Sarees", "Opulent bridal sarees with heavy zari work and intricate handcrafted detail"));

            System.out.println("Seeded saree categories.");

            // 4. Seed Products
            // Silk Sarees
            Product kanchipuram = new Product(
                    "Kanchipuram Pure Silk Saree",
                    "Handwoven in Kanchipuram from pure mulberry silk with a contrast gold zari border and traditional temple motifs. Comes with an unstitched blouse piece.",
                    12499.00,
                    "/images/sarees/kanchipuram-silk-saree.jpg",
                    15,
                    silk
            );
            kanchipuram.setDeal(true);
            kanchipuram.setDealPrice(9999.00);
            kanchipuram.setRating(4.9);
            productRepository.save(kanchipuram);

            Product banarasi = new Product(
                    "Banarasi Katan Silk Saree",
                    "Classic Banarasi weave in lustrous katan silk with intricate gold and silver brocade work across the pallu. A timeless choice for receptions and festivals.",
                    8999.00,
                    "/images/sarees/banarasi-silk-saree.jpg",
                    25,
                    silk
            );
            banarasi.setDeal(true);
            banarasi.setDealPrice(7499.00);
            banarasi.setRating(4.8);
            productRepository.save(banarasi);

            Product mysore = new Product(
                    "Mysore Crepe Silk Saree",
                    "Soft mysore crepe silk in a serene mint tone with a fine zari border. Feather light, easy to drape, and perfect for long celebration days.",
                    4499.00,
                    "/images/sarees/mysore-crepe-silk-saree.jpg",
                    10,
                    silk
            );
            mysore.setRating(4.6);
            productRepository.save(mysore);

            // Cotton Sarees
            Product chanderi = new Product(
                    "Chanderi Silk Cotton Saree",
                    "Handloom Chanderi in a glowing pink and orange palette with delicate gold buttis and a rich zari border. Breathable silk cotton blend with a subtle sheen.",
                    2899.00,
                    "/images/sarees/chanderi-silk-cotton-saree.jpg",
                    29,
                    cotton
            );
            chanderi.setRating(4.5);
            productRepository.save(chanderi);

            Product bengal = new Product(
                    "Bengal Handloom Cotton Saree",
                    "Traditional Bengal handloom in deep indigo with woven zari buttas and a contrast red and gold border. Crisp, comfortable, and made for everyday elegance.",
                    1499.00,
                    "/images/sarees/bengal-handloom-cotton-saree.jpg",
                    50,
                    cotton
            );
            bengal.setRating(4.4);
            productRepository.save(bengal);

            Product kasavu = new Product(
                    "Kerala Kasavu Cotton Saree",
                    "Authentic Kerala kasavu in off white pure cotton with a broad golden kasavu border. The signature drape for Onam, Vishu and temple visits.",
                    1899.00,
                    "/images/sarees/kerala-kasavu-saree.jpg",
                    12,
                    cotton
            );
            kasavu.setRating(4.7);
            productRepository.save(kasavu);

            // Designer Sarees
            Product georgette = new Product(
                    "Designer Georgette Party Saree",
                    "Modern teal georgette with a woven mustard border and a fluid, easy fall. Pairs effortlessly with a statement blouse for evening parties.",
                    3799.00,
                    "/images/sarees/designer-georgette-saree.jpg",
                    99,
                    designer
            );
            georgette.setDeal(true);
            georgette.setDealPrice(2999.00);
            georgette.setRating(4.3);
            productRepository.save(georgette);

            Product organza = new Product(
                    "Organza Zari Butta Saree",
                    "Sheer emerald organza scattered with gold zari buttas and finished with a contrast brocade border. Light as air with a graceful festive drape.",
                    3299.00,
                    "/images/sarees/organza-zari-saree.jpg",
                    35,
                    designer
            );
            organza.setRating(4.5);
            productRepository.save(organza);

            // Bridal Sarees
            Product bridalSaree = new Product(
                    "Bridal Red Zari Silk Saree",
                    "A statement bridal drape in deep red silk with dense gold zari embroidery, hand embellished detailing and a matching dupatta. Crafted for the wedding day.",
                    18999.00,
                    "/images/sarees/bridal-red-zari-saree.jpg",
                    0,
                    bridal
            );
            bridalSaree.setRating(4.9);
            productRepository.save(bridalSaree);

            System.out.println("Seeded default saree inventory.");
        }
    }
}
