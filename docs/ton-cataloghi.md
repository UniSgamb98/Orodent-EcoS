# Cataloghi TON: Linee, Prodotti tecnici e Articoli

Le schede usano il [contenitore comune di Dati tecnici](ton-schede-tecniche.md).

Aprire **TON → Dati tecnici → Linee / Prodotti tecnici / Articoli**.
Gli stessi cataloghi sono raggiungibili dalla Panoramica.

La gerarchia è **Linea → Prodotto → Articolo**:

- Una linea raggruppa una famiglia di prodotti; comprende codice, nome, note e stato.
  Tecnologia e Materia prima non sono più richieste né mostrate nell’anagrafica corrente.
- Un prodotto appartiene a una sola linea e identifica un colore commerciale,
  per esempio A2. **Senza colore** è una scelta esplicita, distinta da un valore
  mancante. Non è il colore Bianca/Gialla/Rosa/Grigia delle polveri.
- Un articolo identifica il prodotto in un formato: diametro e altezza, in mm.
  Linea e colore vengono mostrati dal prodotto selezionato, senza reinserirli.

Il codice articolo può coincidere con quello delle vendite di Statistiche.
L'ID interno resta stabile; lo storico conserva anche i codici precedenti,
che non possono essere assegnati a un'altra anagrafica attraverso i servizi.
L'apertura delle vendite dalla scheda TON sarà un passaggio successivo.

## Creazione dei prodotti

Si scelgono **linea** e **colore commerciale**: codice e descrizione appaiono
in anteprima e non richiedono compilazione manuale. Con la linea di codice
**MST**, nome **Thor** e colore **A0** si ottengono **MSTA0** e **Thor A0**.
Il codice concatena codice linea e colore senza separatori aggiuntivi;
**Senza colore** usa il solo codice linea, con descrizione **Thor Senza colore**.
Le note restano libere.

Il servizio genera i valori dai dati correnti della linea nel DB. Cambiando
linea o colore di un prodotto non ancora utilizzato li ricalcola e conserva
la revisione precedente. Se linea e colore non cambiano, un prodotto esistente
conserva codice e descrizione già salvati, anche se diversi dal nuovo formato:
una modifica alle note o una correzione del nome della linea non lo rinomina.
Questa modifica non richiede una nuova migrazione del DB.

## Inserimento multiplo

Gli elenchi Prodotti tecnici e Articoli offrono **Inserimento multiplo…**.
Dalle schede Linea e Prodotto si usano **Crea più prodotti…** e **Crea più articoli…**
con il padre preselezionato. La pagina permette di scegliere colori, prodotti e
formati, comporre i codici con blocchi visivi e controllare il gruppo prima della
creazione. I duplicati esatti vengono saltati; i conflitti vanno corretti o esclusi.
Tutte le nuove righe e le rispettive revisioni si salvano insieme. Non crea
specifiche, ricette o approvazioni implicitamente.

