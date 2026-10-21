package com.example.ecommerce.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dto.ProductRequest;
import com.example.ecommerce.model.Category;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.repository.CategoryRepository;
import com.example.ecommerce.repository.ProductRepository;

/**
 * Catalogue tooling for the owner: add another colour of a design, bulk import/export through CSV, the
 * public colour list for product pages, and the product feed that Google Shopping and Instagram/Facebook
 * Shops read. Imports only ever create drafts or update existing products; they never publish anything.
 */
@Service
public class CatalogService {
    public static final int MAX_ROWS = 500;
    public static final int MAX_CHARS = 1_500_000;

    public static final List<String> COLUMNS = List.of("sku", "name", "category", "colour", "price", "stock", "description",
            "fabric", "weave", "occasion", "region", "gst_percent", "cost_price", "supplier", "style_group",
            "length_meters", "blouse_included", "care", "image_url", "gallery_urls", "deal_price");

    public record Colour(Long id, String colour, String imageUrl, boolean inStock, String sku) {}

    public record RowResult(int row, String sku, String name, String action, String message) {}

    public record ImportReport(boolean dryRun, int created, int updated, int failed, List<RowResult> rows) {}

    @Autowired private ProductRepository products;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductService productService;
    @Autowired private AuditService audit;

    @Value("${app.frontend-url:}") private String frontendUrl;
    @Value("${app.cors.allowed-origins:}") private String corsOrigins;
    @Value("${app.store.name:Rekha Shopping Mall}") private String storeName;

    // ------------------------------------------------------------------ colours

    /** Published colours of the same design as this product (including itself), oldest first. */
    @Transactional(readOnly = true)
    public List<Colour> coloursOf(Product p) {
        List<Colour> out = new ArrayList<>();
        if (p.getStyleGroup() == null) return out;
        for (Product s : products.findPublicByStyleGroup(p.getStyleGroup())) {
            if (s.getColour() == null || s.getColour().isBlank()) continue;
            out.add(new Colour(s.getId(), s.getColour(), s.publicImageUrl(), s.getStockQuantity() != null && s.getStockQuantity() > 0, s.getSku()));
        }
        return out;
    }

    /**
     * Starts a new colour of an existing design as a DRAFT: same details and price, no stock and no photos
     * yet (photos are colour-specific), linked to the same style group so product pages show the swatches.
     */
    @Transactional
    public Product addColour(Long sourceId, String colour) {
        String c = colour == null ? "" : colour.trim();
        if (c.isEmpty() || c.length() > 40) throw new BusinessException("Enter the new colour (up to 40 characters)");
        Product src = productService.adminGet(sourceId);
        if (src.getStyleGroup() == null) {
            src.setStyleGroup("sg-" + src.getId());
            products.save(src);
        }
        for (Product sibling : products.findByStyleGroupAndDeletedFalse(src.getStyleGroup())) {
            if (c.equalsIgnoreCase(sibling.getColour())) throw new BusinessException("This design already has a " + c + " version");
        }
        Product copy = new Product(src.getName(), src.getDescription(), src.getPrice(), null, 0, src.getCategory());
        copy.setDeal(false);
        copy.setGstPercent(src.getGstPercent());
        copy.setFabric(src.getFabric());
        copy.setWeave(src.getWeave());
        copy.setOccasion(src.getOccasion());
        copy.setRegion(src.getRegion());
        copy.setCareInstructions(src.getCareInstructions());
        copy.setBlouseIncluded(src.getBlouseIncluded());
        copy.setLengthMeters(src.getLengthMeters());
        copy.setCostPrice(src.getCostPrice());
        copy.setSupplierName(src.getSupplierName());
        copy.setStatus(com.example.ecommerce.model.ProductStatus.DRAFT);
        copy.setSource("MANUAL");
        copy.setDeleted(false);
        copy.setColour(c);
        copy.setStyleGroup(src.getStyleGroup());
        copy.setSku(src.getSku() == null ? null : uniqueSku(src.getSku().replaceAll("-[A-Za-z0-9]+$", "") + "-" + slug(c)));
        copy = products.save(copy);
        audit.record("PRODUCT_COLOUR_ADDED", "Product", copy.getId(), c + " from product " + sourceId);
        return copy;
    }

    private String uniqueSku(String base) {
        String sku = base.length() > 40 ? base.substring(0, 40) : base;
        int n = 2;
        while (products.findBySkuIgnoreCaseAndDeletedFalse(sku).isPresent()) {
            String suffix = "-" + n++;
            sku = (base.length() + suffix.length() > 40 ? base.substring(0, 40 - suffix.length()) : base) + suffix;
        }
        return sku;
    }

