package com.orodent.ecos.crm.feature.clienti.nuovocliente.service;

import com.orodent.ecos.crm.core.database.model.Cliente;
import com.orodent.ecos.crm.core.database.model.ClienteAggregate;
import com.orodent.ecos.crm.core.database.model.ContattoCliente;
import com.orodent.ecos.crm.core.database.model.EmailCliente;
import com.orodent.ecos.crm.core.database.model.IndirizzoCliente;
import com.orodent.ecos.crm.core.database.model.SitoWebCliente;
import com.orodent.ecos.crm.core.database.model.TelefonoCliente;
import com.orodent.ecos.crm.core.database.query.StatoTrattativaQuery;
import com.orodent.ecos.crm.core.database.query.TipoClienteQuery;
import com.orodent.ecos.crm.core.database.service.ClientePersistenceService;
import com.orodent.ecos.crm.core.database.service.CurrentOperatoreService;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.dto.ContattoClienteInput;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.dto.EmailClienteInput;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.dto.IndirizzoClienteInput;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.dto.NuovoClienteRequest;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.dto.SitoWebClienteInput;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.dto.TelefonoClienteInput;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class NuovoClienteService {

    private final ClientePersistenceService persistenceService;
    private final CurrentOperatoreService currentOperatoreService;
    private final TipoClienteQuery tipoClienteQuery;
    private final StatoTrattativaQuery statoTrattativaQuery;
    private NuovoClienteDraft lastPreparedDraft;

    public NuovoClienteService(ClientePersistenceService persistenceService) {
        this(persistenceService, persistenceService.operators(), null, null);
    }

    public NuovoClienteService(
            ClientePersistenceService persistenceService,
            CurrentOperatoreService currentOperatoreService) {
        this(persistenceService, currentOperatoreService, null, null);
    }

    public NuovoClienteService(
            ClientePersistenceService persistenceService,
            CurrentOperatoreService currentOperatoreService,
            TipoClienteQuery tipoClienteQuery) {
        this(persistenceService, currentOperatoreService, tipoClienteQuery, null);
    }

    public NuovoClienteService(
            ClientePersistenceService persistenceService,
            CurrentOperatoreService currentOperatoreService,
            TipoClienteQuery tipoClienteQuery,
            StatoTrattativaQuery statoTrattativaQuery) {
        this.persistenceService = persistenceService;
        this.currentOperatoreService = currentOperatoreService;
        this.tipoClienteQuery = tipoClienteQuery;
        this.statoTrattativaQuery = statoTrattativaQuery;
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

    public NuovoClienteDraft saveCliente(NuovoClienteRequest request) {
        NuovoClienteDraft draft = prepareCliente(request);
        persistenceService.saveNuovoCliente(draft.toAggregate());
        return draft;
    }

    public NuovoClienteDraft prepareCliente(NuovoClienteRequest request) {
        if (request.cliente().ragioneSociale() == null
                || request.cliente().ragioneSociale().isBlank()
                || request.cliente().ragioneSociale().strip().length() > 255)
            throw new IllegalArgumentException(
                    "La ragione sociale è obbligatoria, massimo 255 caratteri.");
        UUID clienteId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Cliente cliente =
                new Cliente(
                        clienteId,
                        clean(request.cliente().ragioneSociale()),
                        clean(request.cliente().tipoCliente()),
                        clean(request.cliente().statoTrattativa()),
                        cleanCoinvolgimento(request.cliente().coinvolgimento()),
                        clean(request.cliente().partitaIva()),
                        clean(request.cliente().codiceFiscale()),
                        request.cliente().acquisizione(),
                        currentOperatoreService.currentOperatoreId(),
                        now,
                        null);

        List<ContattoCliente> contatti = new ArrayList<>();
        List<TelefonoCliente> telefoniCollegati = new ArrayList<>();
        List<EmailCliente> emailCollegate = new ArrayList<>();
        Set<String> telefoniUsatiDaiContatti = new HashSet<>();
        Set<String> emailUsateDaiContatti = new HashSet<>();

        for (ContattoClienteInput input : request.contatti()) {
            String descrizione = clean(input.descrizione());
            List<String> telefoniContatto = cleanTelefonoInput(input.telefoni());
            List<String> emailContatto = cleanEmailInput(input.email());
            if (descrizione == null && telefoniContatto.isEmpty() && emailContatto.isEmpty()) {
                continue;
            }

            UUID contattoId = UUID.randomUUID();
            contatti.add(new ContattoCliente(contattoId, clienteId, descrizione));
            for (String telefono : telefoniContatto) {
                telefoniUsatiDaiContatti.add(key(telefono));
                telefoniCollegati.add(
                        new TelefonoCliente(UUID.randomUUID(), clienteId, contattoId, telefono));
            }
            for (String email : emailContatto) {
                emailUsateDaiContatti.add(key(email));
                emailCollegate.add(
                        new EmailCliente(UUID.randomUUID(), clienteId, contattoId, email));
            }
        }

        List<IndirizzoCliente> indirizzi = createIndirizzi(clienteId, request.indirizzi(), now);
        List<SitoWebCliente> sitiWeb = createSitiWeb(clienteId, request.sitiWeb());
        List<TelefonoCliente> telefoni = new ArrayList<>(telefoniCollegati);
        Set<String> telefoniGenerici = new HashSet<>(telefoniUsatiDaiContatti);
        request.telefoni().stream()
                .map(TelefonoClienteInput::descrizione)
                .map(NuovoClienteService::clean)
                .filter(value -> value != null)
                .filter(value -> telefoniGenerici.add(key(value)))
                .map(value -> new TelefonoCliente(UUID.randomUUID(), clienteId, null, value))
                .forEach(telefoni::add);
        List<EmailCliente> email = new ArrayList<>(emailCollegate);
        Set<String> emailGeneriche = new HashSet<>(emailUsateDaiContatti);
        request.email().stream()
                .map(EmailClienteInput::descrizione)
                .map(NuovoClienteService::clean)
                .filter(value -> value != null)
                .filter(value -> emailGeneriche.add(key(value)))
                .map(value -> new EmailCliente(UUID.randomUUID(), clienteId, null, value))
                .forEach(email::add);

        lastPreparedDraft =
                new NuovoClienteDraft(cliente, indirizzi, sitiWeb, telefoni, email, contatti);
        return lastPreparedDraft;
    }

    public Optional<NuovoClienteDraft> getLastPreparedDraft() {
        return Optional.ofNullable(lastPreparedDraft);
    }

    private static List<IndirizzoCliente> createIndirizzi(
            UUID clienteId, List<IndirizzoClienteInput> input, LocalDateTime now) {
        return input.stream()
                .filter(NuovoClienteService::hasAddressData)
                .map(
                        indirizzo ->
                                new IndirizzoCliente(
                                        UUID.randomUUID(),
                                        clienteId,
                                        clean(indirizzo.paese()),
                                        clean(indirizzo.regione()),
                                        clean(indirizzo.provincia()),
                                        clean(indirizzo.citta()),
                                        clean(indirizzo.indirizzo()),
                                        clean(indirizzo.numeroCivico()),
                                        clean(indirizzo.cap()),
                                        indirizzo.principale(),
                                        now,
                                        null))
                .toList();
    }

    private static boolean hasAddressData(IndirizzoClienteInput input) {
        return clean(input.paese()) != null
                || clean(input.regione()) != null
                || clean(input.provincia()) != null
                || clean(input.citta()) != null
                || clean(input.indirizzo()) != null
                || clean(input.numeroCivico()) != null
                || clean(input.cap()) != null;
    }

    private static List<SitoWebCliente> createSitiWeb(
            UUID clienteId, List<SitoWebClienteInput> input) {
        return input.stream()
                .map(SitoWebClienteInput::descrizione)
                .map(NuovoClienteService::clean)
                .filter(value -> value != null)
                .distinct()
                .map(value -> new SitoWebCliente(UUID.randomUUID(), clienteId, value))
                .toList();
    }

    private static List<String> cleanTelefonoInput(List<TelefonoClienteInput> input) {
        return input.stream()
                .map(TelefonoClienteInput::descrizione)
                .map(NuovoClienteService::clean)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private static List<String> cleanEmailInput(List<EmailClienteInput> input) {
        return input.stream()
                .map(EmailClienteInput::descrizione)
                .map(NuovoClienteService::clean)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private static Integer cleanCoinvolgimento(Integer value) {
        if (value == null || value < 1 || value > 5) {
            return null;
        }
        return value;
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String key(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record NuovoClienteDraft(
            Cliente cliente,
            List<IndirizzoCliente> indirizzi,
            List<SitoWebCliente> sitiWeb,
            List<TelefonoCliente> telefoni,
            List<EmailCliente> email,
            List<ContattoCliente> contatti) {
        public NuovoClienteDraft {
            indirizzi = List.copyOf(indirizzi);
            sitiWeb = List.copyOf(sitiWeb);
            telefoni = List.copyOf(telefoni);
            email = List.copyOf(email);
            contatti = List.copyOf(contatti);
        }

        private ClienteAggregate toAggregate() {
            return new ClienteAggregate(cliente, indirizzi, sitiWeb, telefoni, email, contatti);
        }
    }
}
