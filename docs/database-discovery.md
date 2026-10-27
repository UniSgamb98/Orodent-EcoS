# Avvio automatico HOST / CLIENT

All'avvio l'applicazione cerca un host Ton-V3 sul computer locale e sulla rete LAN.
Un candidato viene accettato solo dopo una connessione al database `OrodentEcoS`
e una verifica in lettura delle tabelle e della versione dello schema dell'applicazione.

Se non trova un host utilizzabile, avvia il proprio database, inizializza lo schema e
avvia il server Derby. Solo a quel punto risponde alle richieste discovery.
Un lock sulla directory e la prenotazione della porta discovery impediscono a due
istanze locali di diventare contemporaneamente HOST. Se il database o le porte sono
occupati, ripete la ricerca per un massimo di 15 secondi per consentire a un'altra
istanza di terminare l'avvio, anche su un computer lento.
Errori di filesystem, schema o configurazione vengono segnalati senza trattarli come
un semplice database occupato.

Ogni operazione continua a utilizzare una connessione indipendente. Il CLIENT non
crea database, non inizializza lo schema e non spegne il server remoto alla chiusura.
L'HOST rilascia esclusivamente le risorse che ha avviato.

## Configurazione

Il file di produzione `config/application.properties` si trova accanto all'EXE
(o al JAR del launcher). Il profilo di sviluppo è `config/development.properties`
nella repository. La proprietà `orodent.ecos.config` permette un file esplicito.

| Chiave file | Produzione | Sviluppo | Override JVM |
| --- | --- | --- | --- |
| `database.home` | `I:/CliZr/Tommaso/Database` | `../.local/database`, relativo al file | `ton.database.home` |
| `database.port` | 1528 | 1529 | `ton.database.port` |
| `discovery.port` | 45680 | 45681 | `ton.discovery.port` |
| `discovery.timeout.ms` | 1500 | 1500 | `ton.discovery.timeout.ms` |

`ORODENT_ECOS_DATABASE_HOME` sovrascrive solo la directory del file; la proprietà
JVM ha precedenza anche sulla variabile. Il CLIENT usa il DB dell'HOST scoperto,
non la propria directory. I profili hanno porte diverse per impedire connessioni
accidentali tra sviluppo e produzione. Non sovrascriverle con gli stessi valori.
Senza file e senza override l'avvio diretto usa `~/.orodent-ecos/development`
con le porte di sviluppo. Un file esplicito mancante genera errore.

Per collegamenti tra computer, consentire sul firewall dell'HOST TCP 1528 e UDP 45680
(o le porte configurate). Il discovery utilizza broadcast IPv4 e funziona sulla stessa
rete locale; non attraversa automaticamente router o sottoreti.
Il protocollo è specifico di Ton-V3 e correla le risposte a ogni richiesta,
ma non introduce autenticazione o cifratura del discovery.

## Recupero della connessione e subentro

I CLIENT già aperti controllano periodicamente il collegamento (ogni due secondi,
con probe JDBC a timeout). Anche gli errori di connessione delle operazioni gestite
da `Database` segnalano la disconnessione. La finestra rimane aperta, mostra un
avviso e disabilita temporaneamente le pagine di lavoro. Il recupero avviene su un
thread dedicato, senza occupare il thread JavaFX o gli executor dei moduli.

Il CLIENT cerca nuovamente l'HOST. Se non ne trova uno, tenta di aprire **soltanto
il database già esistente nella propria `database.home`**. Per subentrare deve
ottenere il lock esclusivo `.ton-host.lock` e quello interno di Derby. Se il vecchio
HOST è ancora acceso e mantiene il lock, il CLIENT attende e continua a cercarlo:
una semplice perdita della rete non autorizza un secondo proprietario.

L'HOST registra un UUID persistente nella proprietà Derby
`orodent.ecos.database.identity`, senza modificare la versione dello schema. Ogni
CLIENT conserva l'identità verificata all'avvio e controlla quella dell'archivio
durante la riconnessione e prima di usare una nuova connessione. Un altro archivio
con lo stesso nome e schema viene rifiutato. La promozione apre Derby senza
`create=true`, verifica identità e schema e **non esegue migrazioni**.

