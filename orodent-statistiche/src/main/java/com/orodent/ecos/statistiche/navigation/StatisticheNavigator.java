package com.orodent.ecos.statistiche.navigation;

public interface StatisticheNavigator {

    void showHome();

    void showSalesImport();

    void showCustomerImport();
    void showCustomers();
    default void showCustomer(String code, int year) { showCustomers(); }
    void showProducts();
    void showDiscounts();
    void showReturns();
    void showSalesArchive();
}
