package com.store.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.ai.client.AiProviderClient;
import com.store.ai.config.AiProperties;
import com.store.ai.service.AskAliService;
import com.store.common.response.PageResponse;
import com.store.product.dto.ProductResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AskAliServiceTest {
    @Test
    void modelCanOnlySelectProductsProvidedByCatalogContext() throws Exception {
        AiProviderClient provider = mock(AiProviderClient.class);
        com.store.product.service.ProductService products = mock(com.store.product.service.ProductService.class);
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setMaxContextProducts(8);

        ProductResponse product = ProductResponse.builder()
                .id(41L).name("Test Phone").slug("test-phone")
                .description("A customer-safe product").price(new BigDecimal("99.00"))
                .currency("USD").sku("PHONE-41")
                .availability(com.store.inventory.enums.PublicAvailability.IN_STOCK)
                .availableQuantity(4).categoryId(7L).categoryName("Phones")
                .categorySlug("phones").brandId(3L).brandName("ALI")
                .primaryImageUrl("https://example.com/phone.jpg").build();

        PageResponse<ProductResponse> page = PageResponse.of(List.of(product), 0, 8, 1, 1, true);
        when(products.searchProducts(anyString(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("relevance"), org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(8)))
                .thenReturn(page);
        String providerJson = new ObjectMapper().writeValueAsString(Map.of(
                "answer", "تم العثور على منتج مناسب",
                "suggestedQuery", "هاتف",
                "productIds", List.of(41L, 999999L)));
        when(provider.chat(anyString(), anyString())).thenReturn(providerJson);

        AskAliService service = new AskAliService(provider, properties, products, new ObjectMapper());
        var response = service.ask("هاتف");

        assertEquals("تم العثور على منتج مناسب", response.answer());
        assertEquals(1, response.products().size());
        assertEquals(41L, response.products().get(0).getId());

        var promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(provider).chat(anyString(), promptCaptor.capture());
        String groundedPrompt = promptCaptor.getValue();
        assertFalse(groundedPrompt.contains("supplierCost"));
        assertFalse(groundedPrompt.contains("supplierSku"));
        assertFalse(groundedPrompt.contains("supplierId"));
    }
}