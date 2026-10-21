package com.example.ecommerce;

import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.ProductStatus;
import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.CatalogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Colours of one design, bulk CSV import/export, and the Google/Meta feed. */
class CatalogToolsIntegrationTest extends IntegrationTestSupport {
    private static final AtomicInteger N = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CatalogService catalog;

    private String adminToken() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "admin", "password", "test-admin-password")))).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private String csvHeader() {
        return "sku,name,category,colour,price,stock,description,image_url\n";
    }

    private String category() {
        return categories.findAll().stream().findFirst().orElseGet(() -> categories.save(new com.example.ecommerce.model.Category("Test", "Test category"))).getName();
    }

    @Test
    void addingAColourCreatesALinkedDraftWithItsOwnStockAndSku() throws Exception {
        Product red = product("5000.00", 4);
        red.setColour("Red");
        red.setSku("KAN-RED-" + N.incrementAndGet());
        products.save(red);

        String t = adminToken();
        MvcResult r = mvc.perform(post("/api/admin/products/" + red.getId() + "/colour").param("colour", "Green")
                .header("Authorization", "Bearer " + t)).andExpect(status().isOk()).andReturn();
        JsonNode green = json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("Green", green.get("colour").asText());
        assertEquals("DRAFT", green.get("status").asText());
        assertEquals(0, green.get("stockQuantity").asInt(), "a new colour starts with no stock");
        assertTrue(green.get("sku").asText().endsWith("-GREEN"), green.get("sku").asText());
        assertEquals(green.get("styleGroup").asText(), products.findById(red.getId()).orElseThrow().getStyleGroup());

        mvc.perform(post("/api/admin/products/" + red.getId() + "/colour").param("colour", "green")
                .header("Authorization", "Bearer " + t)).andExpect(status().isBadRequest());

        // Drafts are not shown to shoppers until published.
        MvcResult pub = mvc.perform(get("/api/products/" + red.getId() + "/colours")).andExpect(status().isOk()).andReturn();
        assertEquals(1, json.readTree(pub.getResponse().getContentAsString()).size());

        Product g = products.findById(green.get("id").asLong()).orElseThrow();
        g.setStatus(ProductStatus.PUBLISHED);
        g.setStockQuantity(2);
        g.setImageUrl("/x.jpg");
        products.save(g);
        JsonNode both = json.readTree(mvc.perform(get("/api/products/" + red.getId() + "/colours")).andReturn().getResponse().getContentAsString());
        assertEquals(2, both.size());
        assertTrue(both.get(1).get("inStock").asBoolean());
    }

    @Test
    void aSkuCanBelongToOnlyOneProduct() {
        Product a = product("1000.00", 1);
        a.setSku("DUP-" + N.incrementAndGet());
        products.save(a);
        String csv = csvHeader() + a.getSku() + ",Other,\"" + category() + "\",Blue,900,3,A description that is long enough to publish,/y.jpg\n";
        CatalogService.ImportReport report = catalog.importCsv(csv, true);
        // Same SKU means "update that product", never a duplicate.
        assertEquals(1, report.updated());
        assertEquals(0, report.created());
    }

    @Test
    void importCreatesDraftsUpdatesBySkuAndReportsBadRowsWithoutBlockingTheRest() {
        String sku = "IMP-" + N.incrementAndGet();
        String cat = category();
        String csv = csvHeader()
                + sku + ",Imported Saree,\"" + cat + "\",Maroon,2499,7,\"Soft silk, with a rich border, 30+ chars.\",https://example.com/a.jpg\n"
                + ",No Price,\"" + cat + "\",Blue,,3,Something long enough to be a description,https://example.com/b.jpg\n"
                + ",Wrong Category,Nonexistent,Blue,999,3,Something long enough to be a description,https://example.com/c.jpg\n"
                + ",Bad Stock,\"" + cat + "\",Blue,999,many,Something long enough to be a description,https://example.com/d.jpg\n";

        CatalogService.ImportReport dry = catalog.importCsv(csv, true);
        assertTrue(dry.dryRun());
        assertEquals(1, dry.created());
        assertEquals(3, dry.failed());
        assertTrue(products.findBySkuIgnoreCaseAndDeletedFalse(sku).isEmpty(), "a dry run saves nothing");
        assertTrue(dry.rows().stream().anyMatch(r -> r.message().contains("Unknown category")));
        assertTrue(dry.rows().stream().anyMatch(r -> r.message().contains("not a whole number")));

        CatalogService.ImportReport real = catalog.importCsv(csv, false);
        assertEquals(1, real.created());
        Product p = products.findBySkuIgnoreCaseAndDeletedFalse(sku).orElseThrow();
        assertEquals(ProductStatus.DRAFT, p.getStatus(), "imports never publish");
        assertEquals("Soft silk, with a rich border, 30+ chars.", p.getDescription());
        assertEquals(7, p.getStockQuantity());

        String update = csvHeader() + sku + ",Imported Saree,\"" + cat + "\",Maroon,2599,9,,\n";
        CatalogService.ImportReport again = catalog.importCsv(update, false);
        assertEquals(1, again.updated());
        Product q = products.findBySkuIgnoreCaseAndDeletedFalse(sku).orElseThrow();
        assertEquals(0, new java.math.BigDecimal("2599").compareTo(q.getPrice()));
        assertEquals("https://example.com/a.jpg", q.getImageUrl(), "empty cells keep what is already there");
        assertTrue(q.getDescription().startsWith("Soft silk"));
    }

    @Test
    void importRejectsUnsafeInputEarly() {
        assertThrows(BusinessException.class, () -> catalog.importCsv("", true));
        assertThrows(BusinessException.class, () -> catalog.importCsv("name,price\nA,1\n", true), "category column is required");
        assertThrows(BusinessException.class, () -> catalog.importCsv(csvHeader() + "x,\"unclosed,Cat,Red,1,1,d,/i.jpg\n", true));
        CatalogService.ImportReport r = catalog.importCsv(csvHeader() + ",Js Image,\"" + category() + "\",Red,100,1,Long enough description here ok,javascript:alert(1)\n", true);
        assertEquals(1, r.failed(), "non-http image links are refused");
    }

    @Test
    void exportNeutralisesFormulasAndKeepsCommasAndQuotesIntact() {
        Product p = product("1500.00", 2);
        p.setName("=cmd|' /C calc'!A0");
        p.setDescription("Has, a comma and \"quotes\"");
        products.save(p);
        String csv = catalog.exportCsv();
        assertTrue(csv.contains("\"'=cmd|' /C calc'!A0\""));
        assertTrue(csv.contains("\"Has, a comma and \"\"quotes\"\"\""));
        assertEquals(p.getName().length() > 0, true);
        // The export can be read back by the importer's parser.
        var table = CatalogService.parse(csv);
        assertEquals(CatalogService.COLUMNS.size(), table.get(0).size());
        assertTrue(table.stream().allMatch(row -> row.size() == CatalogService.COLUMNS.size()));
    }

    @Test
    void googleFeedListsOnlyPublishedStockWithEscapedTextAndAbsoluteLinks() throws Exception {
        Product live = product("2000.00", 3);
        live.setName("Silk & Zari <Special>");
        live.setColour("Teal");
        live.setImageUrl("/images/sarees/x.jpg");
        products.save(live);
        Product draft = product("2000.00", 3);
        draft.setName("Hidden Draft Saree");
        draft.setStatus(ProductStatus.DRAFT);
        products.save(draft);

        String xml = mvc.perform(get("/api/store/feed/google.xml")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(xml.contains("Silk &amp; Zari &lt;Special&gt;"));
        assertTrue(xml.contains("<g:price>2000.00 INR</g:price>"));
        assertTrue(xml.contains("<g:color>Teal</g:color>"));
        assertTrue(xml.contains("/products/" + live.getId() + "</link>"));
        assertFalse(xml.contains("Hidden Draft Saree"));
        assertTrue(mvc.perform(get("/api/store/sitemap.xml")).andReturn().getResponse().getContentAsString().contains("/products/" + live.getId()));
    }

    @Test
    void catalogToolsAreAdminOnly() throws Exception {
        mvc.perform(get("/api/admin/catalog/export")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/catalog/import").contentType("text/csv").content("a")).andExpect(status().isUnauthorized());
        String t = adminToken();
        mvc.perform(get("/api/admin/catalog/template").header("Authorization", "Bearer " + t)).andExpect(status().isOk());
        MvcResult r = mvc.perform(post("/api/admin/catalog/import").contentType("text/csv")
                .content(csvHeader() + ",HTTP Import,\"" + category() + "\",Red,100,1,Long enough description here ok,https://example.com/z.jpg\n")
                .header("Authorization", "Bearer " + t)).andExpect(status().isOk()).andReturn();
        assertTrue(json.readTree(r.getResponse().getContentAsString()).get("dryRun").asBoolean(), "import defaults to a dry run");
    }
}
