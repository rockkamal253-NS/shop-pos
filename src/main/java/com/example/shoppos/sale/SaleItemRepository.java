package com.example.shoppos.sale;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    boolean existsByProductId(Long productId);

    @Query("""
            select si.productId as productId, si.productName as productName,
                   sum(si.quantity) as quantitySold, sum(si.lineTotal) as revenue
            from SaleItem si join si.sale s
            where s.status = 'COMPLETED' and s.createdAt >= :since
            group by si.productId, si.productName
            order by quantitySold desc
            """)
    List<TopProduct> findTopProducts(@Param("since") LocalDateTime since, Pageable pageable);

    interface TopProduct {
        Long getProductId();

        String getProductName();

        long getQuantitySold();

        BigDecimal getRevenue();
    }
}
