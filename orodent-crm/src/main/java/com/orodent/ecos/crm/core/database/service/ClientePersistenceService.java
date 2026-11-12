package com.orodent.ecos.crm.core.database.service;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.model.Cliente;
import com.orodent.ecos.crm.core.database.model.ClienteAggregate;
import com.orodent.ecos.crm.core.database.model.ContattoCliente;
import com.orodent.ecos.crm.core.database.model.EmailCliente;
import com.orodent.ecos.crm.core.database.model.IndirizzoCliente;
import com.orodent.ecos.crm.core.database.model.Interazione;
import com.orodent.ecos.crm.core.database.model.NotaCliente;
import com.orodent.ecos.crm.core.database.model.SitoWebCliente;
import com.orodent.ecos.crm.core.database.model.TelefonoCliente;
import com.orodent.ecos.crm.core.database.repository.ClientePreferitoRepository;
import com.orodent.ecos.crm.core.database.repository.ClienteRepository;
import com.orodent.ecos.crm.core.database.repository.ContattoClienteRepository;
import com.orodent.ecos.crm.core.database.repository.EmailClienteRepository;
import com.orodent.ecos.crm.core.database.repository.IndirizzoClienteRepository;
import com.orodent.ecos.crm.core.database.repository.InterazioneRepository;
import com.orodent.ecos.crm.core.database.repository.NotaClienteRepository;
import com.orodent.ecos.crm.core.database.repository.SitoWebClienteRepository;
import com.orodent.ecos.crm.core.database.repository.TelefonoClienteRepository;
import com.orodent.ecos.crm.core.database.repository.derby.DerbyClientePreferitoRepository;
import com.orodent.ecos.crm.core.database.repository.derby.DerbyClienteRepository;
import com.orodent.ecos.crm.core.database.repository.derby.DerbyContattoClienteRepository;
import com.orodent.ecos.crm.core.database.repository.derby.DerbyEmailClienteRepository;
import com.orodent.ecos.crm.core.database.repository.derby.DerbyIndirizzoClienteRepository;
import com.orodent.ecos.crm.core.database.repository.derby.DerbyInterazioneRepository;
import com.orodent.ecos.crm.core.database.repository.derby.DerbyNotaClienteRepository;
import com.orodent.ecos.crm.core.database.repository.derby.DerbySitoWebClienteRepository;
import com.orodent.ecos.crm.core.database.repository.derby.DerbyTelefonoClienteRepository;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ClientePersistenceService {

    private final Database database;
    private final ClienteRepository clienteRepository;
    private final IndirizzoClienteRepository indirizzoRepository;
    private final SitoWebClienteRepository sitoWebRepository;
    private final ContattoClienteRepository contattoRepository;
    private final TelefonoClienteRepository telefonoRepository;
    private final EmailClienteRepository emailRepository;
    private final NotaClienteRepository notaRepository;
    private final InterazioneRepository interazioneRepository;
    private final ClientePreferitoRepository clientePreferitoRepository;

    public ClientePersistenceService(Database database) {
        this(
                database,
                new DerbyClienteRepository(database),
                new DerbyIndirizzoClienteRepository(database),
                new DerbySitoWebClienteRepository(database),
                new DerbyContattoClienteRepository(database),
                new DerbyTelefonoClienteRepository(database),
                new DerbyEmailClienteRepository(database),
                new DerbyNotaClienteRepository(database),
                new DerbyInterazioneRepository(database),
                new DerbyClientePreferitoRepository(database));
    }

    public ClientePersistenceService(
            Database database,
            ClienteRepository clienteRepository,
            IndirizzoClienteRepository indirizzoRepository,
            SitoWebClienteRepository sitoWebRepository,
            ContattoClienteRepository contattoRepository,
            TelefonoClienteRepository telefonoRepository,
            EmailClienteRepository emailRepository,
            NotaClienteRepository notaRepository,
            InterazioneRepository interazioneRepository,
            ClientePreferitoRepository clientePreferitoRepository) {
        this.database = database;
        this.clienteRepository = clienteRepository;
        this.indirizzoRepository = indirizzoRepository;
        this.sitoWebRepository = sitoWebRepository;
        this.contattoRepository = contattoRepository;
        this.telefonoRepository = telefonoRepository;
        this.emailRepository = emailRepository;
        this.notaRepository = notaRepository;
        this.interazioneRepository = interazioneRepository;
        this.clientePreferitoRepository = clientePreferitoRepository;
    }

    public void saveNuovoCliente(ClienteAggregate draft) {
        Connection connection = database.getConnection();

        try {

            clienteRepository.insert(draft.cliente());
            indirizzoRepository.insertAll(draft.indirizzi());
            sitoWebRepository.insertAll(draft.sitiWeb());
            contattoRepository.insertAll(draft.contatti());
            telefonoRepository.insertAll(draft.telefoni());
            emailRepository.insertAll(draft.email());

        } catch (RuntimeException e) {

            throw new RuntimeException("Errore salvataggio nuovo cliente.", e);
        }
    }

    public CurrentOperatoreService operators() {
        return database.operators();
    }

    public void updateCliente(Cliente cliente) {
        database.claim(cliente.id());
        clienteRepository.update(cliente);
    }

    public void updateClienteProfile(
            Cliente cliente,
            List<IndirizzoCliente> indirizzi,
            List<SitoWebCliente> sitiWeb,
            List<ContattoCliente> contatti,
            List<TelefonoCliente> telefoni,
            List<EmailCliente> email,
            List<NotaCliente> note,
            List<Interazione> interazioni) {
        Connection connection = database.getConnection();

        try {

            database.claim(cliente.id());
            clienteRepository.update(cliente);
            syncIndirizzi(cliente.id(), indirizzi);
            syncSitiWeb(cliente.id(), sitiWeb);
            syncContatti(cliente.id(), contatti);
            syncTelefoni(cliente.id(), telefoni);
            syncEmail(cliente.id(), email);
            syncNote(cliente.id(), note);
            syncInterazioni(cliente.id(), interazioni);

        } catch (RuntimeException e) {

            throw new RuntimeException("Errore aggiornamento scheda cliente.", e);
        }
    }

    private void syncTelefoni(UUID clienteId, List<TelefonoCliente> desired) {
        syncById(
                telefonoRepository.findByClienteId(clienteId),
                desired,
                TelefonoCliente::id,
                telefonoRepository::insert,
                telefonoRepository::update,
                telefonoRepository::deleteById);
    }

    private void syncEmail(UUID clienteId, List<EmailCliente> desired) {
        syncById(
                emailRepository.findByClienteId(clienteId),
                desired,
                EmailCliente::id,
                emailRepository::insert,
                emailRepository::update,
                emailRepository::deleteById);
    }

    private void syncSitiWeb(UUID clienteId, List<SitoWebCliente> desired) {
        syncById(
                sitoWebRepository.findByClienteId(clienteId),
                desired,
                SitoWebCliente::id,
                sitoWebRepository::insert,
                sitoWebRepository::update,
                sitoWebRepository::deleteById);
    }

    private void syncContatti(UUID clienteId, List<ContattoCliente> desired) {
        syncById(
                contattoRepository.findByClienteId(clienteId),
                desired,
                ContattoCliente::id,
                contattoRepository::insert,
                contattoRepository::update,
                contattoRepository::deleteById);
    }

    private void syncIndirizzi(UUID clienteId, List<IndirizzoCliente> desired) {
        syncById(
                indirizzoRepository.findByClienteId(clienteId),
                desired,
                IndirizzoCliente::id,
                indirizzoRepository::insert,
                indirizzoRepository::update,
                indirizzoRepository::deleteById);
    }

    private void syncNote(UUID clienteId, List<NotaCliente> desired) {
        var existing = notaRepository.findByClienteId(clienteId);
        var byId =
                existing.stream().collect(Collectors.toMap(NotaCliente::id, Function.identity()));
        var retained =
                desired.stream()
                        .map(
                                n -> {
                                    var old = byId.get(n.id());
                                    if (old == null) return n;
                                    if (java.util.Objects.equals(old.testo(), n.testo()))
                                        return old;
                                    return new NotaCliente(
                                            n.id(),
                                            n.clienteId(),
                                            old.operatoreId(),
                                            n.testo(),
                                            old.createdAt(),
                                            n.updatedAt());
                                })
                        .toList();
        syncById(
                existing,
                retained,
                NotaCliente::id,
                notaRepository::insert,
                notaRepository::update,
                notaRepository::deleteById);
    }

    private void syncInterazioni(UUID clienteId, List<Interazione> desired) {
        var existing = interazioneRepository.findByClienteId(clienteId);
        var byId =
                existing.stream().collect(Collectors.toMap(Interazione::id, Function.identity()));
        var retained =
                desired.stream()
                        .map(
                                n -> {
                                    var old = byId.get(n.id());
                                    if (old == null) return n;
                                    boolean same =
                                            java.util.Objects.equals(old.notaId(), n.notaId())
                                                    && java.util.Objects.equals(
                                                            old.dataContatto(), n.dataContatto())
                                                    && java.util.Objects.equals(
                                                            old.prossimoContatto(),
                                                            n.prossimoContatto())
                                                    && old.esito() == n.esito();
                                    return same
                                            ? old
                                            : new Interazione(
                                                    n.id(),
                                                    n.clienteId(),
                                                    old.operatoreId(),
                                                    n.notaId(),
                                                    n.dataContatto(),
                                                    n.prossimoContatto(),
                                                    n.esito(),
                                                    old.coinvolgimento(),
                                                    old.createdAt(),
                                                    n.updatedAt());
                                })
                        .toList();
        syncById(
                existing,
                retained,
                Interazione::id,
                interazioneRepository::insert,
                interazioneRepository::update,
                interazioneRepository::deleteById);
    }

    private <T> void syncById(
            List<T> existing,
            List<T> desired,
            Function<T, UUID> idExtractor,
            Consumer<T> insert,
            Consumer<T> update,
            Consumer<UUID> delete) {
        Map<UUID, T> existingById =
                existing.stream().collect(Collectors.toMap(idExtractor, Function.identity()));
        Set<UUID> desiredIds = desired.stream().map(idExtractor).collect(Collectors.toSet());

        for (T item : desired) {
            UUID id = idExtractor.apply(item);
            if (existingById.containsKey(id)) {
                if (!java.util.Objects.equals(existingById.get(id), item)) update.accept(item);
            } else {
                insert.accept(item);
            }
        }

        for (UUID existingId : existingById.keySet()) {
            if (!desiredIds.contains(existingId)) {
                delete.accept(existingId);
            }
        }
    }

    public void addNota(NotaCliente nota) {
        database.claim(nota.clienteId());
        notaRepository.insert(nota);
    }

    public void addChiamata(
            NotaCliente nota, Interazione interazione, String nuovoStatoTrattativa) {
        database.claim(interazione.clienteId());
        Connection connection = database.getConnection();

        try {

            if (nota != null) {
                notaRepository.insert(nota);
            }
            interazioneRepository.insert(interazione);
            if (nuovoStatoTrattativa != null) {
                clienteRepository.updateStatoTrattativa(
                        interazione.clienteId(), nuovoStatoTrattativa, interazione.createdAt());
            }

        } catch (RuntimeException e) {

            throw new RuntimeException("Errore salvataggio chiamata cliente.", e);
        }
    }

    public boolean togglePreferito(UUID operatoreId, UUID clienteId) {
        boolean alreadyFavorite = clientePreferitoRepository.exists(operatoreId, clienteId);
        if (alreadyFavorite) {
            clientePreferitoRepository.remove(operatoreId, clienteId);
            return false;
        }

        clientePreferitoRepository.add(operatoreId, clienteId);
        return true;
    }
}
