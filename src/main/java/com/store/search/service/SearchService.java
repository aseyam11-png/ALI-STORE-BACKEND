package com.store.search.service;

import com.store.brand.entity.Brand;
import com.store.brand.repository.BrandRepository;
import com.store.category.entity.Category;
import com.store.category.repository.CategoryRepository;
import com.store.common.response.PageResponse;
import com.store.product.dto.ProductResponse;
import com.store.product.service.ProductService;
import com.store.search.dto.SearchSuggestionResponse;
import com.store.search.dto.SearchSuggestionsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int MAX_LIMIT = 12;
    private static final int PRODUCT_CANDIDATE_LIMIT = 24;

    private final ProductService productService;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    public SearchSuggestionsResponse suggestions(String rawQuery, int requestedLimit) {
        String query = normalize(rawQuery);
        if (query.length() < 2) {
            return new SearchSuggestionsResponse(query, List.of());
        }
        if (query.length() > 120) {
            throw new com.store.common.exception.BusinessException("Search query must be 120 characters or less");
        }

        int limit = Math.min(MAX_LIMIT, Math.max(1, requestedLimit));
        List<SearchSuggestionResponse> ranked = new ArrayList<>();

        PageResponse<ProductResponse> productPage = productService.searchProducts(
                query, null, null, null, null, null, "relevance", 0, PRODUCT_CANDIDATE_LIMIT);

        for (ProductResponse product : productPage.getContent()) {
            ranked.add(SearchSuggestionResponse.builder()
                    .type("PRODUCT")
                    .id(String.valueOf(product.getId()))
                    .title(product.getName())
                    .subtitle(join(product.getBrandName(), product.getCategoryName()))
                    .image(product.getPrimaryImageUrl())
                    .href("/shop/products/" + product.getId())
                    .score(productScore(product, query))
                    .build());
        }

        if (productPage.getContent().isEmpty() && query.length() >= 3) {
            PageResponse<ProductResponse> fuzzyPage = productService.searchProducts(
                    "", null, null, null, null, null, "relevance", 0, 100);
            for (ProductResponse product : fuzzyPage.getContent()) {
                int score = productScore(product, query);
                if (score >= 50) {
                    ranked.add(SearchSuggestionResponse.builder()
                            .type("PRODUCT")
                            .id(String.valueOf(product.getId()))
                            .title(product.getName())
                            .subtitle(join(product.getBrandName(), product.getCategoryName()))
                            .image(product.getPrimaryImageUrl())
                            .href("/shop/products/" + product.getId())
                            .score(score)
                            .build());
                }
            }
        }

        for (Category category : categoryRepository.searchActive(query, PageRequest.of(0, 12)).getContent()) {
            int score = Math.max(
                    scoreText(category.getName(), query),
                    Math.max(scoreText(category.getSlug(), query), scoreText(category.getDescription(), query))
            );
            if (score > 0) {
                ranked.add(SearchSuggestionResponse.builder()
                        .type("CATEGORY")
                        .id(String.valueOf(category.getId()))
                        .title(category.getName())
                        .subtitle(category.getDescription())
                        .href("/shop/categories/" + category.getSlug())
                        .score(score + 8)
                        .build());
            }
        }

        for (Brand brand : brandRepository.searchByName(query, PageRequest.of(0, 12)).getContent()) {
            int score = scoreText(brand.getName(), query);
            if (score > 0) {
                ranked.add(SearchSuggestionResponse.builder()
                        .type("BRAND")
                        .id(String.valueOf(brand.getId()))
                        .title(brand.getName())
                        .subtitle("علامة تجارية")
                        .href("/shop/products?brand=" + brand.getId())
                        .score(score + 6)
                        .build());
            }
        }

        ranked.add(SearchSuggestionResponse.builder()
                .type("QUERY")
                .id(query)
                .title(query)
                .subtitle("عرض كل النتائج")
                .href("/shop/products?q=" + encodeQuery(query))
                .score(20)
                .build());

        return new SearchSuggestionsResponse(
                query,
                ranked.stream()
                        .sorted(Comparator.comparingInt(SearchSuggestionResponse::score).reversed()
                                .thenComparing(SearchSuggestionResponse::title, String.CASE_INSENSITIVE_ORDER))
                        .distinct()
                        .limit(limit)
                        .toList());
    }

    private int productScore(ProductResponse product, String query) {
        int score = 0;
        score = Math.max(score, scoreText(product.getName(), query));
        score = Math.max(score, scoreText(product.getSku(), query) + 10);
        score = Math.max(score, scoreText(product.getBrandName(), query) + 4);
        score = Math.max(score, scoreText(product.getCategoryName(), query));
        if (query.contains(" ")) {
            int tokenScore = 0;
            for (String token : query.split("\\s+")) {
                if (token.length() >= 2) {
                    tokenScore += Math.max(
                            Math.max(scoreText(product.getName(), token), scoreText(product.getBrandName(), token)),
                            scoreText(product.getCategoryName(), token)
                    );
                }
            }
            score = Math.max(score, Math.min(95, tokenScore / Math.max(1, query.split("\\s+").length) + 8));
        }
        return score;
    }
    private int scoreText(String value, String query) {
        String text = normalize(value);
        if (text.isBlank()) return 0;
        if (text.equals(query)) return 100;
        if (text.startsWith(query)) return 90;
        if (text.contains(query)) return 72;

        if (query.length() >= 3) {
            for (String token : text.split("[\\s\\-_/]+")) {
                if (editDistance(token, query) <= maxDistance(query)) return 55;
            }
        }
        return 0;
    }

    private int maxDistance(String query) {
        return query.length() <= 5 ? 1 : Math.max(1, query.length() / 4);
    }

    private int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int i = 0; i <= right.length(); i++) previous[i] = i;

        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1)
                );
            }
            previous = current;
        }
        return previous[right.length()];
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String join(String left, String right) {
        if (left == null || left.isBlank()) return right;
        if (right == null || right.isBlank()) return left;
        return left + " · " + right;
    }

    private String encodeQuery(String query) {
        return java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
    }
}
