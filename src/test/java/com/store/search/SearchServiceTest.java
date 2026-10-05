package com.store.search;

import com.store.brand.repository.BrandRepository;
import com.store.category.repository.CategoryRepository;
import com.store.common.response.PageResponse;
import com.store.product.dto.ProductResponse;
import com.store.product.service.ProductService;
import com.store.search.service.SearchService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchServiceTest {
    @Test
    void suggestionsRankSkuMatchesAboveGeneralTextMatches() {
        ProductService products = mock(ProductService.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        BrandRepository brands = mock(BrandRepository.class);

        ProductResponse generic = ProductResponse.builder().id(1L).name("Everyday Phone").sku("ABC-1").build();
        ProductResponse exactSku = ProductResponse.builder().id(2L).name("Phone Pro").sku("PHONE-77").build();

        when(products.searchProducts(eq("phone-77"), isNull(), isNull(), isNull(), isNull(), isNull(), eq("relevance"), eq(0), eq(24)))
                .thenReturn(PageResponse.of(List.of(generic, exactSku), 0, 24, 2, 1, true));
        when(categories.searchActive(eq("phone-77"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(brands.searchByName(eq("phone-77"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        SearchService service = new SearchService(products, categories, brands);
        var result = service.suggestions("phone-77", 5);

        assertEquals("2", result.suggestions().get(0).id());
    }

    @Test
    void suggestionsRejectOversizedQuery() {
        SearchService service = new SearchService(mock(ProductService.class), mock(CategoryRepository.class), mock(BrandRepository.class));
        assertThrows(com.store.common.exception.BusinessException.class,
                () -> service.suggestions("x".repeat(121), 8));
    }
}