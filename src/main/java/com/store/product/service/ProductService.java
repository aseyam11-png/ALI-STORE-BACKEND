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
import com.store.product.dto.ProductRequest;
import com.store.product.dto.ProductResponse;
import com.store.product.dto.ProductUpdateRequest;
import com.store.product.entity.Product;
import com.store.product.entity.ProductImage;
import com.store.product.enums.ProductStatus;
import com.store.product.repository.ProductRepository;
import com.store.product.repository.ProductSpecifications;
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
    private static final List<ProductStatus> CUSTOMER_VISIBLE_DETAIL_STATUSES = List.of(ProductStatus.ACTIVE, ProductStatus.ARCHIVED);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final InventoryService inventoryService;

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new ConflictException("Product with this SKU already exists");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()));

        Brand brand = null;
        if (request.getBrandId() != null) {
            brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + request.getBrandId()));
        }

        String slug = (request.getSlug() != null && !request.getSlug().isBlank())
                ? SlugUtils.slugify(request.getSlug())
                : SlugUtils.uniqueSlugify(request.getName(), productRepository::existsBySlug);
        if (productRepository.existsBySlug(slug)) {
            throw new ConflictException("Product slug already exists: " + slug);
        }

        String currency = (request.getCurrency() != null && !request.getCurrency().isBlank())
                ? request.getCurrency().toUpperCase()
                : DEFAULT_CURRENCY;

        Product product = Product.builder()
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .price(request.getPrice())
                .currency(currency)
                .sku(request.getSku())
                .category(category)
                .brand(brand)
                .build();

        if (request.getImageUrl() != null && !request.getImageUrl().isBlank()) {
            product.getImages().add(ProductImage.builder()
                    .product(product)
                    .url(request.getImageUrl())
                    .sortOrder(0)
                    .isPrimary(true)
                    .build());
        }

        Product saved = productRepository.save(product);
        inventoryService.initializeForProduct(saved);

        return mapToResponse(saved);
    }

    /**
     * Server-side catalog search boundary. Deliberately built on a JPA
     * Specification against the repository rather than loading everything
     * and filtering in memory. Only ACTIVE products are ever browsable here -
     * DRAFT and ARCHIVED products never appear in listings (see getProductById/
     * getProductBySlug for why ARCHIVED is still individually resolvable).
     */
    public PageResponse<ProductResponse> searchProducts(String q, Long categoryId, Long brandId,
                                                          BigDecimal minPrice, BigDecimal maxPrice,
                                                          PublicAvailability availability, String sort,
                                                          int page, int pageSize) {
        if (page < 0) {
            throw new BusinessException("page must be >= 0");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }

        Specification<Product> spec = Specification.where(ProductSpecifications.hasStatus(ProductStatus.ACTIVE));
        if (q != null && !q.isBlank()) {
            spec = spec.and(ProductSpecifications.nameOrSkuContains(q.trim()));
        }
        if (categoryId != null) {
            spec = spec.and(ProductSpecifications.inCategory(categoryId));
        }
        if (brandId != null) {
            spec = spec.and(ProductSpecifications.hasBrand(brandId));
        }
        if (minPrice != null) {
            spec = spec.and(ProductSpecifications.priceGreaterThanOrEqual(minPrice));
        }
        if (maxPrice != null) {
            spec = spec.and(ProductSpecifications.priceLessThanOrEqual(maxPrice));
        }
        if (availability == PublicAvailability.IN_STOCK || availability == PublicAvailability.OUT_OF_STOCK) {
            spec = spec.and(ProductSpecifications.hasAvailability(availability));
        }

        Pageable pageable = PageRequest.of(page, pageSize, resolveSort(sort));
        Page<ProductResponse> results = productRepository.findAll(spec, pageable).map(this::mapToResponse);
        return toPageResponse(results);
    }

    /**
     * relevance/price_asc/price_desc are the only sorts backed by real data today.
     * rating_desc/reviews_desc are intentionally rejected rather than silently
     * ignored - there is no review-to-product rating aggregation in this codebase
     * yet, so pretending to support them would be fabricated behavior.
     */
    private Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank() || sort.equals("relevance")) {
            return Sort.by(Sort.Direction.ASC, "id");
        }
        return switch (sort) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
            case "rating_desc", "reviews_desc" -> throw new BusinessException(
                    "Sort '" + sort + "' is not supported: no review/rating aggregation exists yet");
            default -> throw new BusinessException("Unsupported sort: " + sort);
        };
    }

    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .filter(p -> CUSTOMER_VISIBLE_DETAIL_STATUSES.contains(p.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return mapToResponse(product);
    }

    public ProductResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlugAndStatusIn(slug, CUSTOMER_VISIBLE_DETAIL_STATUSES)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with slug: " + slug));
        return mapToResponse(product);
    }

    // ---- Admin ----

    public PageResponse<ProductResponse> getAllForAdmin(Pageable pageable) {
        return toPageResponse(productRepository.findAll(pageable).map(this::mapToResponse));
    }

    public ProductResponse getForAdmin(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return mapToResponse(product);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductUpdateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (request.getName() != null && !request.getName().isBlank()) {
            product.setName(request.getName());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            product.setCurrency(request.getCurrency().toUpperCase());
        }
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()));
            product.setCategory(category);
        }
        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + request.getBrandId()));
            product.setBrand(brand);
        }
        if (request.getStatus() != null) {
            product.setStatus(request.getStatus());
        }

        return mapToResponse(productRepository.save(product));
    }

    /**
     * Soft-delete: a product is archived, never hard-deleted, so that existing
     * cart items / order history referencing it stay valid.
     */
    @Transactional
    public void archiveProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        product.setStatus(ProductStatus.ARCHIVED);
        productRepository.save(product);
    }

    private PageResponse<ProductResponse> toPageResponse(Page<ProductResponse> page) {
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
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .images(imageUrls)
                .primaryImageUrl(primaryImageUrl)
                .build();
    }
}
