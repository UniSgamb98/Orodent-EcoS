# Cataloghi TON: Linee, Prodotti tecnici e Articoli

Aprire **TON → Dati tecnici → Linee / Prodotti tecnici / Articoli**.
Gli stessi cataloghi sono raggiungibili dalla Panoramica.

La gerarchia è **Linea → Prodotto → Articolo**:

- Una linea identifica una tecnologia; comprende codice, nome, tecnologia,
  eventuale materia prima e note.
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

## Elenchi e collegamenti

Gli elenchi cercano codice o descrizione e sono paginati in gruppi di 50.
Percentuale, underscore e barra inversa nella ricerca vengono trattati come
caratteri letterali. Le colonne sono ordinate dal DB per codice.

- Prodotti: filtro per linea.
- Articoli: filtri per linea, prodotto, diametro e altezza.
- **Mostra anche archiviati** comprende gli elementi disattivati.
- **Apri scheda** apre l'editor completo. Tornando all'elenco si mantengono
  filtri e pagina; selezionare una riga non sostituisce un editor aperto.

I selettori dei padri hanno una ricerca dedicata e mostrano al massimo 50
risultati per volta, più l'eventuale valore già selezionato. Non caricano l'intero
catalogo. Per aggiungere un nuovo figlio occorrono padri attivi e completi.
Un padre archiviato già assegnato resta visibile, senza essere proposto per nuove
associazioni.

La scheda **Prodotti tecnici** della linea elenca i prodotti collegati e permette
di crearne uno con la linea già selezionata. La scheda **Articoli** del prodotto
fa lo stesso per i formati. **Apri linea / Apri prodotto** risale al padre.

## Anagrafiche e storico

**Crea** salva la prima revisione. **Salva revisione** registra i valori correnti
e conserva le revisioni precedenti nella stessa transazione. Un salvataggio senza
cambiamenti non aggiunge una revisione fittizia.

La scheda **Storico** è paginata e in sola lettura: mostra revisione, data,
operazione e valori completi, inclusi codice, attributi, note e stato.
I riferimenti ai padri conservano il codice disponibile al momento della revisione.
Le informazioni che non erano disponibili prima della migrazione non vengono inventate.

Il contatore per la concorrenza e lo storico sono legati: ogni scrittura del
catalogo passa dal servizio. Scritture SQL esterne che avanzano il contatore
senza registrare la revisione impediscono ulteriori modifiche tramite l'app
finché la discrepanza non viene verificata.

Identità strutturali già utilizzate non cambiano significato:

- Una linea usata mantiene tecnologia e materia prima già definite; i campi
  precedentemente mancanti si possono completare.
- Un prodotto usato mantiene linea e colore già definiti; i mancanti si possono
  completare dopo aver verificato le associazioni precedenti.
- Un articolo già configurato mantiene prodotto e formato.

Codice e nome della linea, codice e descrizione dell'articolo e le note restano
correggibili con storico. Nel prodotto codice e descrizione seguono le regole
di generazione descritte sopra. Per rappresentare
una tecnologia, combinazione o formato diverso si crea una nuova anagrafica.
Un codice non può duplicare quello di un elemento archiviato né riutilizzare
quello conservato nello storico di un'altra identità.

**Archivia** conserva revisioni e collegamenti; **Riattiva** ripristina lo stato
attivo. Il cambio non si propaga ai figli. Un figlio di un padre archiviato è
mostrato con **Padre archiviato** e non è disponibile per nuove configurazioni.

**Elimina** è abilitato soltanto per la prima revisione mai utilizzata e senza
collegamenti. Il servizio ricontrolla riferimenti correnti e storici nella stessa
transazione. Altrimenti si usa Archivia. Non esistono cancellazioni a cascata.

## Articoli e specifiche di produzione

Un articolo si salva anche senza composizione o modello: rimane **Da configurare**.
La scheda **Specifiche di produzione** gestisce i riferimenti versionati già
previsti dal DB, senza un catalogo principale separato per le configurazioni.

Occorrono una composizione approvata dello stesso prodotto, un modello approvato
e un riferimento al metodo di calcolo. Il riferimento identifica il metodo:
questa schermata non implementa il calcolo delle dosi o il flusso delle produzioni.
I [modelli disco](ton-modelli-disco.md) si creano nel catalogo dedicato;
il catalogo delle composizioni è un passaggio successivo. In assenza di una
composizione approvata l'anagrafica dell'articolo resta utilizzabile.

- **Salva bozza** crea o aggiorna una versione modificabile. È ammessa una sola
  bozza per articolo; la numerazione viene assegnata sotto blocco dell'articolo.
- **Salva e approva** verifica prodotto, stato dei riferimenti, numero e numeri
  degli strati, totali al 100%, polveri attive e revisioni di polvere approvate.
  Congela anche i riferimenti alle revisioni di linea, prodotto e articolo.
- La prima versione approvata diventa predefinita. **Imposta predefinita** sceglie
  un'altra versione approvata con storico completo e riferimenti ancora validi.
  Il cambio è registrato anche nello storico dell'articolo.
- Una versione approvata è di sola lettura. **Crea nuova versione** copia i valori
  salvati in una nuova bozza, conservando la precedente.
- **Ritira versione** conserva contenuti e collegamenti. Se era predefinita,
  l'articolo torna senza predefinita; le produzioni precedenti mantengono il loro
  riferimento preciso. È possibile ritirare anche dopo l'archiviazione.
- Le versioni e i selettori sono paginati; le ricerche dedicate individuano
  composizioni per numero di versione e modelli per codice.

Le configurazioni precedenti a V6 non ricevono retroattivamente snapshot
anagrafici inventati: sono mostrate come **da verificare**. Possono essere lette,
copiate e approvate in una nuova versione dopo aver completato le anagrafiche.

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
