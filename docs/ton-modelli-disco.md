# Modelli disco

Aprire **TON → Dati tecnici → Modelli disco**, oppure **Apri modelli disco**
dalla Panoramica TON. La scheda usa lo stesso DB, provider ed executor di EcoS.
Non serve una migrazione nuova: lo schema resta V6.

## Elenco e scheda

L'elenco è paginato per 50 codici e mostra una sola riga per modello:
codice, diametro/strati della versione più recente, ultima approvata ed eventuale
bozza. La ricerca tratta `%`, `_` e `\` come caratteri letterali. I filtri
permettono di cercare modelli con una bozza o con versioni approvate.

**Nuovo modello** prepara la prima versione. Sono richiesti codice, diametro di
pressatura, pressione e peso per millimetro; i sovramateriali partono da zero.
Il codice è normalizzato in maiuscolo e identifica tutte le versioni del modello.
Dopo il primo salvataggio rimane fisso; per una diversa identità si crea un nuovo
modello. Il numero della versione viene assegnato dal servizio.

- **Parametri**: geometria, pressione in kg/cm² come in TON V2, coefficiente g/mm,
  note tecniche. Il diametro di pressatura è distinto dal diametro nominale
  dell'articolo: non viene imposta un'uguaglianza non confermata sul processo.
- **Strati**: percentuali di altezza, totale e anteprima in sezione. Aggiungere,
  rimuovere o spostare una riga conserva i valori delle altre. **Distribuisci
  equamente** richiede conferma e assegna l'eventuale residuo all'ultimo strato
  per ottenere esattamente 100%, con quattro decimali.
- **Sovramateriali**: valori superiore/inferiore predefiniti e fasce opzionali
  `[da, fino a)`. I limiti iniziali sono inclusi, quelli finali esclusi.
  Le fasce adiacenti sono ammesse; le sovrapposte sono rifiutate anche dal servizio.
  Fuori dalle fasce restano i default. Tutte le misure sono in mm.
- **Utilizzi**: specifiche degli articoli collegate alla versione selezionata,
  con formato, stato e indicazione della predefinita. **Apri articolo** apre la
  scheda reale, anche per consultare un articolo archiviato.
- **Versioni**: storico paginato, stato e data. Aprire una riga carica la versione
  completa. Il selettore in alto mostra le prime 50 più recenti e quella aperta;
  le versioni più vecchie si raggiungono dalla tabella paginata.

L'anteprima usa colori illustrativi e sovramateriali schematici. Non calcola pesi
o altezze produttive. Il totale non valido viene segnalato; un totale inferiore
a 100% lascia una parte non assegnata, senza normalizzare le quote inserite.
La numerazione visuale procede dall'alto al basso: prima del futuro calcolo dosi
andranno confermate le convenzioni sugli strati estremi e sul monostrato.

## Salvataggio e versioni

**Salva bozza** salva insieme parametri, strati e fasce. I parametri obbligatori
devono essere validi anche in bozza; le quote degli strati possono essere ancora
da completare come totale. Ogni riga compilata richiede una percentuale positiva,
massimo 100%. Non si arrotondano automaticamente valori oltre la precisione DB.

**Salva e approva** richiede almeno uno strato e totale esatto 100%. La versione
approvata è di sola lettura, inclusi note e sovramateriali. **Crea nuova versione**
copia l'intero contenuto in una nuova bozza; è ammessa una sola bozza per codice.
Il controllo avviene nel DB sotto blocco della famiglia del modello, anche se due
postazioni chiedono la copia contemporaneamente. Un salvataggio identico senza
approvazione non incrementa inutilmente il contatore di modifica.

**Ritira versione** esclude un'approvata dai selettori per nuove specifiche,
conservandone contenuto e collegamenti. Non riscrive le specifiche già approvate.
**Elimina bozza** è consentito soltanto se mai utilizzata, ricontrollando i
riferimenti nella transazione e mantenendo i vincoli FK come ulteriore protezione.

Il cambio versione, l'uscita e la ricarica chiedono conferma se ci sono modifiche
non salvate. Durante le scritture sono bloccati editor e navigazione TON.
Un conflitto concorrente mantiene i campi locali e propone la ricarica esplicita.
Le scritture fallite o interrotte non vengono ripetute automaticamente.

## Collegamento agli articoli

Dentro **Articoli → Specifiche di produzione** sono selezionabili i modelli
approvati, con codice e versione. La specifica collega un `modello_id` preciso:
approvare v3 non sostituisce il modello v2 già usato da una specifica.
Il servizio delle specifiche verifica che composizione e modello abbiano strati
corrispondenti e totali validi. I parametri di produzione saranno usati dal futuro
flusso produttivo, senza spostare l'altezza dell'articolo nel modello.

## Organizzazione del codice

`features/diskmodels` separa valori immutabili, SQL, transazioni, controller e viste.
Le tre tabelle esistenti sono `TON.MODELLI_DISCO`, `TON.MODELLO_STRATI` e
`TON.MODELLO_SOVRAMATERIALI`. Il salvataggio dei figli è atomico con i parametri.
Contatori di modifica e blocchi impediscono sovrascritture di versioni concorrenti.

Ricerche con debounce, letture e pagine superate vengono rilasciate alla chiusura.
Ogni risultato verifica che la richiesta sia ancora attuale prima di aggiornare
JavaFX. Nessuna query o transazione viene eseguita sul thread grafico.
