package com.example.ecommerce.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dto.AdminProductView;
import com.example.ecommerce.dto.ProductRequest;
import com.example.ecommerce.dto.ProductView;
import com.example.ecommerce.model.Category;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.ProductStatus;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.CategoryRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.ReviewRepository;

@Service
public class ProductService {
    static final Set<String> SOURCES = Set.of("MANUAL", "AI_ASSISTED");
    private static final java.util.regex.Pattern SELF_IMAGE = java.util.regex.Pattern.compile("^/api/products/[0-9]+/image([?]v=[0-9a-f-]+)?$");
    static final int MAX_INLINE_IMAGE_CHARS = 7_000_000; // about 5 MB once decoded
    private static final java.util.regex.Pattern SELF_GALLERY = java.util.regex.Pattern.compile("^/api/products/[0-9]+/gallery/([0-9]+)([?]v=[0-9a-f-]+)?$");

    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private AuditService auditService;

    // ---------------------------------------------------------------- shopper reads (published only)

    public List<Product> getAllProducts() { return productRepository.findPublic(); }

    public Product getProductById(Long id) {
        return productRepository.findPublicById(id)
                .orElseThrow(() -> new BusinessException("Product not found with id: " + id));
    }

    public List<Product> getProductsByCategory(Long categoryId) { return productRepository.findPublicByCategory(categoryId); }

    public List<Product> searchProducts(String query) {
        if (query == null || query.trim().isEmpty()) return productRepository.findPublic();
        return productRepository.searchPublic(query.trim());
    }

    public List<Product> getDeals() { return productRepository.findPublicDeals(); }

    /** Attaches real review statistics using one grouped query for the whole list. */
    public List<ProductView> views(List<Product> products) {
        Map<Long, Object[]> stats = new HashMap<>();
        if (!products.isEmpty()) {
            for (Object[] row : reviewRepository.summaries(products.stream().map(Product::getId).toList())) {
                stats.put((Long) row[0], row);
            }
        }
        return products.stream().map(p -> {
            Object[] row = stats.get(p.getId());
            if (row == null) return new ProductView(p, 0, null);
            double avg = BigDecimal.valueOf(((Number) row[2]).doubleValue()).setScale(1, RoundingMode.HALF_UP).doubleValue();
            return new ProductView(p, ((Number) row[1]).intValue(), avg);
        }).toList();
    }

    public ProductView view(Product product) { return views(List.of(product)).get(0); }

    // ---------------------------------------------------------------- admin reads

    public List<Product> adminList() { return productRepository.findByDeletedFalse(); }

