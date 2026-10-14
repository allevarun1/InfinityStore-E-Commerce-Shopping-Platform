package com.example.ecommerce.service.studio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.ecommerce.model.Category;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.repository.CategoryRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.security.RateLimiter;
import com.example.ecommerce.service.AuditService;
import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.TooManyRequestsException;

/**
 * Product Studio. Suggests a name, description and a price for a new listing. It never saves anything:
 * the owner edits the suggestion and saves it as a draft that still has to be reviewed before publishing.
 */
@Service
public class StudioService {
    private static final Logger log = LoggerFactory.getLogger(StudioService.class);
    /** Words that assert a fact. If the writer uses one the owner did not supply, the owner is warned. */
    private static final List<String> CLAIM_WORDS = List.of("pure", "handwoven", "handloom", "hand-woven", "authentic",
            "genuine", "organic", "gi-tagged", "certified", "mulberry", "real zari", "heirloom", "award");

    public record Draft(String name, String description, String generatedBy, boolean sentToExternalService,
                        String warning, BigDecimal suggestedPrice, String priceReasoning, String marketContext) {}

    public record Status(String writer, boolean external, double targetMarginPercent) {}

    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private TemplateListingWriter template;
    @Autowired private AnthropicListingWriter anthropic;
    @Autowired private AuditService audit;

    @Value("${app.studio.target-margin-percent:35}")
    private double targetMargin;

    private final RateLimiter limiter = new RateLimiter();

    public Status status() {
        ListingWriter w = writer();
        return new Status(w.id(), w.external(), targetMargin);
    }

    private ListingWriter writer() {
        return anthropic.configured() ? anthropic : template;
    }

    public Draft generate(StudioRequest request) {
        String actor = SecurityContextHolder.getContext().getAuthentication() == null
                ? "unknown" : SecurityContextHolder.getContext().getAuthentication().getName();
        long wait = limiter.tryAcquire("studio:" + actor, 20, 10 * 60_000L);
        if (wait > 0) throw new TooManyRequestsException("Too many drafts requested. Try again in " + wait + " seconds.", wait);

        Category category = categories.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException("Category not found"));

        ListingWriter chosen = writer();
        ListingWriter.ListingText text;
        String warning = null;
        boolean external = chosen.external();
        String by = chosen.id();
        try {
            text = chosen.write(category.getName(), request);
        } catch (Exception e) {
            // Never leak provider details to the browser; fall back to the offline writer.
            log.warn("Listing writer '{}' failed: {}", chosen.id(), e.getClass().getSimpleName());
            text = template.write(category.getName(), request);
            by = template.id();
            external = false;
            warning = "The AI writer was unavailable, so a basic template draft was used instead.";
        }

        List<String> unsupported = unsupportedClaims(text, request);
        if (!unsupported.isEmpty()) {
            String w = "The text uses claims you did not provide (" + String.join(", ", unsupported) + "). Remove them unless they are true.";
            warning = warning == null ? w : warning + " " + w;
        }

        if (text.description().length() < 30) {
            String w = "The description is short. Add details such as the border, pallu or a note about the weaver before publishing.";
            warning = warning == null ? w : warning + " " + w;
        }

        audit.record("STUDIO_GENERATE", "Product", null, "writer=" + by + ", category=" + category.getName());

        BigDecimal price = null;
        String reasoning = null;
        if (request.getCostPrice() != null && request.getCostPrice().signum() > 0 && targetMargin > 0 && targetMargin < 90) {
            BigDecimal divisor = BigDecimal.ONE.subtract(BigDecimal.valueOf(targetMargin).divide(BigDecimal.valueOf(100)));
            BigDecimal raw = request.getCostPrice().divide(divisor, 2, RoundingMode.HALF_UP);
            // Round up to a price ending in 9, which is how shoppers expect saree prices to read.
            price = raw.divide(BigDecimal.TEN, 0, RoundingMode.CEILING).multiply(BigDecimal.TEN).subtract(BigDecimal.ONE);
            reasoning = "Cost " + request.getCostPrice().stripTrailingZeros().toPlainString() + " divided by (1 minus "
                    + (int) targetMargin + "% target margin), rounded up. It is a starting point, not a market price.";
        }
        return new Draft(text.name(), text.description(), by, external, warning, price, reasoning, marketContext(category));
    }

    /** Where the owner's own live range in this category sits, so a price can be sanity-checked. */
    private String marketContext(Category category) {
        List<BigDecimal> prices = new ArrayList<>();
        for (Product p : products.findPublicByCategory(category.getId())) {
            if (p.getPrice() != null) prices.add(p.getPrice());
        }
        if (prices.isEmpty()) return null;
        prices.sort(BigDecimal::compareTo);
        BigDecimal median = prices.get(prices.size() / 2);
        return "Your " + prices.size() + " live " + category.getName() + " range from " + prices.get(0).stripTrailingZeros().toPlainString()
                + " to " + prices.get(prices.size() - 1).stripTrailingZeros().toPlainString() + " (median " + median.stripTrailingZeros().toPlainString() + ").";
    }

    static List<String> unsupportedClaims(ListingWriter.ListingText text, StudioRequest facts) {
        String supplied = String.join(" ", nz(facts.getFabric()), nz(facts.getWeave()), nz(facts.getOccasion()), nz(facts.getColour()),
                nz(facts.getRegion()), nz(facts.getNotes()), nz(facts.getCareInstructions())).toLowerCase(Locale.ROOT);
        String output = (text.name() + " " + text.description()).toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        for (String word : CLAIM_WORDS) {
            if (Pattern.compile("(?<![a-z])" + Pattern.quote(word) + "(?![a-z])").matcher(output).find() && !supplied.contains(word)) {
                found.add(word);
            }
        }
        return found;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
