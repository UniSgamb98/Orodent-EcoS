# Schemi del database OrodentEcoS

EcoS usa un solo database Derby, organizzato per dominio dei dati.

| Schema | Tabelle attuali | Responsabilità |
| --- | --- | --- |
| `ECOS` | `ECOS_SCHEMA_HISTORY` | Versioni, checksum e date delle migrazioni |
| `ANAGRAFICHE` | `CLIENTI` | Anagrafica condivisa tra i moduli |
| `VENDITE` | `VENDITE_DETTAGLIO` | Vendite e rettifiche condivise |
| `TON` | 28 tabelle: cataloghi, specifiche versionate, produzioni, cotture, lotti e documenti | Dominio del laboratorio; [elenco completo](ton-schema-proposta.md) |

Statistiche legge clienti e vendite dagli schemi condivisi; TON gestisce il proprio
catalogo tecnico in `TON.PRODOTTI`. Lo schema `STATISTICHE` sarà creato soltanto
quando il modulo avrà dati propri da salvare.
I nomi degli schemi descrivono l'ambito dei dati; gli utenti JDBC e i permessi
restano un tema distinto. Non vengono creati utenti differenti per modulo.

I repository usano sempre nomi qualificati, per esempio `ANAGRAFICHE.CLIENTI`.
Il risultato delle query non dipende dallo schema predefinito della connessione.
Connessioni, transazioni e importazioni restano gestite dai servizi condivisi.

## Migrazione dalla versione 1 alla 2

La migrazione viene eseguita soltanto dall'HOST, prima di pubblicare il servizio
discovery. Prima delle scritture, un database legacy esistente viene salvato con
il backup consistente di Derby in `database.home/backups`. Un errore di backup
interrompe l'avvio senza applicare la migrazione; un database nuovo o già corrente
non richiede questa copia. Vedere [backup e ripristino](database-backup.md).
Tutte le operazioni seguenti sono nella stessa transazione:

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

Il CLIENT verifica soltanto lo schema corrente e lo storico; non migra il database remoto.
Se scopre un HOST precedente, interrompe l'avvio con un messaggio che chiede di
aggiornare l'HOST, senza creare un altro database locale.

## Migrazione dalla versione 2 alla 3

`V3__ton_products.sql` aggiunge lo schema `TON` e il catalogo `TON.PRODOTTI`.
I clienti e le vendite rimangono nelle stesse tabelle: non vengono ricopiati.
Il runner verifica lo storico V1/V2 e la struttura, esegue un backup consistente
prima delle scritture e applica soltanto la migrazione pendente. Storico e DDL
vengono confermati nella stessa transazione. L'UUID del database rimane invariato.

Un archivio V1 passa attraverso V2 e V3 nella stessa transazione, preceduta da
un solo backup. Un archivio nuovo non richiede backup. Un normale riavvio sullo
schema 3 non aggiunge righe allo storico e non crea altre copie. Script V1 e V2
rimangono immutati; tabelle TON preesistenti senza migrazione registrata non
vengono adottate o cancellate automaticamente.

La verifica CLIENT è in sola lettura e richiede lo schema corrente. Il recupero della
connessione può soltanto aprire un archivio già compatibile: non esegue migrazioni durante
un subentro. Per l'aggiornamento, chiudere le istanze e riavviare prima l'HOST
aggiornato, poi i CLIENT. Vedere [il primo catalogo TON](ton.md).

## Migrazione dalla versione 3 alla 4

`V4__ton_domain.sql` crea 24 tabelle TON oltre al catalogo V3. Comprende schede
tecniche versionate, composizioni per strato, modelli e configurazioni articolo,
produzioni, forni/cicli/carichi/lotti e documenti. Non inserisce dati dimostrativi
e non importa il database TON-V2. La struttura precede l'implementazione dei nuovi
flussi utente: [campi, collegamenti e limiti applicativi](ton-schema-proposta.md).

Il runner salva un backup V3 prima del DDL, conserva prodotti e relative sequenze,
clienti/vendite, storico V1/V2/V3 e UUID, poi applica V4 in una sola transazione.
Un errore anche nell'ultima tabella annulla tutte le creazioni V4. Le tabelle
preesistenti senza lo storico corrispondente non vengono adottate o cancellate.
V1 e V2 possono raggiungere direttamente V4 passando per le migrazioni mancanti,
con un solo backup preventivo. V1/V2/V3 restano immutabili.

