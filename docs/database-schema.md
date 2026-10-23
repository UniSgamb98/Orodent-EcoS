# Schemi del database OrodentEcoS

EcoS usa un solo database Derby, organizzato per dominio dei dati.

| Schema | Tabelle attuali | Responsabilità |
| --- | --- | --- |
| `ECOS` | `ECOS_SCHEMA_HISTORY` | Versioni, checksum e date delle migrazioni |
| `ANAGRAFICHE` | `CLIENTI` | Anagrafica condivisa tra i moduli |
| `VENDITE` | `VENDITE_DETTAGLIO` | Vendite e rettifiche condivise |

Statistiche legge clienti e vendite da questi schemi. Gli schemi `STATISTICHE` e
`TON` saranno creati quando i rispettivi moduli avranno dati propri da salvare.
I nomi degli schemi descrivono l'ambito dei dati; gli utenti JDBC e i permessi
restano un tema distinto. Non vengono creati utenti differenti per modulo.

I repository usano sempre nomi qualificati, per esempio `ANAGRAFICHE.CLIENTI`.
Il risultato delle query non dipende dallo schema predefinito della connessione.
Connessioni, transazioni e importazioni restano gestite dai servizi condivisi.

## Migrazione dalla versione 1 alla 2

La migrazione viene eseguita soltanto dall'HOST, prima di pubblicare il servizio
discovery. Tutte le operazioni seguenti sono nella stessa transazione:

1. Verifica della struttura e dello storico precedenti. I database senza storico
   vengono registrati come versione 1 usando lo script originale, immutato.
2. Creazione degli schemi e delle tabelle di destinazione con indici e vincoli.
3. Copia di tutte le colonne, compresi identificativi e timestamp. La sequenza
   delle vendite riparte dal prossimo valore precedente, anche se le ultime
   righe generate erano state cancellate.
4. Verifica del numero di righe, copia dello storico e registrazione della versione 2.
5. Rimozione delle tabelle fisiche in `APP`. I vecchi nomi restano come sinonimi
   delle tabelle nuove: non contengono copie dei dati e impediscono ai vecchi
   bootstrap di ricreare tabelle vuote parallele.

Lo storico ufficiale è `ECOS.ECOS_SCHEMA_HISTORY`, con una riga per migrazione.
Lo script `database/schema.sql` rimane la versione 1; la nuova struttura è in
`database/migrations/V2__domain_schemas.sql`. Dopo la pubblicazione questi script
non vanno modificati: un ulteriore cambiamento richiede una nuova migrazione.

Un errore di copia, un vincolo, una dipendenza dalle vecchie tabelle o uno storico
incompatibile annullano la transazione. Le tabelle e i dati precedenti restano
disponibili. Tabelle di destinazione già presenti senza uno storico valido non
vengono unite o sovrascritte automaticamente. Una seconda inizializzazione non
ricopia i dati né aggiunge altre righe allo storico.

Il CLIENT verifica soltanto lo schema 2 e lo storico; non migra il database remoto.
Se scopre un HOST precedente, interrompe l'avvio con un messaggio che chiede di
aggiornare l'HOST, senza creare un altro database locale.

## Distribuzione e migrazioni automatiche

Il contatore delle migrazioni è indipendente dalla compatibilità del launcher.
`database/version.properties`, nel JAR `orodent-database`, dichiara le versioni
interna corrente e minima supportata. L'applicazione legge da lì la versione
corrente; il publisher legge lo stesso file dai componenti che firma.

Il catalogo mantiene `database.schema=1`: il nome è storico e rappresenta il
contratto accettato dai launcher installati, non il contatore delle migrazioni.
Due campi aggiuntivi firmati dichiarano `database.migration.minimum=1` e
`database.migration.target=2`. Il vecchio launcher ignora i campi aggiuntivi e
continua a verificare firma, checksum, runtime e protocollo prima di attivare
l'intero insieme dei nuovi JAR. Il nuovo launcher verifica anche la validità
dell'intervallo dichiarato, senza vincolarlo a un singolo contatore DB.

La verifica del database effettivo avviene nell'applicazione: l'HOST applica le
migrazioni supportate e il CLIENT verifica la compatibilità dell'HOST. I valori del
catalogo non autorizzano la modifica di un database incompatibile e non sostituiscono
lo storico. I cataloghi precedenti, senza i campi aggiuntivi, restano leggibili.

Per questo passaggio non occorre sostituire EXE, launcher o Java: pubblicare una
normale release firmata dei componenti. Ogni postazione la riceve al successivo
avvio del launcher, se gli aggiornamenti sono abilitati e la chiave è quella fidata.

Per il passaggio in produzione:

1. Chiudere le istanze prima della migrazione e conservare una copia della directory
   `OrodentEcoS` con il database fermo.
2. Pubblicare la release firmata mantenendo runtime, protocollo e contratto attuali.
3. Riavviare per prima la postazione che ospita il DB. Il launcher scarica i nuovi
   JAR e l'HOST migra automaticamente il database nello stesso percorso.
4. Riavviare le postazioni CLIENT: anche queste scaricano i nuovi JAR. Conservare
   separati i profili sviluppo e produzione, come in [database-discovery.md](database-discovery.md).

Un CLIENT riavviato prima dell'HOST aggiornato segnala che l'HOST va aggiornato;
non viene creato un database alternativo. Gli aggiornamenti avvengono all'avvio:
non vengono chiusi o sostituiti automaticamente processi già in esecuzione.
Una distribuzione completa rimane necessaria per modificare parti che l'updater
non sostituisce, come Java o il launcher, o per cambiare il contratto richiesto.

I sinonimi legacy sono una protezione durante il passaggio, non il supporto
all'uso contemporaneo di versioni diverse. Dopo la migrazione non riavviare vecchie
distribuzioni sullo stesso database. Tornare allo schema 1 richiede il ripristino
della copia precedente con tutte le istanze ferme; il launcher non esegue downgrade
del database. Il fallback a una release disponibile offline non annulla una migrazione:
un'app incompatibile deve rifiutare il DB. Nessuna migrazione importa i dati della
vecchia app Statistiche.
