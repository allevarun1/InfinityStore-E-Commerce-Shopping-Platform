package com.example.ecommerce.service.studio;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/** Test-only bridge to package-private studio internals. */
public final class StudioServiceAccess {
    private StudioServiceAccess() {}

    public static List<String> claims(ListingWriter.ListingText text, StudioRequest facts) {
        return StudioService.unsupportedClaims(text, facts);
    }

    public static Map<String, Object> body(AnthropicListingWriter w, String category, StudioRequest facts) throws Exception {
        return w.requestBody(category, facts);
    }

    public static ListingWriter.ListingText parse(AnthropicListingWriter w, JsonNode reply) throws Exception {
        return w.parse(reply);
    }
}