HOST e CLIENT controllano in lettura la struttura V4, inclusi colonne, tipi,
nullabilità, default richiesti e presenza dei vincoli nominati. Un V3 senza V4
viene rifiutato dal CLIENT con l'indicazione di aggiornare l'HOST. La verifica
non ricrea colonne o vincoli rimossi manualmente. Il failover non applica DDL.

La migrazione non cambia EXE, Java, protocollo o configurazione: arriva con la
normale release firmata. Chiudere le istanze e riavviare prima l'HOST aggiornato,
poi i CLIENT. Un riavvio su V4 non aggiunge backup o righe allo storico.

## Migrazione dalla versione 4 alla 5

`V5__ton_powder_colors.sql` aggiunge `TON.POLVERI.colore`, con i valori ammessi
`BIANCA`, `GIALLA`, `ROSA` e `GRIGIA`. I record esistenti rimangono con colore
NULL: il vecchio numero di visualizzazione non permette di dedurlo con certezza.
L'interfaccia li mostra come **Da specificare** e richiede la scelta al prossimo
salvataggio dell'anagrafica. L'ordine del catalogo è bianche, gialle, rosa,
grigie, poi codice; i record senza colore compaiono in fondo.

La vecchia colonna `ordine_visualizzazione` rimane come dato legacy, senza essere
usata dall'applicazione. Identificativi, versioni tecniche, ossidi e riferimenti
delle ricette restano invariati. Il runner applica il DDL e registra V5 nella
stessa transazione, dopo il backup dell'HOST. Il CLIENT non migra un HOST V4;
il controllo dello schema V5 verifica anche colonna e vincolo del colore.
V1–V4 rimangono immutabili. La normale release firmata dei componenti distribuisce
questo aggiornamento; riavviare prima l'HOST aggiornato, poi i CLIENT.

## Migrazione dalla versione 5 alla 6

V6 aggiunge la linea canonica e il colore commerciale al prodotto, i campi delle
anagrafiche e le tabelle TON.LINEE_REVISIONI, TON.PRODOTTI_REVISIONI e
TON.ARTICOLI_REVISIONI. Ogni catalogo mantiene snapshot tipizzati completi;
le configurazioni possono riferirsi alle revisioni anagrafiche esatte usate
nell'approvazione. Il numero di tabelle TON passa a 28.

Gli ID e i dati precedenti restano conservati. Un'unica associazione legacy
linea/prodotto viene riportata nel riferimento canonico; con zero o più linee
il prodotto resta da completare. I colori commerciali non sono dedotti dal nome.
LINEE_PRODOTTI rimane conservata come dato legacy: le nuove operazioni usano
PRODOTTI.linea_id, con unicità della combinazione linea/colore.

Lo storico iniziale registra soltanto lo stato disponibile alla migrazione,
con il numero di revisione precedente e origine MIGRAZIONE. Le configurazioni
precedenti conservano i loro contenuti ma non ricevono retroattivamente snapshot
anagrafici non ricostruibili. Per nuovi impieghi si copiano e si approvano dopo
aver completato le anagrafiche. Vedere [uso dei cataloghi](ton-cataloghi.md).

V1–V5 non vengono modificate. Il backup precede il DDL; aggiunte, copia iniziale
e registrazione V6 sono atomiche. La verifica CLIENT controlla anche i nuovi
campi, le tre tabelle di storico e i relativi vincoli, senza applicare migrazioni.

## Distribuzione e migrazioni automatiche

Il contatore delle migrazioni è indipendente dalla compatibilità del launcher.
`database/version.properties`, nel JAR `orodent-database`, dichiara le versioni
interna corrente e minima supportata. L'applicazione legge da lì la versione
corrente; il publisher legge lo stesso file dai componenti che firma.

Il catalogo mantiene `database.schema=1`: il nome è storico e rappresenta il
contratto accettato dai launcher installati, non il contatore delle migrazioni.
Due campi aggiuntivi firmati dichiarano `database.migration.minimum=1` e
`database.migration.target=6`. Il vecchio launcher ignora i campi aggiuntivi e
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
