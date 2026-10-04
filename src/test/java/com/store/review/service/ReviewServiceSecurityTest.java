package com.store.review.service;

import com.store.common.exception.BusinessException;
import com.store.common.exception.ConflictException;
import com.store.order.repository.OrderRepository;
import com.store.review.dto.ReviewRequest;
import com.store.review.entity.Review;
import com.store.review.repository.ReviewRepository;
import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceSecurityTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;

    private ReviewService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ReviewService(reviewRepository, userRepository, orderRepository);
        user = User.builder()
                .firstName("Review")
                .lastName("Buyer")
                .email("buyer@example.com")
                .password("password")
                .build();
        user.setId(42L);
        when(userRepository.findByEmail(eq(user.getEmail())))
                .thenReturn(Optional.of(user));
    }

    @Test
    void addReview_rejectsProductWithoutDeliveredOrder() {
        ReviewRequest request = new ReviewRequest();
        request.setProductId(100L);
        request.setRating(5);
        request.setComment("Good");

        when(orderRepository.existsDeliveredOrderForProduct(42L, 100L))
                .thenReturn(false);

        assertThrows(
                BusinessException.class,
                () -> service.addReview(user.getEmail(), request));

        verifyNoInteractions(reviewRepository);
    }

    @Test
    void addReview_acceptsDeliveredPurchaseButStillPreventsDuplicateReview() {
        ReviewRequest request = new ReviewRequest();
        request.setProductId(100L);
        request.setRating(5);
        request.setComment("  Good  ");

        when(orderRepository.existsDeliveredOrderForProduct(42L, 100L))
                .thenReturn(true)
                .thenReturn(true);
        when(reviewRepository.existsByProductIdAndUserId(100L, 42L))
                .thenReturn(false)
                .thenReturn(true);
        when(reviewRepository.save(any(Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> service.addReview(user.getEmail(), request));

        assertThrows(
                ConflictException.class,
                () -> service.addReview(user.getEmail(), request));

        verify(reviewRepository).save(argThat(
                review -> "Good".equals(review.getComment())));
    }
}
