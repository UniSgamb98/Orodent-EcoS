package com.orodent.ecos.statistiche.features.sales.products.service;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.statistiche.features.sales.products.model.*;
import com.orodent.ecos.statistiche.features.sales.products.repository.ProductAnalysisRepository;
import com.orodent.ecos.statistiche.features.sales.projection.model.SalesDataCoverage;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProductAnalysisServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneId.of("Europe/Rome"));
    private final ConnectionProvider connections = () -> (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> null);

    @Test void loadsLatestYearAndUsesArchiveCoverageWithoutRequeryingForGroupingOrDetail() {
        FakeRepository repository = new FakeRepository();
        ProductAnalysisService service = new ProductAnalysisService(connections,Runnable::run,c -> repository,clock);
        var page = service.load(null,ProductGrouping.FAMILY).join();
        assertEquals(2026,page.snapshot().year());
        assertEquals(LocalDate.of(2026,10,7),page.snapshot().reference());
        assertEquals(LocalDate.of(2020,1,1),repository.from);
        assertEquals(LocalDate.of(2026,12,31),repository.through);
        var regrouped = service.regroup(page.snapshot(),ProductGrouping.FAMILY_COLOR).join();
        assertEquals("A2",regrouped.groups().getFirst().key().color());
        assertEquals(page.classifiedTotals(),regrouped.classifiedTotals());
        assertEquals(1,service.detail(page.snapshot(),page.groups().getFirst()).join().customers().size());
        var detail = service.detail(page.snapshot(), page.groups().getFirst()).join();
        assertNotNull(detail.monthlyProjections().revenue());
        assertNotNull(detail.monthlyProjections().quantity());
        assertEquals(1,repository.reads);
    }
    @Test void historicalYearsUseFullYearsAndTodayCapsFutureDatedData() {
        FakeRepository repository = new FakeRepository();
        ProductAnalysisService service = new ProductAnalysisService(connections,Runnable::run,c -> repository,clock);
        assertEquals(LocalDate.of(2025,12,31),service.load(2025,ProductGrouping.FAMILY).join().snapshot().reference());
        repository.coverage = new SalesDataCoverage(LocalDate.of(2020,1,1),LocalDate.of(2026,12,31));
        assertEquals(LocalDate.of(2026,10,13),service.load(2026,ProductGrouping.FAMILY).join().snapshot().reference());
    }
    @Test void handlesAnEmptyDatabaseWithoutLoadingTransactions() {
        FakeRepository repository = new FakeRepository(); repository.years=List.of(); repository.coverage=null;
        ProductAnalysisService service = new ProductAnalysisService(connections,Runnable::run,c -> repository,clock);
        var page = service.load(null,ProductGrouping.FAMILY).join();
        assertTrue(page.groups().isEmpty()); assertEquals(0,repository.reads);
        assertEquals(LocalDate.of(2026,10,13),page.snapshot().reference());
    }
    private static class FakeRepository implements ProductAnalysisRepository {
        int reads;
        LocalDate from, through;
        List<Integer> years=List.of(2026,2025,2024,2020);
        SalesDataCoverage coverage = new SalesDataCoverage(LocalDate.of(2020,1,1),LocalDate.of(2026,10,7));
        public List<Integer> findYears() { return years; }
        public SalesDataCoverage loadCoverage() { return coverage; }
        public List<ProductSale> loadSales(LocalDate from,LocalDate through) {
            reads++; this.from=from; this.through=through;
            return List.of(new ProductSale("ZR9818MSTA2","MST A2",LocalDate.of(2026,1,1),"C1","Cliente Uno","D1",true,BigDecimal.TEN,BigDecimal.ONE));
        }
    }
}
