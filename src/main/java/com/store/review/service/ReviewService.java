package com.store.review.service;

import com.store.common.exception.ConflictException;
import com.store.common.exception.ForbiddenException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.order.repository.OrderRepository;
import com.store.review.dto.ReviewRequest;
import com.store.review.dto.ReviewResponse;
import com.store.review.entity.Review;
import com.store.review.repository.ReviewRepository;
import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public List<ReviewResponse> getReviewsByProduct(Long productId) {
        return reviewRepository.findByProductId(productId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<ReviewResponse> getMyReviews(String email) {
        Long userId = resolveUserId(email);
        return reviewRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ReviewResponse addReview(String email, ReviewRequest request) {
        Long userId = resolveUserId(email);

        if (!orderRepository.existsDeliveredOrderForProduct(
                userId, request.getProductId())) {
            throw new com.store.common.exception.BusinessException(
                    "You can review a product only after a delivered order containing it");
        }

        if (reviewRepository.existsByProductIdAndUserId(request.getProductId(), userId)) {
            throw new ConflictException("You have already reviewed this product");
        }

        Review review = Review.builder()
                .productId(request.getProductId())
                .userId(userId)
                .rating(request.getRating())
                .comment(request.getComment() == null ? null : request.getComment().trim())
                .build();

        Review saved = reviewRepository.save(review);
        return mapToResponse(saved);
    }

    @Transactional
    public void deleteReview(String email, Long reviewId) {
        Long userId = resolveUserId(email);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + reviewId));

        if (!review.getUserId().equals(userId)) {
            throw new ForbiddenException("You are not authorized to delete this review");
        }

        reviewRepository.delete(review);
    }

    private Long resolveUserId(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return user.getId();
    }

    private ReviewResponse mapToResponse(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProductId())
                .userId(review.getUserId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
