package com.orodent.tonv3.core.database.repository;

import com.orodent.tonv3.core.database.model.VenditaDettaglio;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VenditaDettaglioRepository {

    VenditaDettaglio insert(VenditaDettaglio vendita);

    int insertAll(List<VenditaDettaglio> vendite);

    void update(VenditaDettaglio vendita);

    Optional<VenditaDettaglio> findById(long venditaId);

    Optional<VenditaDettaglio> findByDocumento(
            String sorgente,
            String documentoId,
            int numeroRiga
    );

    List<VenditaDettaglio> findAll();

    List<VenditaDettaglio> findByDateRange(LocalDate dal, LocalDate al);

    List<VenditaDettaglio> findByClienteAndDateRange(
            String codiceCliente,
            LocalDate dal,
            LocalDate al
    );

    List<VenditaDettaglio> findByProdottoAndDateRange(
            String codiceProdotto,
            LocalDate dal,
            LocalDate al
    );

    List<VenditaDettaglio> findByClienteAndProdottoAndDateRange(
            String codiceCliente,
            String codiceProdotto,
            LocalDate dal,
            LocalDate al
    );

    List<VenditaDettaglio> findByCategoriaAndDateRange(
            String categoria,
            LocalDate dal,
            LocalDate al
    );

    void deleteById(long venditaId);

    int deleteByDateRange(LocalDate from, LocalDate through);
}
