package com.orodent.ecos.crm.feature.clienti.nuovocliente.dto;

public record IndirizzoClienteInput(
        String paese,
        String regione,
        String provincia,
        String citta,
        String indirizzo,
        String numeroCivico,
        String cap,
        boolean principale) {}
