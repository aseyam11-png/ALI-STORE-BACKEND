package com.store.review.controller;

import com.store.common.response.ApiResponse;
import com.store.review.dto.ReviewRequest;
import com.store.review.dto.ReviewResponse;
import com.store.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getMyReviews(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getMyReviews(userDetails.getUsername()), "Your reviews retrieved successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> addReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(reviewService.addReview(userDetails.getUsername(), request), "Review added successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        reviewService.deleteReview(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success(null, "Review deleted successfully"));
    }
}
