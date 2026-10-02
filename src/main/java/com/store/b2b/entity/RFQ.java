package com.store.b2b.entity;

import com.store.user.entity.User;
import com.store.b2b.enums.RFQStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rfqs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RFQ {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RFQStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "rfq", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RFQItem> items = new ArrayList<>();

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}