package com.orodent.ecos.ton.features.products.model;

import java.util.List;

public record ProductPage(List<TonProduct> products, long total, int offset, int limit) {
    public ProductPage { products = List.copyOf(products); }
    public boolean hasNext() { return offset + products.size() < total; }
}