    public Product adminGet(Long id) {
        return productRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("Product not found with id: " + id));
    }

    public AdminProductView adminView(Product p) {
        return new AdminProductView(p, margin(p), readinessIssues(p));
    }

    /** Gross margin on the tax-exclusive selling price, or null when cost is unknown. */
    static Double margin(Product p) {
        if (p.getCostPrice() == null) return null;
        BigDecimal sell = Boolean.TRUE.equals(p.getDeal()) && p.getDealPrice() != null ? p.getDealPrice() : p.getPrice();
        if (sell == null || sell.signum() <= 0) return null;
        return sell.subtract(p.getCostPrice()).multiply(BigDecimal.valueOf(100))
                .divide(sell, 1, RoundingMode.HALF_UP).doubleValue();
    }

    // ---------------------------------------------------------------- writes

    @Transactional
    public Product createProduct(ProductRequest request) {
        Category category = category(request.getCategoryId());
        validatePricing(request);
        validateImage(request.getImageUrl());

        Product product = new Product();
        product.setDeleted(false);
        product.setStatus(ProductStatus.DRAFT);
        product.setSource(source(request.getSource()));
        apply(product, request, category);
        product = productRepository.save(product);
        auditService.record("PRODUCT_CREATED", "Product", product.getId(), product.getSource() + " draft");

        if (request.getStatus() != null && !request.getStatus().isBlank()
                && !ProductStatus.DRAFT.name().equalsIgnoreCase(request.getStatus().trim())) {
            product = transition(product.getId(), parse(request.getStatus()));
        }
        return product;
    }

    @Transactional
    public Product updateProduct(Long id, ProductRequest request) {
        Product product = adminGet(id);
        Category category = category(request.getCategoryId());
        validatePricing(request);
        validateImage(request.getImageUrl());

        BigDecimal oldPrice = product.getPrice();
        // The admin form echoes back the public image link for uploaded pictures. That means "unchanged".
        String keepImage = SELF_IMAGE.matcher(request.getImageUrl() == null ? "" : request.getImageUrl()).matches()
                ? product.getImageUrl() : null;
        apply(product, request, category);
        if (keepImage != null) product.setImageUrl(keepImage);
        if (product.isPublished()) {
            List<String> issues = readinessIssues(product);
            if (!issues.isEmpty()) {
                throw new BusinessException("A published product must stay complete: " + String.join("; ", issues));
            }
        }
        product = productRepository.save(product);
        String detail = oldPrice != null && oldPrice.compareTo(product.getPrice()) != 0
                ? "price " + oldPrice + " -> " + product.getPrice() : "details updated";
        auditService.record("PRODUCT_UPDATED", "Product", product.getId(), detail);
        return product;
    }

    /**
     * Lifecycle: DRAFT to READY_FOR_REVIEW to APPROVED to PUBLISHED, and any state back to DRAFT.
     * Hand-made (MANUAL) listings may be published straight from DRAFT or READY_FOR_REVIEW, because the
     * admin who wrote them is the reviewer. AI-assisted listings must pass every step.
     */
    @Transactional
    public Product transition(Long id, ProductStatus target) {
        Product product = adminGet(id);
        ProductStatus from = product.getStatus();
        if (from == target) return product;

        boolean manual = !"AI_ASSISTED".equals(product.getSource());
        boolean allowed = switch (target) {
            case DRAFT -> true;
            case READY_FOR_REVIEW -> from == ProductStatus.DRAFT || from == ProductStatus.APPROVED;
            case APPROVED -> from == ProductStatus.READY_FOR_REVIEW;
            case PUBLISHED -> from == ProductStatus.APPROVED
                    || (manual && (from == ProductStatus.DRAFT || from == ProductStatus.READY_FOR_REVIEW));
        };
        if (!allowed) {
            throw new BusinessException("Cannot move a product from " + from + " to " + target
                    + (target == ProductStatus.PUBLISHED && !manual ? " (AI-assisted listings must be reviewed and approved first)" : ""));
        }
        if (target != ProductStatus.DRAFT) {
            List<String> issues = readinessIssues(product);
            if (!issues.isEmpty()) {
                throw new BusinessException("Not ready: " + String.join("; ", issues));
            }
        }
        product.setStatus(target);
        product = productRepository.save(product);
        auditService.record("PRODUCT_STATUS", "Product", product.getId(), from + " -> " + target);
        return product;
    }

    @Transactional
    public Product markStock(Long id, boolean inStock) {
        Product product = adminGet(id);
        if (!inStock) {
            product.setStockQuantity(0);
        } else if (product.getStockQuantity() == null || product.getStockQuantity() == 0) {
            product.setStockQuantity(10);
        }
        auditService.record("PRODUCT_STOCK", "Product", id, "stock set to " + product.getStockQuantity());
        return productRepository.save(product);
    }

    /** Soft delete keeps order history intact. */
    @Transactional
    public void deleteProduct(Long id) {
        Product product = adminGet(id);
        cartItemRepository.deleteByProductId(id);
        product.setDeleted(true);
        productRepository.save(product);
        auditService.record("PRODUCT_DELETED", "Product", id, product.getName());
    }

    // ---------------------------------------------------------------- rules

    /** Everything a shopper needs before a listing can go live. Empty means ready. */
    public static List<String> readinessIssues(Product p) {
        List<String> issues = new ArrayList<>();
        if (p.getName() == null || p.getName().trim().length() < 3) issues.add("add a product name");
        if (p.getDescription() == null || p.getDescription().trim().length() < 30) issues.add("write a description of at least 30 characters");
        if (p.getImageUrl() == null || p.getImageUrl().isBlank()) issues.add("add a product image");
        if (p.getPrice() == null || p.getPrice().signum() <= 0) issues.add("set a price");
        if (p.getStockQuantity() == null || p.getStockQuantity() < 0) issues.add("set the stock quantity");
        if (Boolean.TRUE.equals(p.getDeal()) && (p.getDealPrice() == null || p.getPrice() == null || p.getDealPrice().compareTo(p.getPrice()) >= 0)) {
            issues.add("the deal price must be lower than the regular price");
        }
        return issues;
    }

    private void apply(Product product, ProductRequest r, Category category) {
        product.setName(r.getName().trim());
        product.setDescription(r.getDescription().trim());
        product.setPrice(r.getPrice());
        product.setDealPrice(r.getDealPrice());
        product.setImageUrl(blankToNull(r.getImageUrl()));
        product.setStockQuantity(r.getStockQuantity());
        product.setCategory(category);
        product.setDeal(Boolean.TRUE.equals(r.getDeal()));
        product.setGstPercent(r.getGstPercent());
        product.setFabric(blankToNull(r.getFabric()));
        product.setWeave(blankToNull(r.getWeave()));
        product.setOccasion(blankToNull(r.getOccasion()));
        product.setColour(blankToNull(r.getColour()));
        product.setRegion(blankToNull(r.getRegion()));
        product.setCareInstructions(blankToNull(r.getCareInstructions()));
        product.setBlouseIncluded(r.getBlouseIncluded());
        product.setLengthMeters(r.getLengthMeters());
        product.setCostPrice(r.getCostPrice());
        product.setSupplierName(blankToNull(r.getSupplierName()));
        product.setGalleryRaw(resolveGallery(product.getGalleryRaw(), r.getGallery()));
    }

    /**
     * The admin form echoes back public gallery links for photos it did not change; those map back to the
     * stored entry. Anything else is a new photo and is validated like the main image.
     */
    static List<String> resolveGallery(List<String> stored, List<String> requested) {
        List<String> out = new java.util.ArrayList<>();
        if (requested == null) return out;
        for (String raw : requested) {
            if (raw == null || raw.isBlank()) continue;
            String url = raw.trim();
            java.util.regex.Matcher m = SELF_GALLERY.matcher(url);
            if (m.matches()) {
                int i = Integer.parseInt(m.group(1));
                if (i < stored.size()) out.add(stored.get(i));
                continue;
            }
            validateImage(url);
            out.add(url);
        }
        if (out.size() > 6) throw new BusinessException("Up to 6 extra photos");
        return out;
    }

    private void validatePricing(ProductRequest request) {
        if (Boolean.TRUE.equals(request.getDeal())
                && (request.getDealPrice() == null || request.getDealPrice().compareTo(request.getPrice()) >= 0)) {
            throw new BusinessException("A deal needs a deal price lower than the regular price");
        }
    }

    /** Only raster data URLs (never SVG, which can carry script) or plain http(s) or relative paths. */
    static void validateImage(String url) {
        if (url == null || url.isBlank()) return;
        if (url.startsWith("data:")) {
            if (!url.matches("(?s)^data:image/(png|jpeg|jpg|webp|gif);base64,[A-Za-z0-9+/=]+$")) {
                throw new BusinessException("Image must be a PNG, JPEG, WebP or GIF");
            }
            if (url.length() > MAX_INLINE_IMAGE_CHARS) {
                throw new BusinessException("Image is too large (limit about 5 MB)");
            }
            return;
        }
        boolean web = url.startsWith("https://") || url.startsWith("http://");
        boolean local = url.startsWith("/") && !url.startsWith("//");
        if (!web && !local) throw new BusinessException("Image must be an uploaded file or an http(s) link");
    }

    private Category category(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new BusinessException("Category not found with id: " + id));
    }

    private static String source(String s) {
        if (s == null || s.isBlank()) return "MANUAL";
        String v = s.trim().toUpperCase();
        if (!SOURCES.contains(v)) throw new BusinessException("Unknown source: " + s);
        return v;
    }

    public static ProductStatus parse(String s) {
        try {
            return ProductStatus.valueOf(s.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new BusinessException("Unknown status: " + s);
        }
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
