package com.orodent.ecos.crm.feature.clienti.nuovocliente.dto;

import java.time.LocalDate;

public record ClienteInput(
        String ragioneSociale,
        String tipoCliente,
        String statoTrattativa,
        Integer coinvolgimento,
        String partitaIva,
        String codiceFiscale,
        LocalDate acquisizione,
        String operatore) {
    public static final String DEFAULT_OPERATORE = "utente";

    public ClienteInput {
        if (operatore == null || operatore.isBlank()) {
            operatore = DEFAULT_OPERATORE;
        }
    }
}
