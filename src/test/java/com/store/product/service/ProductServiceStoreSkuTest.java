package com.store.product.service;

import com.store.brand.repository.BrandRepository;
import com.store.category.entity.Category;
import com.store.category.repository.CategoryRepository;
import com.store.inventory.service.InventoryService;
import com.store.product.dto.ProductAdminResponse;
import com.store.product.dto.ProductRequest;
import com.store.product.dto.ProductUpdateRequest;
import com.store.common.exception.BusinessException;
import com.store.product.entity.Product;
import com.store.product.repository.ProductRepository;
import com.store.pricing.service.PricingService;
import com.store.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceStoreSkuTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private BrandRepository brandRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private InventoryService inventoryService;
    @Mock private PricingService pricingService;

    @InjectMocks private ProductService productService;

    @Test
    void createProductGeneratesStoreSkuWhenRequestOmitsIt() {
        Category category = new Category();
        category.setId(10L);
        category.setName("General");
        category.setSlug("general");
        category.setActive(true);

        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(productRepository.existsBySlug(anyString())).thenReturn(false);
        when(productRepository.existsBySku("ALI-00000042")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            if (product.getId() == null) product.setId(42L);
            return product;
        });

        ProductRequest request = new ProductRequest();
        request.setName("Generated SKU product");
        request.setCategoryId(10L);
        request.setPrice(new BigDecimal("29.99"));
        ProductAdminResponse response = productService.createProduct(request);

        assertEquals("ALI-00000042", response.getSku());
        assertEquals("SAR", response.getCurrency());
        verify(productRepository, times(2)).save(any(Product.class));
        verify(inventoryService).initializeForProduct(any(Product.class));
    }

    @Test
    void createProductRejectsNonSarCurrencyBeforeAnyWrite() {
        ProductRequest request = new ProductRequest();
        request.setCurrency("USD");

        assertThrows(BusinessException.class, () -> productService.createProduct(request));
        verifyNoInteractions(productRepository, categoryRepository, brandRepository,
                supplierRepository, inventoryService, pricingService);
    }

    @Test
    void updateProductRejectsNonSarCurrencyBeforeLoadingProduct() {
        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setCurrency("USD");

        assertThrows(BusinessException.class, () -> productService.updateProduct(42L, request));
        verifyNoInteractions(productRepository, categoryRepository, brandRepository,
                supplierRepository, inventoryService, pricingService);
    }
}