    // ------------------------------------------------------------------ CSV export

    @Transactional(readOnly = true)
    public String exportCsv() {
        StringBuilder sb = new StringBuilder(String.join(",", COLUMNS)).append('\n');
        for (Product p : products.findByDeletedFalse()) {
            List<String> gallery = new ArrayList<>();
            for (String g : p.publicGallery()) if (g.startsWith("http")) gallery.add(g);
            String img = p.publicImageUrl();
            Object[] row = {p.getSku(), p.getName(), p.getCategory() == null ? "" : p.getCategory().getName(), p.getColour(),
                    p.getPrice(), p.getStockQuantity(), p.getDescription(), p.getFabric(), p.getWeave(), p.getOccasion(),
                    p.getRegion(), p.getGstPercent(), p.getCostPrice(), p.getSupplierName(), p.getStyleGroup(), p.getLengthMeters(),
                    p.getBlouseIncluded() == null ? "" : (p.getBlouseIncluded() ? "yes" : "no"), p.getCareInstructions(),
                    img != null && img.startsWith("http") ? img : (img != null && img.startsWith("/api/") ? "(uploaded photo)" : img),
                    String.join("|", gallery), Boolean.TRUE.equals(p.getDeal()) ? p.getDealPrice() : null};
            for (int i = 0; i < row.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(cell(row[i]));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** A header-only file the owner can fill in. */
    public String template() {
        return String.join(",", COLUMNS) + "\nKAN-RED-01,Kanchipuram Silk Saree,Silk Sarees,Red,12499,5,\"Pure zari border, rich pallu. Add at least 30 characters.\","
                + "Silk,Kanchipuram,Wedding,Tamil Nadu,5,7500,My weaver,kan-01,5.5,yes,Dry clean only,https://example.com/red-front.jpg,https://example.com/red-pallu.jpg|https://example.com/red-border.jpg,\n";
    }

    // ------------------------------------------------------------------ CSV import

    /**
     * Creates new products (as drafts) and updates existing ones matched by SKU. With dryRun nothing is
     * saved; the same report shows what would happen. Rows are independent: a bad row never blocks the rest.
     */
    public ImportReport importCsv(String csv, boolean dryRun) {
        if (csv == null || csv.isBlank()) throw new BusinessException("The file is empty");
        if (csv.length() > MAX_CHARS) throw new BusinessException("The file is too large. Import up to " + MAX_ROWS + " products at a time.");
        List<List<String>> table = parse(csv.startsWith("﻿") ? csv.substring(1) : csv);
        if (table.size() < 2) throw new BusinessException("The file needs a header row and at least one product");
        List<String> header = new ArrayList<>();
        for (String h : table.get(0)) header.add(h.trim().toLowerCase(Locale.ROOT).replace(' ', '_'));
        for (String required : List.of("name", "category", "price")) {
            if (!header.contains(required)) throw new BusinessException("Missing column \"" + required + "\". Download the template to see the columns.");
        }
        if (table.size() - 1 > MAX_ROWS) throw new BusinessException("Too many rows. Import up to " + MAX_ROWS + " products at a time.");

        Map<String, Category> byName = new LinkedHashMap<>();
        for (Category c : categories.findAll()) byName.put(c.getName().trim().toLowerCase(Locale.ROOT), c);

        List<RowResult> out = new ArrayList<>();
        java.util.Set<String> seenSkus = new java.util.HashSet<>();
        int created = 0, updated = 0, failed = 0;
        for (int i = 1; i < table.size(); i++) {
            List<String> cells = table.get(i);
            if (cells.stream().allMatch(String::isBlank)) continue;
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) row.put(header.get(c), c < cells.size() ? cells.get(c).trim() : "");
            String sku = row.getOrDefault("sku", "");
            String name = row.getOrDefault("name", "");
            try {
                if (!sku.isEmpty() && !seenSkus.add(sku.toLowerCase(Locale.ROOT))) throw new BusinessException("SKU " + sku + " appears twice in this file");
                Category category = byName.get(row.getOrDefault("category", "").toLowerCase(Locale.ROOT));
                if (category == null) {
                    throw new BusinessException("Unknown category \"" + row.get("category") + "\". Use one of: " + String.join(", ", byName.values().stream().map(Category::getName).toList()));
                }
                Product existing = sku.isEmpty() ? null : products.findBySkuIgnoreCaseAndDeletedFalse(sku).orElse(null);
                ProductRequest req = toRequest(row, category, existing);
                if (dryRun) {
                    // Same checks as a real save, without saving.
                    ProductService.validateImage(req.getImageUrl());
                    if (req.getPrice() == null || req.getPrice().signum() <= 0) throw new BusinessException("Price must be above 0");
                } else if (existing != null) {
                    productService.updateProduct(existing.getId(), req);
                } else {
                    productService.createProduct(req);
                }
                if (existing != null) { updated++; out.add(new RowResult(i + 1, sku, name, "UPDATE", dryRun ? "Would update" : "Updated")); }
                else { created++; out.add(new RowResult(i + 1, sku, name, "CREATE", dryRun ? "Would create as a draft" : "Created as a draft")); }
            } catch (BusinessException | IllegalArgumentException e) {
                failed++;
                out.add(new RowResult(i + 1, sku, name, "ERROR", e.getMessage()));
            }
        }
        if (!dryRun && created + updated > 0) audit.record("CATALOG_IMPORT", "Product", null, created + " created, " + updated + " updated, " + failed + " failed");
        return new ImportReport(dryRun, created, updated, failed, out);
    }

    private ProductRequest toRequest(Map<String, String> r, Category category, Product existing) {
        ProductRequest q = new ProductRequest();
        q.setName(r.get("name"));
        String desc = r.getOrDefault("description", "");
        q.setDescription(desc.isEmpty() && existing != null ? existing.getDescription() : (desc.isEmpty() ? r.get("name") : desc));
        q.setCategoryId(category.getId());
        q.setPrice(decimal(r, "price", true));
        String stock = r.getOrDefault("stock", "");
        q.setStockQuantity(stock.isEmpty() ? (existing != null ? existing.getStockQuantity() : 0) : integer(stock, "stock"));
        if (q.getStockQuantity() < 0) throw new BusinessException("Stock cannot be negative");
        BigDecimal deal = decimal(r, "deal_price", false);
        q.setDeal(deal != null);
        q.setDealPrice(deal);
        q.setGstPercent(decimal(r, "gst_percent", false));
        q.setCostPrice(decimal(r, "cost_price", false));
        q.setLengthMeters(decimal(r, "length_meters", false));
        q.setSku(blank(r.get("sku")));
        q.setColour(blank(r.get("colour")));
        q.setFabric(blank(r.get("fabric")));
        q.setWeave(blank(r.get("weave")));
        q.setOccasion(blank(r.get("occasion")));
        q.setRegion(blank(r.get("region")));
        q.setSupplierName(blank(r.get("supplier")));
        q.setCareInstructions(blank(r.get("care")));
        q.setStyleGroup(blank(r.get("style_group")));
        String blouse = r.getOrDefault("blouse_included", "").toLowerCase(Locale.ROOT);
        q.setBlouseIncluded(blouse.isEmpty() ? null : List.of("yes", "y", "true", "1").contains(blouse));
        String image = r.getOrDefault("image_url", "");
        q.setImageUrl(image.isEmpty() && existing != null ? existing.getImageUrl() : blank(image));
        String gallery = r.getOrDefault("gallery_urls", "");
        List<String> urls = new ArrayList<>();
        for (String u : gallery.split("\\|")) if (!u.isBlank()) urls.add(u.trim());
        if (urls.size() > 6) throw new BusinessException("Up to 6 gallery photos per product");
        // Keep the existing photos when the file leaves the column empty.
        q.setGallery(gallery.isEmpty() && existing != null ? existing.getGalleryRaw() : urls);
        q.setSource("MANUAL");
        return q;
    }

    // ------------------------------------------------------------------ product feed and sitemap

    /** Google Merchant / Meta Commerce compatible RSS feed of published products. */
    @Transactional(readOnly = true)
    public String googleFeed(String apiBase) {
        String site = siteBase();
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<rss version=\"2.0\" xmlns:g=\"http://base.google.com/ns/1.0\"><channel>\n");
        x.append("<title>").append(esc(storeName)).append("</title><link>").append(esc(site)).append("</link><description>")
                .append(esc(storeName)).append(" product feed</description>\n");
        for (Product p : products.findPublic()) {
            String image = absolute(p.publicImageUrl(), apiBase, site);
            if (image == null || p.getPrice() == null) continue;
            boolean sale = Boolean.TRUE.equals(p.getDeal()) && p.getDealPrice() != null && p.getDealPrice().compareTo(p.getPrice()) < 0;
            x.append("<item>");
            tag(x, "g:id", p.getSku() != null ? p.getSku() : "P" + p.getId());
            tag(x, "title", trunc(p.getName(), 150));
            tag(x, "description", trunc(p.getDescription(), 4900));
            tag(x, "link", site + "/products/" + p.getId());
            tag(x, "g:image_link", image);
            int extra = 0;
            for (String g : p.publicGallery()) {
                String u = absolute(g, apiBase, site);
                if (u != null && extra++ < 10) tag(x, "g:additional_image_link", u);
            }
            tag(x, "g:availability", p.getStockQuantity() != null && p.getStockQuantity() > 0 ? "in stock" : "out of stock");
            tag(x, "g:price", p.getPrice().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + " INR");
            if (sale) tag(x, "g:sale_price", p.getDealPrice().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + " INR");
            tag(x, "g:condition", "new");
            tag(x, "g:brand", storeName);
            tag(x, "g:identifier_exists", "no");
            tag(x, "g:google_product_category", "Apparel & Accessories > Clothing > Traditional & Ceremonial Clothing > Saris & Lehengas");
            if (p.getCategory() != null) tag(x, "g:product_type", p.getCategory().getName());
            if (p.getColour() != null) tag(x, "g:color", p.getColour());
            if (p.getFabric() != null) tag(x, "g:material", p.getFabric());
            if (p.getStyleGroup() != null) tag(x, "g:item_group_id", p.getStyleGroup());
            tag(x, "g:gender", "female");
            tag(x, "g:age_group", "adult");
            x.append("</item>\n");
        }
        return x.append("</channel></rss>\n").toString();
    }

    @Transactional(readOnly = true)
    public String sitemap() {
        String site = siteBase();
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String path : List.of("/", "/shipping", "/contact", "/privacy", "/terms")) {
            x.append("<url><loc>").append(esc(site + path)).append("</loc></url>\n");
        }
        for (Product p : products.findPublic()) {
            x.append("<url><loc>").append(esc(site + "/products/" + p.getId())).append("</loc></url>\n");
        }
        return x.append("</urlset>\n").toString();
    }