[Uso, regole e controlli dell'inserimento multiplo](ton-bulk-entry-proposal.md).

## Elenchi e collegamenti

La pagina usa il [componente comune degli elenchi TON](ton-elenchi.md).

Gli elenchi cercano codice o descrizione e mostrano tutti i risultati filtrati
in una tabella scorrevole, senza pulsante di apertura, frecce di navigazione
o contatore in basso. Ricerca, filtri e
strumenti sono in un pannello bianco sullo sfondo azzurro, con la stessa palette
delle schede di dettaglio e dell’elenco polveri.
Percentuale, underscore e barra inversa nella ricerca vengono trattati come
caratteri letterali. Le colonne sono ordinate dal DB per codice.

- Prodotti: filtro per linea.
- Articoli: filtri per linea, prodotto, diametro e altezza.
- **Mostra anche archiviati** comprende gli elementi disattivati.
- Doppio clic o Invio aprono l'editor completo. Tornando all'elenco si mantengono
  i filtri; selezionare una riga non sostituisce un editor aperto.

I selettori dei padri hanno una ricerca dedicata e mostrano al massimo 50
risultati per volta, più l'eventuale valore già selezionato. Non caricano l'intero
catalogo. Per aggiungere un nuovo figlio occorrono padri attivi e completi.
Un padre archiviato già assegnato resta visibile, senza essere proposto per nuove
associazioni.

La scheda **Prodotti tecnici** della linea elenca tutti i prodotti collegati in
un unico elenco scorrevole, anche oltre i primi 50. Non ha contatore, frecce di
pagina o pulsante di apertura: doppio clic sulla riga o Invio apre il prodotto.
Permette di crearne uno con la linea già selezionata. La scheda **Articoli** del
prodotto fa lo stesso per i formati: elenco completo scorrevole, senza frase
introduttiva, contatore, frecce o pulsante di apertura; doppio clic o Invio apre
la scheda articolo. **Apri linea / Apri prodotto** risale al padre.

Le schede di Linee, Prodotti tecnici e Articoli mostrano codice e nome
nell’intestazione di TON. Il collegamento **Torna…** è a sinistra e **Aggiorna dati**
a destra. Nei prodotti e negli articoli, **Apri linea / Apri prodotto** è accanto
al riquadro della revisione. Pannelli, campi, schede e pulsanti condividono la
palette della scheda polvere. Le colonne dei cataloghi e degli elenchi collegati
si distribuiscono uniformemente su tutta la larghezza disponibile.

I precedenti valori di Tecnologia e Materia prima restano conservati nel database e,
quando presenti, nelle revisioni storiche. Salvare una linea esistente non li cancella.
Le polveri della composizione e i loro fornitori descrivono i materiali effettivi.
Nell’inserimento multiplo non esiste più il blocco **Materiale**: per introdurre
prefissi come `ZR` o `ZS` si usa **Testo fisso**. I codici già salvati rimangono invariati.

## Anagrafiche e Revisioni

**Crea** salva la prima revisione. **Salva revisione** registra i valori correnti
e conserva le revisioni precedenti nella stessa transazione. Un salvataggio senza
cambiamenti non aggiunge una revisione fittizia.

La scheda **Revisioni** usa la struttura comune di Dati tecnici: titolo e
**Aggiorna** sulla stessa riga e tabella estesa all'altezza disponibile.
Mostra tutte le revisioni, con data e operazione, senza paginazione o pulsante
di apertura. Doppio clic o Invio apre una finestra in sola lettura con i valori
completi, inclusi codice, attributi, note e stato.
I riferimenti ai padri conservano il codice disponibile al momento della revisione.
Le informazioni che non erano disponibili prima della migrazione non vengono inventate.

Le liste **Prodotti tecnici** nella Linea e **Articoli** nel Prodotto si estendono
anch'esse fino al fondo della scheda.

Il contatore per la concorrenza e lo storico sono legati: ogni scrittura del
catalogo passa dal servizio. Scritture SQL esterne che avanzano il contatore
senza registrare la revisione impediscono ulteriori modifiche tramite l'app
finché la discrepanza non viene verificata.

Le identità strutturali rispettano questi vincoli:

- La linea mantiene i valori storici di tecnologia e materia prima, senza esporli
  nell’anagrafica corrente.
- Un prodotto usato mantiene linea e colore già definiti; i mancanti si possono
  completare dopo aver verificato le associazioni precedenti.
- Il prodotto di appartenenza di un articolo è fisso dal primo salvataggio,
  anche se l'articolo non è ancora utilizzato. Il selettore compare solo nella
  creazione; nella scheda esistente codice e nome del prodotto sono in sola
  lettura. Il servizio rifiuta anche richieste dirette di riassegnazione.
- Un articolo già utilizzato mantiene anche il formato; prima dell'utilizzo si
  possono correggere diametro e altezza mantenendo lo stesso prodotto.

Codice e nome della linea, codice e descrizione dell'articolo e le note restano
correggibili con storico. Nel prodotto codice e descrizione seguono le regole
di generazione descritte sopra. Per rappresentare
una famiglia, combinazione o formato diverso si crea una nuova anagrafica.
Un codice non può duplicare quello di un elemento archiviato né riutilizzare
quello conservato nello storico di un'altra identità.

**Archivia** conserva revisioni e collegamenti; **Riattiva** ripristina lo stato
attivo. Il cambio non si propaga ai figli. Un figlio di un padre archiviato è
mostrato con **Padre archiviato** e non è disponibile per nuove configurazioni.

**Elimina** è abilitato soltanto per la prima revisione mai utilizzata e senza
collegamenti. Il servizio ricontrolla riferimenti correnti e storici nella stessa
transazione. Altrimenti si usa Archivia. Non esistono cancellazioni a cascata.

## Prodotti e specifiche di produzione

La scheda **Specifiche di produzione** appartiene al **Prodotto tecnico**, tra
**Articoli** e **Revisioni**. Tutti gli articoli del prodotto ereditano la stessa
specifica predefinita: composizione, modello e metodo di calcolo. Nell'articolo
restano anagrafica, diametro e altezza; la sua colonna Produzione mostra la
configurazione ereditata. Le anagrafiche si salvano anche senza specifiche.

Occorrono una composizione approvata dello stesso prodotto, un modello approvato
e un riferimento al metodo di calcolo. Questa schermata non calcola le dosi:
la Produzione usa il gruppo del modello corrispondente al diametro dell'articolo
e l'altezza del formato. Un diametro non configurato viene segnalato nella
Produzione e non riceve una specifica autonoma.

- **Salva bozza** crea o aggiorna una versione modificabile. La creazione è
  serializzata sul prodotto e non aggiunge una seconda bozza ordinaria.
- **Salva e approva** verifica stato dei riferimenti, corrispondenza degli strati,
  totali al 100% e polveri attive con revisioni approvate. Congela i riferimenti
  alle revisioni di linea e prodotto. Le revisioni degli articoli vengono fissate
  quando si salva il piano di produzione.
- La prima versione approvata diventa predefinita per un prodotto nuovo.
  **Imposta predefinita** sceglie un'altra approvata con storico completo e
  riferimenti validi; il cambio è registrato nelle Revisioni del prodotto.
- Una versione approvata è di sola lettura. **Crea nuova versione** conserva la
  precedente e copia i valori in una bozza.
- **Ritira versione** conserva contenuti e collegamenti. Se era predefinita, il
  prodotto torna senza predefinita; i piani preparati mantengono la fotografia
  originale. È possibile ritirare anche dopo l'archiviazione.
- Le versioni e i selettori mantengono la propria paginazione e ricerca.

### Migrazione V21

`TON.CONFIGURAZIONI_PRODOTTO` è il nuovo proprietario delle specifiche.
La migrazione riunisce soltanto contenuti e snapshot equivalenti delle vecchie
configurazioni articolo; `TON.CONFIGURAZIONI_MIGRATE` conserva la corrispondenza
per ogni ID precedente. Stati, riferimenti tecnici e note distinti rimangono
versioni separate. Le configurazioni articolo originali restano intatte per i
riferimenti storici.

Se le predefinite precedenti convergono su una sola specifica approvata con
snapshot completi, questa diventa la predefinita del prodotto. In caso contrario
il prodotto è **da verificare**: tutte le versioni importate sono consultabili,
ma una scelta esplicita con **Imposta predefinita** è necessaria per produrre.
Non viene selezionata arbitrariamente una variante. Anche l'approvazione di una
nuova versione lascia tale scelta manuale quando c'è una verifica in sospeso.
Le configurazioni precedenti senza snapshot anagrafici devono essere copiate
e approvate nuovamente; non si inventano revisioni mancanti.

Migrazione, nuove associazioni e Revisioni dei prodotti vengono salvate nella
stessa transazione, dopo il backup previsto dall'HOST. Piani preparati,
documenti e produzioni fisiche precedenti non vengono riscritti. Costi nominali,
scenari, stime da vendite e simulazioni leggono la specifica del prodotto; i
consumi delle produzioni fisiche storiche continuano a leggere quella articolo
originale, mantenendo separati i due ambiti di identificazione.

## Migrazione V6

V6 aggiunge la linea canonica e il colore commerciale al prodotto, i campi di
catalogo mancanti, tre tabelle di revisioni e i riferimenti storici delle
configurazioni. Lo schema TON passa da 25 a 28 tabelle.

Per un prodotto con un'unica associazione in LINEE_PRODOTTI si conserva quella
linea. Con zero o più associazioni la linea rimane da specificare. Il colore
commerciale non viene dedotto dal codice. Le associazioni precedenti restano
conservate come dati legacy e sono mostrate nella scheda da completare; i nuovi
cataloghi usano solo PRODOTTI.linea_id.

La revisione iniziale registra i dati realmente disponibili e il contatore
precedente, con origine MIGRAZIONE. Non ricostruisce cambiamenti non registrati.
ID, codici, versioni tecniche, composizioni, documenti, vendite e UUID del DB
vengono conservati.

V1–V5 restano immutabili. L'HOST salva il backup prima delle scritture e applica
DDL e storico insieme; un errore annulla la migrazione. I CLIENT controllano
V6 in sola lettura, senza migrare durante il failover. La normale release firmata
dei componenti distribuisce il passaggio, senza sostituire Java o launcher.
Per l'aggiornamento chiudere le istanze e riavviare prima l'HOST aggiornato.

## Organizzazione e asincronicità

Le implementazioni precedenti del catalogo prodotti sono sostituite da
features/catalog, condiviso dai tre cataloghi:

- model: valori immutabili, gerarchia, bozze, filtri e risultati paginati.
- repository: SQL qualificato e snapshot tipizzati; nessuna connessione propria.
- service: transazioni, controllo concorrente, validazioni e ordine dei blocchi.
- controller: ciclo di vita, ricerca con debounce, letture superate e comandi.
- view: elenco, editor, campi, collegamenti, storico e specifiche.

I servizi usano provider ed executor di EcoS. Le letture vengono ignorate quando
superate o dopo la chiusura; i listener e i timer vengono rilasciati. Le scritture
in corso terminano senza essere interrotte o ripetute automaticamente. Durante
il salvataggio sono disabilitati la pagina e la navigazione del modulo.
Le modifiche non salvate richiedono conferma prima di essere scartate; un conflitto
conserva il testo locale e offre il caricamento esplicito della scheda corrente.
