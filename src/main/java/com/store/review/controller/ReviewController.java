package com.store.review.controller;

import com.store.common.response.ApiResponse;
import com.store.review.dto.ReviewRequest;
import com.store.review.dto.ReviewResponse;
import com.store.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getReviewsByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getReviewsByProduct(productId), "Reviews retrieved successfully"));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getReviewsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getReviewsByUser(userId), "User reviews retrieved successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> addReview(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(reviewService.addReview(userId, request), "Review added successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        reviewService.deleteReview(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Review deleted successfully"));
    }
}