package com.orodent.ecos.crm.feature.clienti.schedacliente.service;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.model.CallOutcome;
import com.orodent.ecos.crm.core.database.model.Cliente;
import com.orodent.ecos.crm.core.database.model.ContattoCliente;
import com.orodent.ecos.crm.core.database.model.EmailCliente;
import com.orodent.ecos.crm.core.database.model.IndirizzoCliente;
import com.orodent.ecos.crm.core.database.model.Interazione;
import com.orodent.ecos.crm.core.database.model.NotaCliente;
import com.orodent.ecos.crm.core.database.model.SitoWebCliente;
import com.orodent.ecos.crm.core.database.model.TelefonoCliente;
import com.orodent.ecos.crm.core.database.query.ClienteProfileQuery;
import com.orodent.ecos.crm.core.database.query.ClienteProfileQuery.AddressRecord;
import com.orodent.ecos.crm.core.database.query.ClienteProfileQuery.ClienteProfileRecord;
import com.orodent.ecos.crm.core.database.query.ClienteProfileQuery.ContactRecord;
import com.orodent.ecos.crm.core.database.query.ClienteProfileQuery.TimelineRecord;
import com.orodent.ecos.crm.core.database.query.ClienteProfileQuery.ValueRecord;
import com.orodent.ecos.crm.core.database.query.StatoTrattativaQuery;
import com.orodent.ecos.crm.core.database.query.TipoClienteQuery;
import com.orodent.ecos.crm.core.database.query.derby.DerbyClienteProfileQuery;
import com.orodent.ecos.crm.core.database.query.derby.DerbyStatoTrattativaQuery;
import com.orodent.ecos.crm.core.database.query.derby.DerbyTipoClienteQuery;
import com.orodent.ecos.crm.core.database.service.ClientePersistenceService;
import com.orodent.ecos.crm.core.database.service.CurrentOperatoreService;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.AddressEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.AddressItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.ClienteProfile;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.ContactEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.ContactItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.EditProfileDraft;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FornoCatalogItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FornoClienteEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FornoClienteItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FresatoreCatalogItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FresatoreClienteEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FresatoreClienteItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.InteractionEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.InteractionPreview;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.InteractionType;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.MaterialeCatalogItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.MaterialeClienteEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.MaterialeClienteItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.TimelineFilter;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.ValueEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.ValueItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.repository.ClienteRisorseRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SchedaClienteService {

    private final ClienteProfileQuery profileQuery;
    private final ClientePersistenceService persistenceService;
    private final CurrentOperatoreService currentOperatoreService;
    private final TipoClienteQuery tipoClienteQuery;
    private final StatoTrattativaQuery statoTrattativaQuery;
    private final ClienteRisorseService risorseService;
    private ClienteProfile currentProfile;
    private EditProfileDraft editingDraft;
    private UUID currentClienteId;
    private TimelineFilter currentFilter = TimelineFilter.ALL;

    public SchedaClienteService(Database database) {
        this(
                new DerbyClienteProfileQuery(database),
                new ClientePersistenceService(database),
                database.operators(),
                new DerbyTipoClienteQuery(database),
                new DerbyStatoTrattativaQuery(database),
                database);
    }

    public SchedaClienteService(
            ClienteProfileQuery profileQuery,
            ClientePersistenceService persistenceService,
            CurrentOperatoreService currentOperatoreService) {
        this(profileQuery, persistenceService, currentOperatoreService, null, null);
    }

    public SchedaClienteService(
            ClienteProfileQuery profileQuery,
            ClientePersistenceService persistenceService,
            CurrentOperatoreService currentOperatoreService,
            TipoClienteQuery tipoClienteQuery) {
        this(profileQuery, persistenceService, currentOperatoreService, tipoClienteQuery, null);
    }

    public SchedaClienteService(
            ClienteProfileQuery profileQuery,
            ClientePersistenceService persistenceService,
            CurrentOperatoreService currentOperatoreService,
            TipoClienteQuery tipoClienteQuery,
            StatoTrattativaQuery statoTrattativaQuery) {
        this(
                profileQuery,
                persistenceService,
                currentOperatoreService,
                tipoClienteQuery,
                statoTrattativaQuery,
                null);
    }

    private SchedaClienteService(
            ClienteProfileQuery profileQuery,
            ClientePersistenceService persistenceService,
            CurrentOperatoreService currentOperatoreService,
            TipoClienteQuery tipoClienteQuery,
            StatoTrattativaQuery statoTrattativaQuery,
            Database database) {
        this.profileQuery = profileQuery;
        this.persistenceService = persistenceService;
        this.currentOperatoreService = currentOperatoreService;
        this.tipoClienteQuery = tipoClienteQuery;
        this.statoTrattativaQuery = statoTrattativaQuery;
        this.risorseService =
                new ClienteRisorseService(
                        database == null ? null : new ClienteRisorseRepository(database));
    }

    public List<String> getTipiCliente() {
        if (tipoClienteQuery == null) {
            return List.of();
        }

        return tipoClienteQuery.findAll().stream()
                .map(TipoClienteQuery.TipoClienteRecord::nome)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    public List<String> getStatiTrattativa() {
        if (statoTrattativaQuery == null) {
            return List.of();
        }

        return statoTrattativaQuery.findAll().stream()
                .map(StatoTrattativaQuery.StatoTrattativaRecord::nome)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    public ClienteProfile loadProfile(UUID clienteId) {
        currentClienteId = clienteId;
        currentProfile =
                clienteId == null
                        ? emptyProfile()
                        : profileQuery
                                .findById(clienteId, currentOperatoreService.currentOperatoreId())
                                .map(this::toClienteProfile)
                                .orElseGet(this::emptyProfile);
        editingDraft = null;
        currentFilter = TimelineFilter.ALL;
        return filteredProfile();
    }

    private ClienteProfile toClienteProfile(ClienteProfileRecord record) {
        return new ClienteProfile(
                record.clienteId(),
                record.ragioneSociale(),
                record.tipoCliente(),
                record.statoTrattativa(),
                record.coinvolgimento(),
                record.partitaIva(),
                record.codiceFiscale(),
                record.acquisizione(),
                record.favorite(),
                toValueItems(record.telefoni()),
                toValueItems(record.email()),
                toValueItems(record.sitiWeb()),
                toAddressItems(record.indirizzi()),
                toContactItems(record.contatti()),
                risorseService.findClienteForni(record.clienteId()),
                risorseService.findClienteFresatori(record.clienteId()),
                risorseService.findClienteMateriali(record.clienteId()),
                record.timeline().stream().map(this::toInteractionPreview).toList());
    }

    public List<FornoCatalogItem> getForniCatalog() {
        return risorseService.getForniCatalog();
    }

    public List<FresatoreCatalogItem> getFresatoriCatalog() {
        return risorseService.getFresatoriCatalog();
    }

    public List<MaterialeCatalogItem> getMaterialiCatalog() {
        return risorseService.getMaterialiCatalog();
    }

    private List<ValueItem> toValueItems(List<ValueRecord> values) {
        return values.stream().map(value -> new ValueItem(value.id(), value.value())).toList();
    }

    private List<AddressItem> toAddressItems(List<AddressRecord> values) {
        return values.stream()
                .map(
                        value ->
                                new AddressItem(
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

    private List<ContactItem> toContactItems(List<ContactRecord> values) {
        return values.stream()
                .map(
                        value ->
                                new ContactItem(
                                        value.id(),
                                        value.descrizione(),
                                        toValueItems(value.telefoni()),
                                        toValueItems(value.email())))
                .toList();
    }

    private InteractionPreview toInteractionPreview(TimelineRecord record) {
        InteractionType type =
                record.type() == ClienteProfileQuery.TimelineType.CHIAMATA
                        ? InteractionType.CHIAMATA
                        : InteractionType.NOTA;
        return new InteractionPreview(
                record.notaId(),
                record.interazioneId(),
                record.data(),
                record.createdAt(),
                type,
                record.prossimoContatto(),
                record.esito(),
                record.testo());
    }

    private ClienteProfile emptyProfile() {
        return new ClienteProfile(
                currentClienteId,
                "Cliente non trovato",
                "",
                "",
                null,
                "",
                "",
                null,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    public ClienteProfile toggleFavorite() {
        ensureProfileLoaded();
        if (currentClienteId == null) {
            return filteredProfile();
        }

        persistenceService.togglePreferito(
                currentOperatoreService.currentOperatoreId(), currentClienteId);
        return loadProfile(currentClienteId);
    }

    public ClienteProfile updateCoinvolgimento(Integer coinvolgimento) {
        ensureProfileLoaded();
        if (currentClienteId == null) {
            return filteredProfile();
        }

        Integer cleanCoinvolgimento = cleanCoinvolgimento(coinvolgimento);
        LocalDateTime now = LocalDateTime.now();
        Cliente cliente =
                new Cliente(
                        currentClienteId,
                        nullableClean(currentProfile.ragioneSociale()),
                        nullableClean(currentProfile.tipoCliente()),
                        nullableClean(currentProfile.statoTrattativa()),
                        cleanCoinvolgimento,
                        nullableClean(currentProfile.partitaIva()),
                        nullableClean(currentProfile.codiceFiscale()),
                        currentProfile.acquisizione(),
                        currentOperatoreService.currentOperatoreId(),
                        null,
                        now);
        persistenceService.updateCliente(cliente);
        currentProfile = currentProfile.withCoinvolgimento(cleanCoinvolgimento);
        return filteredProfile();
    }

    public ClienteProfile setTimelineFilter(TimelineFilter filter) {
        ensureProfileLoaded();
        currentFilter = filter == null ? TimelineFilter.ALL : filter;
        return filteredProfile();
    }

    public EditProfileDraft startEdit() {
        ensureProfileLoaded();
        currentFilter = TimelineFilter.ALL;
        editingDraft = EditProfileDraft.from(currentProfile);
        return editingDraft;
    }

    public List<FornoClienteEditInput> startForniEdit() {
        ensureProfileLoaded();
        return risorseService.startForniEdit(currentProfile);
    }

    public List<FornoClienteItem> cancelForniEdit() {
        ensureProfileLoaded();
        return risorseService.cancelForniEdit(currentProfile);
    }

    public List<FornoClienteItem> saveForniEdit(List<FornoClienteEditInput> forni) {
        ensureProfileLoaded();
        List<FornoClienteItem> savedForni = risorseService.saveForni(currentClienteId, forni);
        currentProfile = currentProfile.withForni(savedForni);
        return savedForni;
    }

    public List<FresatoreClienteEditInput> startFresatoriEdit() {
        ensureProfileLoaded();
        return risorseService.startFresatoriEdit(currentProfile);
    }

    public List<FresatoreClienteItem> cancelFresatoriEdit() {
        ensureProfileLoaded();
        return risorseService.cancelFresatoriEdit(currentProfile);
    }

    public List<FresatoreClienteItem> saveFresatoriEdit(List<FresatoreClienteEditInput> fresatori) {
        ensureProfileLoaded();
        List<FresatoreClienteItem> savedFresatori =
                risorseService.saveFresatori(currentClienteId, fresatori);
        currentProfile = currentProfile.withFresatori(savedFresatori);
        return savedFresatori;
    }

    public List<MaterialeClienteEditInput> startMaterialiEdit() {
        ensureProfileLoaded();
        return risorseService.startMaterialiEdit(currentProfile);
    }

    public List<MaterialeClienteItem> cancelMaterialiEdit() {
        ensureProfileLoaded();
        return risorseService.cancelMaterialiEdit(currentProfile);
    }

    public List<MaterialeClienteItem> saveMaterialiEdit(List<MaterialeClienteEditInput> materiali) {
        ensureProfileLoaded();
        List<MaterialeClienteItem> savedMateriali =
                risorseService.saveMateriali(currentClienteId, materiali);
        currentProfile = currentProfile.withMateriali(savedMateriali);
        return savedMateriali;
    }

    public ClienteProfile cancelEdit() {
        ensureProfileLoaded();
        editingDraft = null;
        return filteredProfile();
    }

    public ClienteProfile saveEdit(EditProfileDraft draft) {
        ensureProfileLoaded();
        if (currentClienteId == null) {
            return filteredProfile();
        }

        LocalDateTime now = LocalDateTime.now();
        Cliente cliente =
                new Cliente(
                        currentClienteId,
                        nullableClean(draft.ragioneSociale()),
                        nullableClean(draft.tipoCliente()),
                        nullableClean(draft.statoTrattativa()),
                        cleanCoinvolgimento(draft.coinvolgimento()),
                        nullableClean(draft.partitaIva()),
                        nullableClean(draft.codiceFiscale()),
                        draft.acquisizione(),
                        currentOperatoreService.currentOperatoreId(),
                        null,
                        now);

        ContactModels contactModels = toContactModels(draft.contatti());
        persistenceService.updateClienteProfile(
                cliente,
                toIndirizzi(draft.indirizzi(), now),
                toSitiWeb(draft.sitiWeb()),
                contactModels.contatti(),
                combine(toTelefoni(draft.telefoni(), null), contactModels.telefoni()),
                combine(toEmail(draft.email(), null), contactModels.email()),
                toNoteUpdates(draft.interazioni(), now),
                toInterazioneUpdates(draft.interazioni(), now));

        editingDraft = null;
        currentFilter = TimelineFilter.ALL;
        return loadProfile(currentClienteId);
    }

    private List<TelefonoCliente> toTelefoni(List<ValueEditInput> values, UUID contattoId) {
        return values.stream()
                .map(value -> new ValueItem(idOrNew(value.id()), normalize(value.value())))
                .filter(value -> !value.value().isBlank())
                .map(
                        value ->
                                new TelefonoCliente(
                                        value.id(), currentClienteId, contattoId, value.value()))
                .toList();
    }

    private List<EmailCliente> toEmail(List<ValueEditInput> values, UUID contattoId) {
        return values.stream()
                .map(value -> new ValueItem(idOrNew(value.id()), normalize(value.value())))
                .filter(value -> !value.value().isBlank())
                .map(
                        value ->
                                new EmailCliente(
                                        value.id(), currentClienteId, contattoId, value.value()))
                .toList();
    }

    private List<SitoWebCliente> toSitiWeb(List<ValueEditInput> values) {
        return values.stream()
                .map(value -> new ValueItem(idOrNew(value.id()), normalize(value.value())))
                .filter(value -> !value.value().isBlank())
                .map(value -> new SitoWebCliente(value.id(), currentClienteId, value.value()))
                .toList();
    }

    private ContactModels toContactModels(List<ContactEditInput> values) {
        List<ContattoCliente> contatti = new ArrayList<>();
        List<TelefonoCliente> telefoni = new ArrayList<>();
        List<EmailCliente> email = new ArrayList<>();
        for (ContactEditInput value : values) {
            UUID contattoId = idOrNew(value.id());
            String descrizione = nullableClean(value.descrizione());
            List<TelefonoCliente> telefoniContatto = toTelefoni(value.telefoni(), contattoId);
            List<EmailCliente> emailContatto = toEmail(value.email(), contattoId);
            if (descrizione == null && telefoniContatto.isEmpty() && emailContatto.isEmpty()) {
                continue;
            }

            contatti.add(new ContattoCliente(contattoId, currentClienteId, descrizione));
            telefoni.addAll(telefoniContatto);
            email.addAll(emailContatto);
        }
        return new ContactModels(contatti, telefoni, email);
    }

    private List<IndirizzoCliente> toIndirizzi(List<AddressEditInput> values, LocalDateTime now) {
        return values.stream()
                .map(
                        value ->
                                new AddressEditInput(
                                        idOrNew(value.id()),
                                        nullableClean(value.paese()),
                                        nullableClean(value.regione()),
                                        nullableClean(value.provincia()),
                                        nullableClean(value.citta()),
                                        nullableClean(value.indirizzo()),
                                        nullableClean(value.numeroCivico()),
                                        nullableClean(value.cap()),
                                        value.principale()))
                .filter(this::hasAddressData)
                .map(
                        value ->
                                new IndirizzoCliente(
                                        value.id(),
                                        currentClienteId,
                                        value.paese(),
                                        value.regione(),
                                        value.provincia(),
                                        value.citta(),
                                        value.indirizzo(),
                                        value.numeroCivico(),
                                        value.cap(),
                                        value.principale(),
                                        now,
                                        now))
                .toList();
    }

    private boolean hasAddressData(AddressEditInput value) {
        return value.paese() != null
                || value.regione() != null
                || value.provincia() != null
                || value.citta() != null
                || value.indirizzo() != null
                || value.numeroCivico() != null
                || value.cap() != null;
    }

    private <T> List<T> combine(List<T> first, List<T> second) {
        List<T> values = new ArrayList<>(first);
        values.addAll(second);
        return values;
    }

    private List<NotaCliente> toNoteUpdates(
            List<InteractionEditInput> interactions, LocalDateTime now) {
        return interactions.stream()
                .filter(interaction -> interaction.notaId() != null)
                .map(
                        interaction ->
                                new NotaCliente(
                                        interaction.notaId(),
                                        currentClienteId,
                                        currentOperatoreService.currentOperatoreId(),
                                        normalize(interaction.testo()),
                                        null,
                                        now))
                .filter(nota -> !nota.testo().isBlank())
                .toList();
    }

    private List<Interazione> toInterazioneUpdates(
            List<InteractionEditInput> interactions, LocalDateTime now) {
        return interactions.stream()
                .filter(interaction -> interaction.interazioneId() != null)
                .map(
                        interaction ->
                                new Interazione(
                                        interaction.interazioneId(),
                                        currentClienteId,
                                        currentOperatoreService.currentOperatoreId(),
                                        interaction.notaId(),
                                        interaction.data(),
                                        interaction.prossimoContatto(),
                                        interaction.esito(),
                                        BigDecimal.ZERO,
                                        null,
                                        now))
                .toList();
    }

    private UUID idOrNew(UUID id) {
        return id == null ? UUID.randomUUID() : id;
    }

    public ClienteProfile addNota(String testo) {
        ensureProfileLoaded();
        if (currentClienteId == null || testo == null || testo.isBlank()) {
            return filteredProfile();
        }

        LocalDateTime now = LocalDateTime.now();
        NotaCliente nota =
                new NotaCliente(
                        UUID.randomUUID(),
                        currentClienteId,
                        currentOperatoreService.currentOperatoreId(),
                        testo.trim(),
                        now,
                        null);
        persistenceService.addNota(nota);
        return loadProfile(currentClienteId);
    }

    public ClienteProfile addChiamata(String testo, LocalDate prossimoContatto, CallOutcome esito) {
        ensureProfileLoaded();
        if (currentClienteId == null) {
            return filteredProfile();
        }
        if (esito == null) {
            throw new IllegalArgumentException("Seleziona un esito per la chiamata.");
        }

        LocalDateTime now = LocalDateTime.now();
        NotaCliente nota = null;
        if (testo != null && !testo.isBlank()) {
            nota =
                    new NotaCliente(
                            UUID.randomUUID(),
                            currentClienteId,
                            currentOperatoreService.currentOperatoreId(),
                            testo.trim(),
                            now,
                            null);
        }

        Interazione interazione =
                new Interazione(
                        UUID.randomUUID(),
                        currentClienteId,
                        currentOperatoreService.currentOperatoreId(),
                        nota == null ? null : nota.id(),
                        LocalDate.now(),
                        prossimoContatto,
                        esito,
                        BigDecimal.ZERO,
                        now,
                        null);
        persistenceService.addChiamata(nota, interazione, esito.statoTrattativa().orElse(null));
        return loadProfile(currentClienteId);
    }

    private void addInteraction(InteractionPreview interaction) {
        List<InteractionPreview> interazioni = new ArrayList<>(currentProfile.interazioni());
        interazioni.add(0, interaction);
        currentProfile = currentProfile.withInterazioni(interazioni);
    }

    private ClienteProfile filteredProfile() {
        if (currentFilter == TimelineFilter.ALL) {
            return currentProfile;
        }

        List<InteractionPreview> filteredInteractions =
                currentProfile.interazioni().stream()
                        .filter(interaction -> currentFilter.matches(interaction.type()))
                        .toList();
        return currentProfile.withInterazioni(filteredInteractions);
    }

    private void ensureProfileLoaded() {
        if (currentProfile == null) {
            loadProfile(currentClienteId);
        }
    }

    private List<ValueItem> cleanValueItems(List<ValueEditInput> values) {
        return values.stream()
                .map(value -> new ValueItem(value.id(), normalize(value.value())))
                .filter(value -> !value.value().isBlank())
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private Integer cleanCoinvolgimento(Integer value) {
        if (value == null || value < 1 || value > 5) {
            return null;
        }
        return value;
    }

    private String nullableClean(String value) {
        String cleanValue = normalize(value);
        return cleanValue.isBlank() ? null : cleanValue;
    }

    private record ContactModels(
            List<ContattoCliente> contatti,
            List<TelefonoCliente> telefoni,
            List<EmailCliente> email) {}
}