    // ------------------------------------------------------------------ helpers

    String siteBase() {
        String base = frontendUrl != null && !frontendUrl.isBlank() ? frontendUrl
                : corsOrigins == null || corsOrigins.isBlank() ? "" : corsOrigins.split(",")[0].trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static String absolute(String url, String apiBase, String site) {
        if (url == null || url.isBlank()) return null;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("/api/")) return apiBase.replaceAll("/api/?$", "") + url;
        return url.startsWith("/") ? site + url : null;
    }

    private static void tag(StringBuilder x, String name, String value) {
        if (value == null || value.isBlank()) return;
        x.append('<').append(name).append('>').append(esc(value)).append("</").append(name).append('>');
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String trunc(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }

    private static String slug(String s) {
        String v = s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "").trim();
        return v.isEmpty() ? "NEW" : (v.length() > 8 ? v.substring(0, 8) : v);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static BigDecimal decimal(Map<String, String> r, String key, boolean required) {
        String v = r.getOrDefault(key, "").replace(",", "").replace("₹", "").trim();
        if (v.isEmpty()) {
            if (required) throw new BusinessException("Missing " + key);
            return null;
        }
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            throw new BusinessException("\"" + r.get(key) + "\" is not a number in " + key);
        }
    }

    private static Integer integer(String v, String key) {
        try {
            return Integer.valueOf(v.replace(",", "").trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("\"" + v + "\" is not a whole number in " + key);
        }
    }

    /** Spreadsheet-safe CSV cell: quotes, and a leading apostrophe stops formulas from running. */
    static String cell(Object o) {
        if (o == null) return "";
        String v = o instanceof BigDecimal d ? d.stripTrailingZeros().toPlainString() : String.valueOf(o);
        String safe = v.replace("\"", "\"\"");
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe + "\"";
    }

    /** Minimal RFC 4180 parser: quoted cells, escaped quotes, commas and line breaks inside quotes. */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else quoted = false;
                } else cur.append(ch);
            } else if (ch == '"' && cur.length() == 0) {
                quoted = true;
            } else if (ch == ',') {
                row.add(cur.toString()); cur.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cur.toString()); cur.setLength(0);
                rows.add(row); row = new ArrayList<>();
            } else cur.append(ch);
        }
        if (quoted) throw new BusinessException("The file has an unclosed quote. Check the cells with commas or line breaks.");
        if (cur.length() > 0 || !row.isEmpty()) { row.add(cur.toString()); rows.add(row); }
        return rows;
    }
}
