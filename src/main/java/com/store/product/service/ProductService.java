package com.store.product.service;

import com.store.brand.entity.Brand;
import com.store.brand.repository.BrandRepository;
import com.store.category.entity.Category;
import com.store.category.repository.CategoryRepository;
import com.store.common.exception.BusinessException;
import com.store.common.exception.ConflictException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.common.response.PageResponse;
import com.store.common.util.SlugUtils;
import com.store.inventory.enums.PublicAvailability;
import com.store.inventory.service.InventoryService;
import com.store.product.dto.ProductAdminResponse;
import com.store.product.dto.ProductRequest;
import com.store.product.dto.ProductResponse;
import com.store.product.dto.ProductUpdateRequest;
import com.store.product.entity.Product;
import com.store.product.entity.ProductImage;
import com.store.product.enums.ProductStatus;
import com.store.product.repository.ProductRepository;
import com.store.product.repository.ProductSpecifications;
import com.store.supplier.entity.Supplier;
import com.store.supplier.entity.SupplierStatus;
import com.store.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {
    private static final String DEFAULT_CURRENCY = "USD";
    private static final int MAX_PAGE_SIZE = 100;
    private static final List<ProductStatus> CUSTOMER_VISIBLE_DETAIL_STATUSES = List.of(ProductStatus.ACTIVE);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryService inventoryService;

    @Transactional
    public ProductAdminResponse createProduct(ProductRequest request) {
        String sku = request.getSku().trim();
        if (productRepository.existsBySku(sku)) {
            throw new ConflictException("Product with this SKU already exists");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()));

        Brand brand = request.getBrandId() == null ? null : brandRepository.findById(request.getBrandId())
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + request.getBrandId()));

        Supplier supplier = null;
        if (request.getSupplierId() != null) {
            supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with id: " + request.getSupplierId()));
            ensureSupplierCanFulfill(supplier);
        }

        if (request.getSupplierCost() != null) {
            validateSupplierCost(request.getPrice(), request.getSupplierCost());
        }

        if (request.getSupplierId() != null && (request.getSupplierSku() == null || request.getSupplierSku().isBlank())) {
            throw new BusinessException("Supplier SKU is required when a supplier is mapped");
        }

        String slug = request.getSlug() != null && !request.getSlug().isBlank()
                ? SlugUtils.slugify(request.getSlug())
                : SlugUtils.uniqueSlugify(request.getName(), productRepository::existsBySlug);

        if (productRepository.existsBySlug(slug)) {
            throw new ConflictException("Product slug already exists: " + slug);
        }

        String currency = request.getCurrency() != null && !request.getCurrency().isBlank()
                ? request.getCurrency().trim().toUpperCase()
                : DEFAULT_CURRENCY;

        Product product = Product.builder()
                .name(request.getName().trim())
                .slug(slug)
                .description(request.getDescription())
                .price(request.getPrice())
                .currency(currency)
                .sku(sku)
                .category(category)
                .brand(brand)
                .supplier(supplier)
                .supplierSku(request.getSupplierSku() == null ? null : request.getSupplierSku().trim())
                .supplierCost(request.getSupplierCost())
                .status(ProductStatus.DRAFT)
                .build();

        if (request.getImageUrl() != null && !request.getImageUrl().isBlank()) {
            product.getImages().add(ProductImage.builder()
                    .product(product)
                    .url(request.getImageUrl().trim())
                    .sortOrder(0)
                    .isPrimary(true)
                    .build());
        }

        Product saved = productRepository.save(product);
        inventoryService.initializeForProduct(saved);
        return mapToAdminResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchProducts(String q, Long categoryId, Long brandId,
                                                         BigDecimal minPrice, BigDecimal maxPrice,
                                                         PublicAvailability availability, String sort,
                                                         int page, int pageSize) {
        if (page < 0) throw new BusinessException("page must be >= 0");
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessException("minPrice cannot be greater than maxPrice");
        }

        Specification<Product> spec = Specification.where(ProductSpecifications.hasStatus(ProductStatus.ACTIVE))
                .and(ProductSpecifications.hasActiveCategory())
                .and(ProductSpecifications.hasActiveSupplier());

        if (q != null && !q.isBlank()) spec = spec.and(ProductSpecifications.nameOrSkuContains(q.trim()));
        if (categoryId != null) spec = spec.and(ProductSpecifications.inCategory(categoryId));
        if (brandId != null) spec = spec.and(ProductSpecifications.hasBrand(brandId));
        if (minPrice != null) spec = spec.and(ProductSpecifications.priceGreaterThanOrEqual(minPrice));
        if (maxPrice != null) spec = spec.and(ProductSpecifications.priceLessThanOrEqual(maxPrice));
        if (availability == PublicAvailability.IN_STOCK || availability == PublicAvailability.OUT_OF_STOCK) {
            spec = spec.and(ProductSpecifications.hasAvailability(availability));
        }

        Pageable pageable = PageRequest.of(page, pageSize, resolveSort(sort));
        Page<ProductResponse> results = productRepository.findAll(spec, pageable).map(this::mapToResponse);
        return toPageResponse(results);
    }

    private Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank() || sort.equals("relevance")) {
            return Sort.by(Sort.Direction.ASC, "id");
        }
        return switch (sort) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
            case "rating_desc", "reviews_desc" ->
                    throw new BusinessException("Sort '" + sort + "' is not supported: no review/rating aggregation exists yet");
            default -> throw new BusinessException("Unsupported sort: " + sort);
        };
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .filter(p -> CUSTOMER_VISIBLE_DETAIL_STATUSES.contains(p.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        if (!Boolean.TRUE.equals(product.getCategory().getActive())
                || product.getSupplier() == null
                || product.getSupplier().getStatus() != SupplierStatus.ACTIVE
                || !Boolean.TRUE.equals(product.getSupplier().getFulfillmentEnabled())) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }
        return mapToResponse(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlugAndStatusIn(slug, CUSTOMER_VISIBLE_DETAIL_STATUSES)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with slug: " + slug));
        if (!Boolean.TRUE.equals(product.getCategory().getActive())
                || product.getSupplier() == null
                || product.getSupplier().getStatus() != SupplierStatus.ACTIVE
                || !Boolean.TRUE.equals(product.getSupplier().getFulfillmentEnabled())) {
            throw new ResourceNotFoundException("Product not found with slug: " + slug);
        }
        return mapToResponse(product);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductAdminResponse> getAllForAdmin(Pageable pageable) {
        Pageable safe = PageRequest.of(
                Math.max(0, pageable.getPageNumber()),
                Math.min(Math.max(1, pageable.getPageSize()), MAX_PAGE_SIZE),
                pageable.getSort());
        return toAdminPageResponse(productRepository.findAll(safe).map(this::mapToAdminResponse));
    }

    @Transactional(readOnly = true)
    public ProductAdminResponse getForAdmin(Long id) {
        return mapToAdminResponse(productRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Product not found with id: " + id)));
    }

    @Transactional
    public ProductAdminResponse updateProduct(Long id, ProductUpdateRequest request) {
        Product product = productRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Product not found with id: " + id));

        if (request.getName() != null && !request.getName().isBlank()) product.setName(request.getName().trim());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getPrice() != null) product.setPrice(request.getPrice());
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) product.setCurrency(request.getCurrency().trim().toUpperCase());
        if (request.getCategoryId() != null) {
            product.setCategory(categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId())));
        }
        if (request.getBrandId() != null) {
            product.setBrand(brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + request.getBrandId())));
        }
        if (request.getSupplierId() != null) {
            Supplier supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with id: " + request.getSupplierId()));
            ensureSupplierCanFulfill(supplier);
            product.setSupplier(supplier);
        }
        if (request.getSupplierSku() != null && !request.getSupplierSku().isBlank()) {
            product.setSupplierSku(request.getSupplierSku().trim());
        }
        if (request.getSupplierCost() != null) product.setSupplierCost(request.getSupplierCost());
        if (request.getStatus() != null) {
            if (request.getStatus() == ProductStatus.ACTIVE) validatePublishable(product);
            product.setStatus(request.getStatus());
        }
        if (product.getStatus() == ProductStatus.ACTIVE) validatePublishable(product);

        return mapToAdminResponse(productRepository.save(product));
    }

    @Transactional
    public void archiveProduct(Long id) {
        Product product = productRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Product not found with id: " + id));
        product.setStatus(ProductStatus.ARCHIVED);
        productRepository.save(product);
    }

    private void ensureSupplierCanFulfill(Supplier supplier) {
        if (supplier == null
                || supplier.getStatus() != SupplierStatus.ACTIVE
                || !Boolean.TRUE.equals(supplier.getFulfillmentEnabled())) {
            throw new BusinessException("Supplier is not active for fulfillment");
        }
    }

    private void validateSupplierCost(BigDecimal sellingPrice, BigDecimal supplierCost) {
        if (supplierCost == null || supplierCost.signum() <= 0) {
            throw new BusinessException("Supplier cost must be greater than zero");
        }
        if (sellingPrice.compareTo(supplierCost) < 0) {
            throw new BusinessException("Selling price cannot be lower than supplier cost");
        }
    }

    private void validatePublishable(Product product) {
        if (product.getCategory() == null || !Boolean.TRUE.equals(product.getCategory().getActive())) {
            throw new BusinessException("Product cannot be active while its category is inactive");
        }
        if (product.getSupplier() == null || product.getSupplierSku() == null || product.getSupplierSku().isBlank()
                || product.getSupplierCost() == null || product.getSupplierCost().signum() <= 0) {
            throw new BusinessException("Product cannot be active without a mapped supplier and supplier cost");
        }
        ensureSupplierCanFulfill(product.getSupplier());
        validateSupplierCost(product.getPrice(), product.getSupplierCost());
    }

    private PageResponse<ProductResponse> toPageResponse(Page<ProductResponse> page) {
        return PageResponse.of(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    private PageResponse<ProductAdminResponse> toAdminPageResponse(Page<ProductAdminResponse> page) {
        return PageResponse.of(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    private ProductResponse mapToResponse(Product product) {
        List<String> imageUrls = product.getImages().stream()
                .sorted(Comparator.comparing(ProductImage::getSortOrder))
                .map(ProductImage::getUrl)
                .collect(Collectors.toList());
        String primaryImageUrl = product.getImages().stream()
                .filter(ProductImage::getIsPrimary)
                .map(ProductImage::getUrl)
                .findFirst()
                .orElse(imageUrls.isEmpty() ? null : imageUrls.get(0));

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .sku(product.getSku())
                .status(product.getStatus())
                .availability(inventoryService.getPublicAvailability(product.getId()))
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .images(imageUrls)
                .primaryImageUrl(primaryImageUrl)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    private ProductAdminResponse mapToAdminResponse(Product product) {
        List<String> imageUrls = product.getImages().stream()
                .sorted(Comparator.comparing(ProductImage::getSortOrder))
                .map(ProductImage::getUrl)
                .collect(Collectors.toList());
        String primaryImageUrl = product.getImages().stream()
                .filter(ProductImage::getIsPrimary)
                .map(ProductImage::getUrl)
                .findFirst()
                .orElse(imageUrls.isEmpty() ? null : imageUrls.get(0));

        return ProductAdminResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .sku(product.getSku())
                .status(product.getStatus())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .images(imageUrls)
                .primaryImageUrl(primaryImageUrl)
                .supplierId(product.getSupplier() != null ? product.getSupplier().getId() : null)
                .supplierCode(product.getSupplier() != null ? product.getSupplier().getExternalCode() : null)
                .supplierName(product.getSupplier() != null ? product.getSupplier().getName() : null)
                .supplierSku(product.getSupplierSku())
                .supplierCost(product.getSupplierCost())
                .build();
    }
}
