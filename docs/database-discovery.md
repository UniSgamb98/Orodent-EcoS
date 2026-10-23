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

La scelta HOST/CLIENT avviene all'avvio. Se l'HOST viene chiuso, i CLIENT perdono
l'accesso ai dati: non viene promosso automaticamente un nuovo HOST durante la sessione.
Il database non viene copiato sul computer CLIENT.

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
Un CLIENT non accetta un HOST di schema 1 e non tenta un database locale alternativo.
Il passaggio richiede tutte le postazioni aggiornate: avviare prima l'HOST.
Vedere [struttura e migrazione del database](database-schema.md).

I test di integrazione avviano JVM separate con directory temporanee e porte dedicate:
verificano dati condivisi, avvio contemporaneo, connessioni indipendenti, chiusura del
CLIENT e conflitti con risorse occupate da processi che non partecipano al discovery.

## Cambio del nome database

Il database Derby è `OrodentEcoS`; un precedente `TonV3Database` non viene
rinominato o migrato automaticamente. Il vecchio database resta intatto.
Per conservare dati preesistenti occorre pianificare una migrazione separata.
Aggiornare le postazioni allo stesso nome database per utilizzare il discovery.
La variabile precedente `TON_DATABASE_HOME` non viene più letta.
