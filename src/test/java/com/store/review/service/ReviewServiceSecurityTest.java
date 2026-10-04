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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        user = User.builder()
                .firstName("Review")
                .lastName("Buyer")
                .email("buyer@example.com")
                .password("ignored")
                .isEnabled(true)
                .build();
        user.setId(42L);

        when(userRepository.findByEmail(eq(user.getEmail())))
                .thenReturn(Optional.of(user));
        service = new ReviewService(reviewRepository, userRepository, orderRepository);
    }

    @Test
    void addReview_rejectsProductWithoutDeliveredOrder() {
        ReviewRequest request = new ReviewRequest();
        request.setProductId(100L);
        request.setRating(5);
        request.setComment("Good");

        when(orderRepository.existsDeliveredOrderForProduct(42L, 100L))
                .thenReturn(false);

        assertThrows(BusinessException.class,
                () -> service.addReview(user.getEmail(), request));
        verifyNoInteractions(reviewRepository);
    }

    @Test
    void addReview_acceptsDeliveredPurchaseAndTrimsComment() {
        ReviewRequest request = new ReviewRequest();
        request.setProductId(100L);
        request.setRating(5);
        request.setComment("  Good  ");

        when(orderRepository.existsDeliveredOrderForProduct(42L, 100L))
                .thenReturn(true);
        when(reviewRepository.existsByProductIdAndUserId(100L, 42L))
                .thenReturn(false);
        when(reviewRepository.save(any(Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.addReview(user.getEmail(), request);

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());
        assertEquals("Good", captor.getValue().getComment());
    }

    @Test
    void addReview_rejectsDuplicateAfterVerifiedPurchaseCheck() {
        ReviewRequest request = new ReviewRequest();
        request.setProductId(100L);
        request.setRating(4);
        request.setComment("Again");

        when(orderRepository.existsDeliveredOrderForProduct(42L, 100L))
                .thenReturn(true);
        when(reviewRepository.existsByProductIdAndUserId(100L, 42L))
                .thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.addReview(user.getEmail(), request));
        verify(reviewRepository, never()).save(any());
    }
}