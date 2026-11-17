package com.example.shoppos.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    boolean existsByProductId(Long productId);

    @EntityGraph(attributePaths = {"product", "user"})
    Page<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "user"})
    List<StockMovement> findTop50ByOrderByCreatedAtDesc();
}
