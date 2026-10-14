package com.example.ecommerce.service.studio;

/** Produces listing text from owner-supplied facts. Implementations must not add facts. */
public interface ListingWriter {
    record ListingText(String name, String description) {}

    /** Short identifier shown to the owner, such as "template" or "anthropic". */
    String id();

    /** Whether this writer sends the facts to an external service. */
    boolean external();

    ListingText write(String categoryName, StudioRequest facts) throws Exception;
}
