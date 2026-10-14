package com.example.ecommerce.service.studio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Deterministic, offline writer. It only rearranges the facts it is given, so it cannot invent
 * claims such as "pure silk" or "handwoven". This is the default and needs no account or key.
 */
@Component
public class TemplateListingWriter implements ListingWriter {
    @Override public String id() { return "template"; }

    @Override public boolean external() { return false; }

    @Override
    public ListingText write(String categoryName, StudioRequest f) {
        List<String> nameParts = new ArrayList<>();
        add(nameParts, f.getColour());
        add(nameParts, f.getWeave());
        String fabric = clean(f.getFabric());
        if (fabric != null && !contains(nameParts, fabric)) nameParts.add(fabric);
        if (nameParts.isEmpty() && categoryName != null) nameParts.add(categoryName.replaceAll("(?i)\\s*sarees?$", "").trim());
        String name = String.join(" ", nameParts).trim() + " Saree";
        name = name.trim().equals("Saree") ? "Saree" : name.trim();

        List<String> sentences = new ArrayList<>();
        StringBuilder phrase = new StringBuilder();
        String colour = clean(f.getColour());
        String weave = clean(f.getWeave());
        if (colour != null) phrase.append(colour.toLowerCase(Locale.ROOT)).append(' ');
        if (weave != null) phrase.append(weave).append(' ');
        if (fabric != null) phrase.append(fabric.toLowerCase(Locale.ROOT)).append(' ');
        phrase.append("saree");
        String lead = phrase.toString();
        StringBuilder first = new StringBuilder("aeiouAEIOU".indexOf(lead.charAt(0)) >= 0 ? "An " : "A ").append(lead);
        String region = clean(f.getRegion());
        if (region != null) first.append(" from ").append(region);
        sentences.add(first + ".");

        if (f.getLengthMeters() != null && f.getLengthMeters().signum() > 0) {
            sentences.add(f.getLengthMeters().stripTrailingZeros().toPlainString() + " metres long"
                    + (Boolean.TRUE.equals(f.getBlouseIncluded()) ? ", with a blouse piece included." : "."));
        } else if (Boolean.TRUE.equals(f.getBlouseIncluded())) {
            sentences.add("A blouse piece is included.");
        }
        String occasion = clean(f.getOccasion());
        if (occasion != null) sentences.add("Suited to " + occasion.toLowerCase(Locale.ROOT) + " wear.");
        String notes = clean(f.getNotes());
        if (notes != null) sentences.add(notes.endsWith(".") ? notes : notes + ".");
        String care = clean(f.getCareInstructions());
        if (care != null) sentences.add("Care: " + (care.endsWith(".") ? care : care + "."));

        return new ListingText(name, String.join(" ", sentences));
    }

    private static void add(List<String> parts, String v) {
        String c = clean(v);
        if (c != null) parts.add(c);
    }

    private static boolean contains(List<String> parts, String word) {
        return parts.stream().anyMatch(p -> p.toLowerCase(Locale.ROOT).contains(word.toLowerCase(Locale.ROOT)));
    }

    private static String clean(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
