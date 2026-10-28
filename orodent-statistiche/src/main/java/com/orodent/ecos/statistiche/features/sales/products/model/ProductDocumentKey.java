package com.orodent.ecos.statistiche.features.sales.products.model;

/** A document can have multiple lines, while numbers can repeat across customers and years.
 * The imported source is an inventory phase, not a separate document namespace.
 */
public record ProductDocumentKey(int year, String customer, String document) {
    public static ProductDocumentKey of(ProductSale sale) {
        return new ProductDocumentKey(sale.date().getYear(), sale.customerCode(), sale.document());
    }
}
