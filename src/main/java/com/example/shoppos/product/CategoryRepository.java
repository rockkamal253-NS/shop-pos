package com.example.shoppos.product;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends org.springframework.data.jpa.repository.JpaRepository<Category, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /** Category list with the number of products in each category. */
    @Query("""
            select c.id as id, c.name as name,
                   (select count(p) from Product p where p.category = c) as productCount
            from Category c
            order by c.name asc
            """)
    List<CategoryWithCount> findAllWithCounts();

    interface CategoryWithCount {
        Long getId();

        String getName();

        long getProductCount();
    }
}
