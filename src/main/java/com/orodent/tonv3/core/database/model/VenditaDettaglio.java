package com.orodent.tonv3.core.database.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record VenditaDettaglio(
        Long venditaId,
        String sorgente,
        String documentoId,
        String numeroDocumento,
        int numeroRiga,
        LocalDate dataVendita,
        String codiceCliente,
        String codiceProdotto,
        String descrizioneProdotto,
        String categoriaProdotto,
        BigDecimal quantita,
        BigDecimal prezzoUnitario,
        BigDecimal scontoPercentuale,
        BigDecimal scontoImporto,
        BigDecimal importoNetto,
        TipoOperazione tipoOperazione,
        String canale,
        String agente,
        LocalDateTime importatoIl
) {
}
