package com.example.shoppos.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsByBarcodeIgnoreCase(String barcode);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

    boolean existsByBarcodeIgnoreCaseAndIdNot(String barcode, Long id);

    boolean existsByCategoryId(Long categoryId);

    Optional<Product> findByBarcode(String barcode);

    Optional<Product> findBySkuIgnoreCase(String sku);

    /** Search by name, SKU or barcode fragment (case-insensitive). */
    @EntityGraph(attributePaths = "category")
    @Query("""
            select p from Product p
            where (:q is null
                   or lower(p.name) like lower(concat('%', :q, '%'))
                   or lower(p.sku) like lower(concat('%', :q, '%'))
                   or lower(p.barcode) like lower(concat('%', :q, '%')))
            """)
    Page<Product> search(@Param("q") String q, Pageable pageable);

    /** Active products at or below their low-stock threshold, most critical first. */
    @EntityGraph(attributePaths = "category")
    @Query("select p from Product p where p.active = true and p.stockQuantity <= p.lowStockThreshold order by p.stockQuantity asc")
    List<Product> findLowStock(Pageable pageable);
}
