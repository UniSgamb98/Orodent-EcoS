package com.orodent.ecos.crm.core.database.query;

import com.orodent.ecos.crm.core.database.model.CallOutcome;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClienteProfileQuery {
    Optional<ClienteProfileRecord> findById(UUID clienteId, UUID operatoreId);

    enum TimelineType {
        NOTA,
        CHIAMATA
    }

    record ClienteProfileRecord(
            UUID clienteId,
            String ragioneSociale,
            String tipoCliente,
            String statoTrattativa,
            Integer coinvolgimento,
            String partitaIva,
            String codiceFiscale,
            LocalDate acquisizione,
            boolean favorite,
            List<ValueRecord> telefoni,
            List<ValueRecord> email,
            List<ValueRecord> sitiWeb,
            List<AddressRecord> indirizzi,
            List<ContactRecord> contatti,
            List<TimelineRecord> timeline) {
        public ClienteProfileRecord {
            telefoni = List.copyOf(telefoni);
            email = List.copyOf(email);
            sitiWeb = List.copyOf(sitiWeb);
            indirizzi = List.copyOf(indirizzi);
            contatti = List.copyOf(contatti);
            timeline = List.copyOf(timeline);
        }
    }

    record ValueRecord(UUID id, String value) {}

    record AddressRecord(
            UUID id,
            String paese,
            String regione,
            String provincia,
            String citta,
            String indirizzo,
            String numeroCivico,
            String cap,
            boolean principale) {}

    record ContactRecord(
            UUID id, String descrizione, List<ValueRecord> telefoni, List<ValueRecord> email) {
        public ContactRecord {
            telefoni = List.copyOf(telefoni);
            email = List.copyOf(email);
        }
    }

    record TimelineRecord(
            UUID notaId,
            UUID interazioneId,
            LocalDate data,
            LocalDateTime createdAt,
            TimelineType type,
            LocalDate prossimoContatto,
            CallOutcome esito,
            String testo) {}
}
