package com.example.shoppos.dashboard;

import com.example.shoppos.product.Product;
import com.example.shoppos.product.ProductRepository;
import com.example.shoppos.sale.SaleItemRepository;
import com.example.shoppos.sale.SaleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Service
public class DashboardService {

    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final ProductRepository products;

    public DashboardService(SaleRepository sales, SaleItemRepository saleItems, ProductRepository products) {
        this.sales = sales;
        this.saleItems = saleItems;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public DashboardStats stats() {
        LocalDate today = LocalDate.now();
        var todayStart = today.atStartOfDay();
        var tomorrow = today.plusDays(1).atStartOfDay();
        var weekStart = today.with(DayOfWeek.MONDAY).atStartOfDay(); // ISO week starts Monday

        BigDecimal todayRevenue = sales.revenueBetween(todayStart, tomorrow);
        long todayTransactions = sales.countCompletedBetween(todayStart, tomorrow);
        List<SaleItemRepository.TopProduct> topProducts =
                saleItems.findTopProducts(weekStart, PageRequest.of(0, 5));
        List<Product> lowStock = products.findLowStock(PageRequest.of(0, 10));

        return new DashboardStats(todayRevenue, todayTransactions, topProducts, lowStock);
    }

    public record DashboardStats(BigDecimal todayRevenue,
                                 long todayTransactions,
                                 List<SaleItemRepository.TopProduct> topProducts,
                                 List<Product> lowStock) {
    }
}