Quando il subentro riesce, il nuovo HOST pubblica il discovery e gli altri CLIENT
si riconnettono a lui. Il ruolo visualizzato nella Home viene aggiornato. Le
schermate e le bozze in memoria rimangono aperte; i dati già visualizzati non sono
ricaricati automaticamente, per non sovrascrivere modifiche dell'utente. Ricaricare
la pagina o usare `Riprova` per aggiornare i risultati.

Le operazioni fallite **non vengono rieseguite automaticamente**. Una transazione
senza commit viene annullata da Derby; se si perde la risposta al commit, il
risultato può essere incerto. Verificare il contenuto prima di ripetere un
salvataggio. Questo recupero non introduce un registro di comandi idempotenti né
garantisce che tutte le chiamate JDBC già in corso terminino immediatamente durante
un'interruzione di rete: il probe a timeout consente al monitor di procedere
indipendentemente dalle operazioni applicative.

Per il subentro fra PC, tutti devono puntare alla **stessa cartella fisica di rete**,
con permessi di lettura/scrittura e supporto affidabile dei lock tra macchine.
Lo stesso testo `I:/...` non basta se le unità mappate indicano condivisioni diverse.
Se la condivisione è indisponibile, il CLIENT resta in riconnessione: non crea un
database vuoto alternativo. Il database non viene copiato o replicato. Una copia
di backup conserva anche l'UUID e non deve essere avviata come archivio parallelo
nello stesso ambiente di discovery.

La creazione iniziale rimane consentita al normale avvio su una nuova home; il
divieto di creazione riguarda il recupero di una sessione già collegata. Spegnere
il programma arresta anche il monitor e non lascia tentativi di promozione in
background.

Per introdurre questa versione, aggiornare e riavviare prima l'HOST: i nuovi CLIENT
richiedono che esponga l'identità persistente e segnalano chiaramente un HOST ancora
precedente. L'identità persistente non aggiunge tabelle; i contratti di release e discovery
UDP rimangono invariati. Lo schema completo TON richiede invece la migrazione SQL V4,
eseguita soltanto al riavvio pianificato dell'HOST. Le future migrazioni si eseguono durante un riavvio
pianificato dell'HOST, con le altre istanze chiuse e backup preventivo.

## Responsabilità

- `Database`: scelta del ruolo e stato dell'applicazione.
- `DerbyHost`: proprietà delle risorse locali, schema e ciclo di vita del server.
- `DerbyClient`: connessioni JDBC remote e verifica dell'host.
- `DiscoveryClient`, `DiscoveryServer`, `DiscoveryProtocol`: trasporto e protocollo UDP.
- `DatabaseConfiguration`: percorsi, porte e timeout.
- `DatabaseSchema`: inizializzazione transazionale e registrazione della versione
  in `ECOS.ECOS_SCHEMA_HISTORY`; verifica in sola lettura per i CLIENT.

La versione 2 separa anagrafica e vendite negli schemi `ANAGRAFICHE` e `VENDITE`.
L'HOST migra le vecchie tabelle `APP` in transazione, conservandone i dati.
Il CLIENT richiede lo schema corrente 4 e non tenta un database locale alternativo
se trova un HOST incompatibile.
Il passaggio richiede tutte le postazioni aggiornate: avviare prima l'HOST.
Vedere [struttura e migrazione del database](database-schema.md).

I test di integrazione avviano JVM separate con directory temporanee e porte dedicate:
verificano dati condivisi, avvio contemporaneo, connessioni indipendenti, chiusura del
CLIENT e conflitti con risorse occupate da processi che non partecipano al discovery.
Comprendono arresto ordinato e improvviso dell'HOST, elezione con due CLIENT,
rollback di una scrittura interrotta senza replay, perdita/ripristino della rete con
proprietario ancora attivo, home indisponibile e archivio esistente di identità
diversa. Il test JavaFX opzionale controlla avviso, disabilitazione e aggiornamento
del ruolo nella finestra reale. Le prove automatiche usano il filesystem locale:
prima dell'uso in ufficio verificare il subentro su due PC Windows e sulla
condivisione SMB effettiva, compresi i tempi di rilascio dei lock dopo un arresto.

## Cambio del nome database

Il database Derby è `OrodentEcoS`; un precedente `TonV3Database` non viene
rinominato o migrato automaticamente. Il vecchio database resta intatto.
Per conservare dati preesistenti occorre pianificare una migrazione separata.
Aggiornare le postazioni allo stesso nome database per utilizzare il discovery.
La variabile precedente `TON_DATABASE_HOME` non viene più letta.
