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

## Distribuzione della versione 2

I launcher della linea precedente accettano soltanto cataloghi con `database.schema=1`.
La nuova linea dichiara `database.schema=2`: occorre installare una distribuzione
completa con il nuovo launcher su tutte le postazioni. Il runtime Java resta della
stessa linea. Le successive release con schema 2 possono usare gli aggiornamenti
automatici dei componenti.

Per il passaggio in produzione:

1. Chiudere tutte le istanze. Conservare una copia della directory `OrodentEcoS`
   con il database fermo.
2. Installare il nuovo pacchetto conservando il proprio file di configurazione e
   lo stesso percorso `database.home`.
3. Avviare la postazione HOST per prima: applica la migrazione al database esistente.
4. Avviare le postazioni CLIENT aggiornate. Conservare separati i profili sviluppo
   e produzione, come descritto in [database-discovery.md](database-discovery.md).

I sinonimi legacy sono una protezione durante il passaggio, non il supporto
all'uso contemporaneo di versioni diverse. Dopo la migrazione non riavviare vecchie
distribuzioni sullo stesso database. Tornare allo schema 1 richiede il ripristino
della copia precedente con tutte le istanze ferme; il launcher non esegue downgrade
del database. Nessuna migrazione importa i dati della vecchia app Statistiche.
