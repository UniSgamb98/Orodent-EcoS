package com.orodent.ecos.statistiche.features.sales.customers.model;

import java.time.LocalDate;

public record CustomerTrendPeriod(LocalDate from, LocalDate through,
                                  LocalDate previousFrom, LocalDate previousThrough) { }
