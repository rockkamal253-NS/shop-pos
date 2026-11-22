package com.example.shoppos.sale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @EntityGraph(attributePaths = {"cashier", "items", "voidedBy"})
    Optional<Sale> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"cashier"})
    @Query("""
            select s from Sale s
            where (:from is null or s.createdAt >= :from)
              and (:to is null or s.createdAt < :to)
              and (:cashierId is null or s.cashier.id = :cashierId)
            """)
    Page<Sale> search(@Param("from") LocalDateTime from,
                      @Param("to") LocalDateTime to,
                      @Param("cashierId") Long cashierId,
                      Pageable pageable);

    @Query("select coalesce(sum(s.total), 0) from Sale s " +
            "where s.status = 'COMPLETED' and s.createdAt >= :from and s.createdAt < :to")
    BigDecimal revenueBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select count(s) from Sale s " +
            "where s.status = 'COMPLETED' and s.createdAt >= :from and s.createdAt < :to")
    long countCompletedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
