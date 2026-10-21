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
    private com.example.ecommerce.repository.CouponRepository couponRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.example.ecommerce.repository.StoreVideoRepository videoRepository;

    @Autowired
    private com.example.ecommerce.repository.TestimonialRepository testimonialRepository;

    private void video(String title, String caption, StoreVideo.Kind kind, String url, String poster, Long productId, int order) {
        StoreVideo v = new StoreVideo();
        v.setTitle(title); v.setCaption(caption); v.setKind(kind); v.setVideoUrl(url); v.setPosterUrl(poster);
        v.setProductId(productId); v.setSortOrder(order);
        videoRepository.save(v);
    }

    private Product saree(Category category, String name, String description, String price, String slug, int stock, double rating,
                          String fabric, String weave, String occasion, String colour, String region, String dealPrice) {
        Product p = new Product(name, description, new java.math.BigDecimal(price), "/images/sarees/" + slug + ".jpg", stock, category);
        if (dealPrice != null) { p.setDeal(true); p.setDealPrice(new java.math.BigDecimal(dealPrice)); }
        p.setRating(rating);
        p.setStatus(com.example.ecommerce.model.ProductStatus.PUBLISHED);
        p.setFabric(fabric); p.setWeave(weave); p.setOccasion(occasion); p.setColour(colour); p.setRegion(region);
        return productRepository.save(p);
    }

    private void sampleQuote(String name, String city, String quote, int rating, Long productId, int order) {
        Testimonial t = new Testimonial();
        t.setName(name); t.setLocation(city); t.setQuote(quote); t.setRating(rating); t.setProductId(productId);
        t.setSample(true); t.setSortOrder(order);
        testimonialRepository.save(t);
    }

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

    // Sample sarees are for local development only. A real store must never launch with them.
    @Value("${app.seed.demo-catalog:false}")
    private boolean demoCatalog;

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
        if (userRepository.count() == 0 && !adminPassword.isBlank()) {
            // Seed Admin User
            User admin = new User(
                    adminUsername,
                    adminEmail,
                    passwordEncoder.encode(adminPassword),
                    "Rekha Shopping Mall",
                    "Administrator"
            );
            admin.setPhone("9999999999");
            admin.setAddresses("Rekha Shopping Mall Admin Office");
            Set<Role> adminRoles = new HashSet<>();
            adminRoles.add(adminRole);
            adminRoles.add(customerRole);
            admin.setRoles(adminRoles);
            userRepository.save(admin);

            // Seed Customer User (demo account; only when CUSTOMER_PASSWORD is provided)
            if (!customerPassword.isBlank()) {
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
            }

            System.out.println("Seeded admin account" + (customerPassword.isBlank() ? "." : " and demo customer account."));
            if ("admin123".equals(adminPassword)) {
                System.out.println("WARNING: the admin account is using the built-in development password. "
                        + "Set the ADMIN_PASSWORD environment variable before exposing this deployment.");
            }
        }

        // 2b. Keep the admin password in step with ADMIN_PASSWORD.
        // The seeding block above only runs on an empty database, and an admin cannot
        // change their own password through /api/user/profile, so this is what lets an
        // existing deployment have its credentials rotated.
        if (!adminPassword.isBlank()) userRepository.findByUsername(adminUsername).ifPresent(existingAdmin -> {
            if (!passwordEncoder.matches(adminPassword, existingAdmin.getPassword())) {
                existingAdmin.setPassword(passwordEncoder.encode(adminPassword));
                userRepository.save(existingAdmin);
                System.out.println("Updated the admin password to match ADMIN_PASSWORD.");
            }
        });

        // 3. Seed Categories & Products
        if (demoCatalog && categoryRepository.count() == 0) {
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
                    new java.math.BigDecimal("12499.00"),
                    "/images/sarees/kanchipuram-silk-saree.jpg",
                    15,
                    silk
            );
            kanchipuram.setDeal(true);
            kanchipuram.setDealPrice(new java.math.BigDecimal("9999.00"));
            kanchipuram.setRating(4.9);
            kanchipuram.setStatus(com.example.ecommerce.model.ProductStatus.PUBLISHED);
            kanchipuram.setFabric("Silk"); kanchipuram.setWeave("Kanchipuram"); kanchipuram.setOccasion("Wedding"); kanchipuram.setColour("Maroon & gold"); kanchipuram.setRegion("Tamil Nadu");
            productRepository.save(kanchipuram);

            Product banarasi = new Product(
                    "Banarasi Katan Silk Saree",
                    "Classic Banarasi weave in lustrous katan silk with intricate gold and silver brocade work across the pallu. A timeless choice for receptions and festivals.",
                    new java.math.BigDecimal("8999.00"),
                    "/images/sarees/banarasi-silk-saree.jpg",
                    25,
                    silk
            );
            banarasi.setDeal(true);
            banarasi.setDealPrice(new java.math.BigDecimal("7499.00"));
            banarasi.setRating(4.8);
            banarasi.setStatus(com.example.ecommerce.model.ProductStatus.PUBLISHED);
            banarasi.setFabric("Silk"); banarasi.setWeave("Banarasi katan"); banarasi.setOccasion("Festive"); banarasi.setColour("Gold"); banarasi.setRegion("Uttar Pradesh");
            productRepository.save(banarasi);

            Product mysore = new Product(
                    "Mysore Crepe Silk Saree",
                    "Soft mysore crepe silk in a serene mint tone with a fine zari border. Feather light, easy to drape, and perfect for long celebration days.",
                    new java.math.BigDecimal("4499.00"),
                    "/images/sarees/mysore-crepe-silk-saree.jpg",
                    10,
                    silk
            );
            mysore.setRating(4.6);
            mysore.setStatus(com.example.ecommerce.model.ProductStatus.PUBLISHED);
            mysore.setFabric("Silk"); mysore.setWeave("Crepe"); mysore.setOccasion("Festive"); mysore.setColour("Mint"); mysore.setRegion("Karnataka");
            productRepository.save(mysore);

            // Cotton Sarees
            Product chanderi = new Product(
                    "Chanderi Silk Cotton Saree",
                    "Handloom Chanderi in a glowing pink and orange palette with delicate gold buttis and a rich zari border. Breathable silk cotton blend with a subtle sheen.",
                    new java.math.BigDecimal("2899.00"),
                    "/images/sarees/chanderi-silk-cotton-saree.jpg",
                    29,
                    cotton
            );
            chanderi.setRating(4.5);
            chanderi.setStatus(com.example.ecommerce.model.ProductStatus.PUBLISHED);
            chanderi.setFabric("Silk cotton"); chanderi.setWeave("Chanderi"); chanderi.setOccasion("Festive"); chanderi.setColour("Pink & orange"); chanderi.setRegion("Madhya Pradesh");
            productRepository.save(chanderi);

            Product bengal = new Product(
                    "Bengal Handloom Cotton Saree",
                    "Traditional Bengal handloom in deep indigo with woven zari buttas and a contrast red and gold border. Crisp, comfortable, and made for everyday elegance.",
                    new java.math.BigDecimal("1499.00"),
                    "/images/sarees/bengal-handloom-cotton-saree.jpg",
                    50,
                    cotton
            );
            bengal.setRating(4.4);
            bengal.setStatus(com.example.ecommerce.model.ProductStatus.PUBLISHED);
            bengal.setFabric("Cotton"); bengal.setWeave("Handloom"); bengal.setOccasion("Everyday"); bengal.setColour("Indigo"); bengal.setRegion("West Bengal");
            productRepository.save(bengal);

            Product kasavu = new Product(
                    "Kerala Kasavu Cotton Saree",
                    "Authentic Kerala kasavu in off white pure cotton with a broad golden kasavu border. The signature drape for Onam, Vishu and temple visits.",
                    new java.math.BigDecimal("1899.00"),
                    "/images/sarees/kerala-kasavu-saree.jpg",
                    12,
                    cotton
            );
            kasavu.setRating(4.7);
            kasavu.setStatus(com.example.ecommerce.model.ProductStatus.PUBLISHED);
            kasavu.setFabric("Cotton"); kasavu.setWeave("Kasavu"); kasavu.setOccasion("Temple & Onam"); kasavu.setColour("Off white & gold"); kasavu.setRegion("Kerala");
            productRepository.save(kasavu);

            // Designer Sarees
            Product georgette = new Product(
                    "Designer Georgette Party Saree",
                    "Modern teal georgette with a woven mustard border and a fluid, easy fall. Pairs effortlessly with a statement blouse for evening parties.",
                    new java.math.BigDecimal("3799.00"),
                    "/images/sarees/designer-georgette-saree.jpg",
                    99,
                    designer
            );
            georgette.setDeal(true);
            georgette.setDealPrice(new java.math.BigDecimal("2999.00"));
            georgette.setRating(4.3);
            georgette.setStatus(com.example.ecommerce.model.ProductStatus.PUBLISHED);
            georgette.setFabric("Georgette"); georgette.setWeave("Plain weave"); georgette.setOccasion("Party"); georgette.setColour("Teal"); georgette.setRegion("Mixed");
            productRepository.save(georgette);

            Product organza = new Product(
                    "Organza Zari Butta Saree",
                    "Sheer emerald organza scattered with gold zari buttas and finished with a contrast brocade border. Light as air with a graceful festive drape.",
                    new java.math.BigDecimal("3299.00"),
                    "/images/sarees/organza-zari-saree.jpg",
                    35,
                    designer
            );
            organza.setRating(4.5);
            organza.setStatus(com.example.ecommerce.model.ProductStatus.PUBLISHED);
            organza.setFabric("Organza"); organza.setWeave("Zari butta"); organza.setOccasion("Festive"); organza.setColour("Emerald"); organza.setRegion("Mixed");
            productRepository.save(organza);

            // Bridal Sarees
            Product bridalSaree = new Product(
                    "Bridal Red Zari Silk Saree",
                    "A statement bridal drape in deep red silk with dense gold zari embroidery, hand embellished detailing and a matching dupatta. Crafted for the wedding day.",
                    new java.math.BigDecimal("18999.00"),
                    "/images/sarees/bridal-red-zari-saree.jpg",
                    0,
                    bridal
            );
            bridalSaree.setRating(4.9);
            bridalSaree.setStatus(com.example.ecommerce.model.ProductStatus.PUBLISHED);
            bridalSaree.setFabric("Silk"); bridalSaree.setWeave("Zari embroidered"); bridalSaree.setOccasion("Wedding"); bridalSaree.setColour("Red"); bridalSaree.setRegion("Mixed");
            productRepository.save(bridalSaree);

            // Demo motion-design clips (rendered by tools/make_demo_videos.py) and clearly labelled sample quotes.
            // Local demo only: a real store adds its own footage and real customer words in the admin.
            video("Silk, cotton, designer, bridal", "A look across the collection", StoreVideo.Kind.STORY, "/videos/story-collection.mp4", "/videos/story-collection.jpg", null, 1);
            video("Kanchipuram Pure Silk Saree", "Mulberry silk with a gold zari border", StoreVideo.Kind.REEL, "/videos/cine-kanchipuram.mp4", "/videos/cine-kanchipuram.jpg", kanchipuram.getId(), 2);
            video("Banarasi Katan Silk Saree", "Lustrous katan silk, brocade pallu", StoreVideo.Kind.REEL, "/videos/cine-banarasi.mp4", "/videos/cine-banarasi.jpg", banarasi.getId(), 3);
            video("Bridal Red Zari Silk Saree", "Wedding-day red with dense gold zari", StoreVideo.Kind.REEL, "/videos/cine-bridal.mp4", "/videos/cine-bridal.jpg", bridalSaree.getId(), 4);
            video("Kerala Kasavu Cotton Saree", "Pure cotton with the golden kasavu border", StoreVideo.Kind.REEL, "/videos/cine-kasavu.mp4", "/videos/cine-kasavu.jpg", kasavu.getId(), 5);
            video("Designer Georgette Party Saree", "A flowing fall made for the party floor", StoreVideo.Kind.REEL, "/videos/cine-georgette.mp4", "/videos/cine-georgette.jpg", georgette.getId(), 6);
            video("Organza Zari Butta Saree", "Sheer organza scattered with zari buttas", StoreVideo.Kind.REEL, "/videos/cine-organza.mp4", "/videos/cine-organza.jpg", organza.getId(), 7);
            video("Mysore Crepe Silk Saree", "Soft crepe silk that drapes beautifully", StoreVideo.Kind.REEL, "/videos/cine-mysore.mp4", "/videos/cine-mysore.jpg", mysore.getId(), 8);
            video("Chanderi Silk Cotton Saree", "Lightweight with a sheer, glowing finish", StoreVideo.Kind.REEL, "/videos/cine-chanderi.mp4", "/videos/cine-chanderi.jpg", chanderi.getId(), 9);
            video("Bengal Handloom Cotton Saree", "Hand woven and breathable", StoreVideo.Kind.REEL, "/videos/cine-bengal.mp4", "/videos/cine-bengal.jpg", bengal.getId(), 10);
            // More sample sarees (openly licensed photos, see docs/CREDITS.md) with a cinematic reel each.
            Product x_pochampally_ikat_silk = saree(silk, "Pochampally Ikat Silk Saree", "Hand-dyed double ikat in deep indigo with geometric diamond motifs and a teal zari border. Each design is tie-dyed thread by thread before weaving.", "7999", "pochampally-ikat-silk", 14, 4.7, "Silk", "Pochampally ikat", "Festive", "Indigo & teal", "Telangana", "6499");
            Product x_sambalpuri_ikat_elephant = saree(cotton, "Sambalpuri Ikat Elephant Cotton Saree", "Odisha ikat cotton with a procession of elephants and trees in black and red, finished with a checked border. Breathable and easy for all-day wear.", "3999", "sambalpuri-ikat-elephant", 18, 4.6, "Cotton", "Sambalpuri ikat", "Everyday", "Red & black", "Odisha", null);
            Product x_sambalpuri_handloom_drape = saree(cotton, "Sambalpuri Handloom Drape Saree", "Plum handloom with temple motifs and a vivid orange border and pallu. A drape that photographs beautifully and feels light on the day.", "4299", "sambalpuri-handloom-drape", 12, 4.7, "Cotton", "Sambalpuri handloom", "Temple & Onam", "Plum & orange", "Odisha", "3699");
            Product x_paithani_silk_peacock = saree(silk, "Paithani Peacock Silk Saree", "Maharashtra paithani in turquoise and parrot green with a woven peacock pallu in gold and yellow. Heirloom weave, finished with tassels.", "15999", "paithani-silk-peacock", 6, 4.9, "Silk", "Paithani", "Wedding", "Turquoise & green", "Maharashtra", "13499");
            Product x_blush_net_wedding = saree(designer, "Blush Net Embroidered Wedding Saree", "Sheer blush net with floral sequin embroidery and a rich border, styled over a contrast blouse. Made for sangeet nights and receptions.", "11999", "blush-net-wedding", 8, 4.8, "Net", "Embroidered", "Party", "Blush & maroon", "Mixed", null);
            Product x_patola_double_ikat = saree(bridal, "Patola Double Ikat Saree", "Gujarat double ikat in rust red and ivory with traditional jaali and flower motifs. A true heirloom weave that holds its value.", "24999", "patola-double-ikat", 4, 5.0, "Silk", "Patola double ikat", "Wedding", "Rust & ivory", "Gujarat", "21999");
            Product x_banarasi_bandhani = saree(designer, "Banarasi Bandhani Crape Silk Saree", "Royal blue crape silk with all-over bandhani dots and a fine zari border and pallu. Light on the shoulder, rich in the frame.", "6999", "banarasi-bandhani", 16, 4.6, "Silk", "Banarasi bandhani", "Festive", "Royal blue", "Uttar Pradesh", null);
            Product x_koorai_silk_checks = saree(silk, "Koorai Silk Checks Saree", "Tamil Nadu wedding silk in bold purple, gold and indigo checks with a woven stripe pallu. Cheerful, durable and made for festive mornings.", "8499", "koorai-silk-checks", 10, 4.5, "Silk", "Koorai silk", "Wedding", "Purple & gold", "Tamil Nadu", null);
            Product x_red_paisley_zari_silk = saree(silk, "Red Paisley Zari Silk Saree", "Vivid red silk with a wide gold zari border of large paisleys. A classic that works for pujas, weddings and festival evenings.", "9499", "red-paisley-zari-silk", 9, 4.7, "Silk", "Zari paisley", "Festive", "Red & gold", "Karnataka", "7999");
            Product x_sambalpuri_bandha_cotton = saree(cotton, "Sambalpuri Bandha Cotton Saree", "Soft cotton with a temple-style border in grey, ivory and sea green. Gentle colours for office and everyday elegance.", "3499", "sambalpuri-bandha-cotton", 20, 4.4, "Cotton", "Sambalpuri bandha", "Everyday", "Ivory & grey", "Odisha", null);

            video("Pochampally Ikat Silk Saree", "Hand-dyed double ikat from Telangana", StoreVideo.Kind.REEL, "/videos/cine-pochampally.mp4", "/videos/cine-pochampally.jpg", x_pochampally_ikat_silk.getId(), 11);
            video("Sambalpuri Ikat Elephant Cotton Saree", "Odisha ikat with elephant motifs", StoreVideo.Kind.REEL, "/videos/cine-sambalpuri.mp4", "/videos/cine-sambalpuri.jpg", x_sambalpuri_ikat_elephant.getId(), 12);
            video("Sambalpuri Handloom Drape Saree", "Plum handloom with an orange border", StoreVideo.Kind.REEL, "/videos/cine-sambalpuri-drape.mp4", "/videos/cine-sambalpuri-drape.jpg", x_sambalpuri_handloom_drape.getId(), 13);
            video("Paithani Peacock Silk Saree", "Heirloom peacock pallu from Maharashtra", StoreVideo.Kind.REEL, "/videos/cine-paithani.mp4", "/videos/cine-paithani.jpg", x_paithani_silk_peacock.getId(), 14);
            video("Blush Net Embroidered Wedding Saree", "Sheer net with sequin florals", StoreVideo.Kind.REEL, "/videos/cine-net.mp4", "/videos/cine-net.jpg", x_blush_net_wedding.getId(), 15);
            video("Patola Double Ikat Saree", "An heirloom double ikat from Gujarat", StoreVideo.Kind.REEL, "/videos/cine-patola.mp4", "/videos/cine-patola.jpg", x_patola_double_ikat.getId(), 16);
            video("Banarasi Bandhani Crape Silk Saree", "Bandhani dots with a zari border", StoreVideo.Kind.REEL, "/videos/cine-bandhani.mp4", "/videos/cine-bandhani.jpg", x_banarasi_bandhani.getId(), 17);
            video("Koorai Silk Checks Saree", "Tamil Nadu wedding silk in bold checks", StoreVideo.Kind.REEL, "/videos/cine-koorai.mp4", "/videos/cine-koorai.jpg", x_koorai_silk_checks.getId(), 18);
            video("Red Paisley Zari Silk Saree", "A wide gold paisley zari border", StoreVideo.Kind.REEL, "/videos/cine-paisley.mp4", "/videos/cine-paisley.jpg", x_red_paisley_zari_silk.getId(), 19);
            video("Sambalpuri Bandha Cotton Saree", "A temple border in gentle greys", StoreVideo.Kind.REEL, "/videos/cine-bandha-cotton.mp4", "/videos/cine-bandha-cotton.jpg", x_sambalpuri_bandha_cotton.getId(), 20);

            sampleQuote("Priya", "Chennai", "The silk drapes beautifully and the zari border looks even better in person. Packing was neat and it arrived on time.", 5, kanchipuram.getId(), 1);
            sampleQuote("Ananya", "Bengaluru", "Ordered the kasavu for Onam. The cotton is soft and the golden border is lovely. Cash on delivery made it easy.", 5, kasavu.getId(), 2);
            sampleQuote("Meera", "Hyderabad", "The stylist chat helped me pick a saree for my sister's wedding in minutes. Exactly what I was looking for.", 5, null, 3);
            sampleQuote("Kavya", "Mumbai", "The colours match the photos, which is rare online. The delivery date estimate was accurate too.", 4, null, 4);
            sampleQuote("Lakshmi", "Kochi", "Good quality and fair pricing, with the GST shown clearly before paying. Will order again for the festive season.", 5, banarasi.getId(), 5);

            System.out.println("Seeded default saree inventory.");
        }

        // Demo welcome offer (production owners create their own under Admin > Coupons).
        if (demoCatalog && couponRepository.findByCodeIgnoreCase("WELCOME10").isEmpty()) {
            com.example.ecommerce.model.Coupon welcome = new com.example.ecommerce.model.Coupon();
            welcome.setCode("WELCOME10");
            welcome.setDescription("Welcome offer");
            welcome.setPercentOff(new java.math.BigDecimal("10"));
            welcome.setMaxDiscount(new java.math.BigDecimal("500"));
            welcome.setMinSubtotal(new java.math.BigDecimal("1499"));
            welcome.setFirstOrderOnly(true);
            couponRepository.save(welcome);
        }
    }
}
