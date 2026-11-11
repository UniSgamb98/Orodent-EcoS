package com.orodent.ecos.crm.feature.clienti.schedacliente.dto;

import com.orodent.ecos.crm.core.database.model.CallOutcome;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class SchedaClienteModels {

    private SchedaClienteModels() {}

    public enum TimelineFilter {
        ALL,
        NOTES,
        CALLS;

        public boolean matches(InteractionType type) {
            return this == ALL
                    || (this == NOTES && type == InteractionType.NOTA)
                    || (this == CALLS && type == InteractionType.CHIAMATA);
        }
    }

    public enum InteractionType {
        NOTA("Nota"),
        CHIAMATA("Chiamata");

        private final String label;

        InteractionType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record ClienteProfile(
            UUID clienteId,
            String ragioneSociale,
            String tipoCliente,
            String statoTrattativa,
            Integer coinvolgimento,
            String partitaIva,
            String codiceFiscale,
            LocalDate acquisizione,
            boolean favorite,
            List<ValueItem> telefoni,
            List<ValueItem> email,
            List<ValueItem> sitiWeb,
            List<AddressItem> indirizzi,
            List<ContactItem> contatti,
            List<FornoClienteItem> forni,
            List<FresatoreClienteItem> fresatori,
            List<MaterialeClienteItem> materiali,
            List<InteractionPreview> interazioni) {
        public ClienteProfile {
            telefoni = List.copyOf(telefoni);
            email = List.copyOf(email);
            sitiWeb = List.copyOf(sitiWeb);
            indirizzi = List.copyOf(indirizzi);
            contatti = List.copyOf(contatti);
            forni = List.copyOf(forni);
            fresatori = List.copyOf(fresatori);
            materiali = List.copyOf(materiali);
            interazioni = List.copyOf(interazioni);
        }

        public ClienteProfile withCoinvolgimento(Integer coinvolgimento) {
            return new ClienteProfile(
                    clienteId,
                    ragioneSociale,
                    tipoCliente,
                    statoTrattativa,
                    coinvolgimento,
                    partitaIva,
                    codiceFiscale,
                    acquisizione,
                    favorite,
                    telefoni,
                    email,
                    sitiWeb,
                    indirizzi,
                    contatti,
                    forni,
                    fresatori,
                    materiali,
                    interazioni);
        }

        public ClienteProfile withFavorite(boolean favorite) {
            return new ClienteProfile(
                    clienteId,
                    ragioneSociale,
                    tipoCliente,
                    statoTrattativa,
                    coinvolgimento,
                    partitaIva,
                    codiceFiscale,
                    acquisizione,
                    favorite,
                    telefoni,
                    email,
                    sitiWeb,
                    indirizzi,
                    contatti,
                    forni,
                    fresatori,
                    materiali,
                    interazioni);
        }

        public ClienteProfile withInterazioni(List<InteractionPreview> interazioni) {
            return new ClienteProfile(
                    clienteId,
                    ragioneSociale,
                    tipoCliente,
                    statoTrattativa,
                    coinvolgimento,
                    partitaIva,
                    codiceFiscale,
                    acquisizione,
                    favorite,
                    telefoni,
                    email,
                    sitiWeb,
                    indirizzi,
                    contatti,
                    forni,
                    fresatori,
                    materiali,
                    interazioni);
        }

        public ClienteProfile withForni(List<FornoClienteItem> forni) {
            return new ClienteProfile(
                    clienteId,
                    ragioneSociale,
                    tipoCliente,
                    statoTrattativa,
                    coinvolgimento,
                    partitaIva,
                    codiceFiscale,
                    acquisizione,
                    favorite,
                    telefoni,
                    email,
                    sitiWeb,
                    indirizzi,
                    contatti,
                    forni,
                    fresatori,
                    materiali,
                    interazioni);
        }

        public ClienteProfile withFresatori(List<FresatoreClienteItem> fresatori) {
            return new ClienteProfile(
                    clienteId,
                    ragioneSociale,
                    tipoCliente,
                    statoTrattativa,
                    coinvolgimento,
                    partitaIva,
                    codiceFiscale,
                    acquisizione,
                    favorite,
                    telefoni,
                    email,
                    sitiWeb,
                    indirizzi,
                    contatti,
                    forni,
                    fresatori,
                    materiali,
                    interazioni);
        }

        public ClienteProfile withMateriali(List<MaterialeClienteItem> materiali) {
            return new ClienteProfile(
                    clienteId,
                    ragioneSociale,
                    tipoCliente,
                    statoTrattativa,
                    coinvolgimento,
                    partitaIva,
                    codiceFiscale,
                    acquisizione,
                    favorite,
                    telefoni,
                    email,
                    sitiWeb,
                    indirizzi,
                    contatti,
                    forni,
                    fresatori,
                    materiali,
                    interazioni);
        }
    }

    public record EditProfileDraft(
            String ragioneSociale,
            String tipoCliente,
            String statoTrattativa,
            Integer coinvolgimento,
            String partitaIva,
            String codiceFiscale,
            LocalDate acquisizione,
            List<ValueEditInput> telefoni,
            List<ValueEditInput> email,
            List<ValueEditInput> sitiWeb,
            List<AddressEditInput> indirizzi,
            List<ContactEditInput> contatti,
            List<InteractionEditInput> interazioni) {
        public EditProfileDraft {
            telefoni = List.copyOf(telefoni);
            email = List.copyOf(email);
            sitiWeb = List.copyOf(sitiWeb);
            indirizzi = List.copyOf(indirizzi);
            contatti = List.copyOf(contatti);
            interazioni = List.copyOf(interazioni);
        }

        private static List<ValueEditInput> toEditInputs(List<ValueItem> values) {
            return values.stream()
                    .map(value -> new ValueEditInput(value.id(), value.value()))
                    .toList();
        }

        private static List<AddressEditInput> toAddressEditInputs(List<AddressItem> values) {
            return values.stream()
                    .map(
                            value ->
                                    new AddressEditInput(
                                            value.id(),
                                            value.paese(),
                                            value.regione(),
                                            value.provincia(),
                                            value.citta(),
                                            value.indirizzo(),
                                            value.numeroCivico(),
                                            value.cap(),
                                            value.principale()))
                    .toList();
        }

        private static List<ContactEditInput> toContactEditInputs(List<ContactItem> values) {
            return values.stream()
                    .map(
                            value ->
                                    new ContactEditInput(
                                            value.id(),
                                            value.descrizione(),
                                            toEditInputs(value.telefoni()),
                                            toEditInputs(value.email())))
                    .toList();
        }

        public static EditProfileDraft from(ClienteProfile profile) {
            return new EditProfileDraft(
                    profile.ragioneSociale(),
                    profile.tipoCliente(),
                    profile.statoTrattativa(),
                    profile.coinvolgimento(),
                    profile.partitaIva(),
                    profile.codiceFiscale(),
                    profile.acquisizione(),
                    toEditInputs(profile.telefoni()),
                    toEditInputs(profile.email()),
                    toEditInputs(profile.sitiWeb()),
                    toAddressEditInputs(profile.indirizzi()),
                    toContactEditInputs(profile.contatti()),
                    profile.interazioni().stream().map(InteractionEditInput::from).toList());
        }
    }

    public record FornoCatalogItem(UUID fornoId, String tecnologia, String marca, String modello) {}

    public record FornoClienteItem(
            UUID id,
            UUID fornoId,
            String tecnologia,
            String anno,
            String marca,
            String modello,
            String nota) {}

    public record FornoClienteEditInput(
            UUID id,
            UUID fornoId,
            String tecnologia,
            String anno,
            String marca,
            String modello,
            String nota) {
        public static FornoClienteEditInput from(FornoClienteItem item) {
            return new FornoClienteEditInput(
                    item.id(),
                    item.fornoId(),
                    item.tecnologia(),
                    item.anno(),
                    item.marca(),
                    item.modello(),
                    item.nota());
        }
    }

    public record FresatoreCatalogItem(UUID fresatoreId, String marca, String modello) {}

    public record FresatoreClienteItem(
            UUID id, UUID fresatoreId, String marca, String modello, String nota) {}

    public record FresatoreClienteEditInput(
            UUID id, UUID fresatoreId, String marca, String modello, String nota) {
        public static FresatoreClienteEditInput from(FresatoreClienteItem item) {
            return new FresatoreClienteEditInput(
                    item.id(), item.fresatoreId(), item.marca(), item.modello(), item.nota());
        }
    }

    public record MaterialeCatalogItem(
            UUID materialeId, String materiale, String marchio, String modello) {}

    public record MaterialeClienteItem(
            UUID id,
            UUID materialeId,
            String materiale,
            String marchio,
            String modello,
            String consumo,
            String frequenzaAcquisto,
            String nota) {}

    public record MaterialeClienteEditInput(
            UUID id,
            UUID materialeId,
            String materiale,
            String marchio,
            String modello,
            String consumo,
            String frequenzaAcquisto,
            String nota) {
        public static MaterialeClienteEditInput from(MaterialeClienteItem item) {
            return new MaterialeClienteEditInput(
                    item.id(),
                    item.materialeId(),
                    item.materiale(),
                    item.marchio(),
                    item.modello(),
                    item.consumo(),
                    item.frequenzaAcquisto(),
                    item.nota());
        }
    }

    public record ValueItem(UUID id, String value) {}

    public record AddressItem(
            UUID id,
            String paese,
            String regione,
            String provincia,
            String citta,
            String indirizzo,
            String numeroCivico,
            String cap,
            boolean principale) {}

    public record ContactItem(
            UUID id, String descrizione, List<ValueItem> telefoni, List<ValueItem> email) {
        public ContactItem {
            telefoni = List.copyOf(telefoni);
            email = List.copyOf(email);
        }
    }

    public record ValueEditInput(UUID id, String value) {}

    public record AddressEditInput(
            UUID id,
            String paese,
            String regione,
            String provincia,
            String citta,
            String indirizzo,
            String numeroCivico,
            String cap,
            boolean principale) {}

    public record ContactEditInput(
            UUID id,
            String descrizione,
            List<ValueEditInput> telefoni,
            List<ValueEditInput> email) {
        public ContactEditInput {
            telefoni = List.copyOf(telefoni);
            email = List.copyOf(email);
        }
    }

    public record InteractionEditInput(
            UUID notaId,
            UUID interazioneId,
            LocalDate data,
            LocalDateTime createdAt,
            InteractionType type,
            LocalDate prossimoContatto,
            CallOutcome esito,
            String testo) {
        public static InteractionEditInput from(InteractionPreview interaction) {
            return new InteractionEditInput(
                    interaction.notaId(),
                    interaction.interazioneId(),
                    interaction.data(),
                    interaction.createdAt(),
                    interaction.type(),
                    interaction.prossimoContatto(),
                    interaction.esito(),
                    interaction.testo());
        }
    }

    public record InteractionPreview(
            UUID notaId,
            UUID interazioneId,
            LocalDate data,
            LocalDateTime createdAt,
            InteractionType type,
            LocalDate prossimoContatto,
            CallOutcome esito,
            String testo) {}
}
