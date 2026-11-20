# Modelli disco

Le schede usano il [contenitore comune di Dati tecnici](ton-schede-tecniche.md).

Aprire **TON → Dati tecnici → Modelli disco**, oppure **Apri modelli disco**
dalla Panoramica TON. La scheda usa lo stesso DB, provider ed executor di EcoS.
La V19 collega gli stampi ai modelli senza assegnarli automaticamente ai modelli
già presenti. Vedere [schema e migrazioni](database-schema.md#migrazione-dalla-versione-18-alla-19).

## Elenco e scheda

La pagina usa il [componente comune degli elenchi TON](ton-elenchi.md).

L'elenco mostra tutti i risultati filtrati in una sola tabella scorrevole,
con una sola riga per modello:
codice, diametro/strati della versione più recente, ultima approvata ed eventuale
bozza. La ricerca tratta `%`, `_` e `\` come caratteri letterali. I filtri
permettono di cercare modelli con una bozza o con versioni approvate.

L’elenco condivide pannelli bianchi, campi bordati e intestazioni azzurre con gli
altri cataloghi TON. **Aggiorna** è accanto alla ricerca; non ci sono pulsante
di apertura, frecce di pagina o contatore in basso. Le colonne occupano tutta
la larghezza in modo uniforme. Doppio clic o Invio aprono il modello.
Storico, versioni e riferimenti negli editor conservano la propria paginazione.

La scheda modello usa la stessa palette della scheda polvere: codice
nell'intestazione del modulo, **Torna ai modelli** a sinistra e **Aggiorna dati**
a destra. **Crea nuova versione** affianca il riquadro di stato, sopra le schede;
salvataggio e altre azioni sono raccolti nel pannello bianco inferiore.

**Nuovo modello** prepara la prima versione. Sono richiesti codice, stampo di
riferimento, pressione e peso per millimetro; i sovramateriali partono da zero.
Il codice è normalizzato in maiuscolo e identifica tutte le versioni del modello.
Dopo il primo salvataggio rimane fisso; per una diversa identità si crea un nuovo
modello. Il numero della versione viene assegnato dal servizio.

- **Parametri**: stampo di riferimento, pressione in kg/cm² come in TON V2,
  coefficiente g/mm e note tecniche. Il diametro deriva dallo stampo scelto in
  [Macchinari e attrezzature](ton-macchinari.md) e non è più un campo numerico
  modificabile. Resta distinto dal diametro nominale dell'articolo.
- **Strati**: percentuali di altezza, totale e anteprima in sezione. Aggiungere,
  rimuovere o spostare una riga conserva i valori delle altre. **Distribuisci
  equamente** richiede conferma e assegna l'eventuale residuo all'ultimo strato
  per ottenere esattamente 100%, con quattro decimali.
- **Sovramateriali**: valori superiore/inferiore predefiniti e fasce opzionali
  `[da, fino a)`. I limiti iniziali sono inclusi, quelli finali esclusi.
  Le fasce adiacenti sono ammesse; le sovrapposte sono rifiutate anche dal servizio.
  Fuori dalle fasce restano i default. Tutte le misure sono in mm.
- **Stampi ammissibili**: scelta manuale degli altri stampi utilizzabili per
  questa versione e confronto con il riferimento, sempre incluso. Nessuna
  tolleranza o regola seleziona automaticamente gli stampi.
- **Utilizzi**: specifiche degli articoli collegate alla versione selezionata,
  con formato, stato e indicazione della predefinita. **Apri articolo** apre la
  scheda reale, anche per consultare un articolo archiviato.
- **Versioni**: storico paginato, stato e data. Aprire una riga carica la versione
  completa. Tutte le versioni, anche le più vecchie, si raggiungono da questa
  tabella; non è presente un secondo selettore in alto.

L'anteprima usa colori illustrativi e sovramateriali schematici. Non calcola pesi
o altezze produttive. Il totale non valido viene segnalato; un totale inferiore
a 100% lascia una parte non assegnata, senza normalizzare le quote inserite.
La numerazione visuale procede dall'alto al basso. Per il calcolo economico
`COSTI-NOMINALE-1` è stata confermata la regola: percentuali sull’altezza nominale,
poi sovramateriale superiore al primo strato e inferiore all’ultimo; entrambi nel
monostrato. [Regola e calcolo costi](costi-e-consumi.md#regola-di-calcolo-costi-nominale-1).

## Stampi e scostamenti teorici

Il selettore in **Parametri** propone gli stampi attivi, senza una scelta iniziale.
In **Stampi ammissibili** ogni altro stampo ha una spunta indipendente, inizialmente
vuota. Cambiare riferimento non ammette automaticamente quello precedente.
**Aggiorna elenco stampi** rilegge il catalogo senza modificare le scelte.

La tabella mostra diametro, differenza di diametro, scostamento della sezione,
differenza di peso in g/mm e in g ogni 100 g di polvere di riferimento. Sono stime
geometriche a parità di altezza e densità, con segno positivo per la polvere in più
e negativo per quella in meno:

```text
rapporto sezioni = (diametro stampo / diametro riferimento)²
scostamento = rapporto sezioni − 1
Δ sezione % = scostamento × 100
Δ peso g/mm = scostamento × g/mm del modello
Δ peso g/100 g = scostamento × 100
```

Per esempio, da Ø 99 a Ø 99,2 mm con 25 g/mm: +0,4044% di sezione,
+0,10111 g/mm e +0,4044 g ogni 100 g. Il calcolo serve a decidere quali stampi
ammettere; **non corregge il coefficiente g/mm né le pesate della produzione**.
Non misura la densità reale del verde o i ritiri di cottura e non definisce
la compatibilità con le presse.

Ogni versione conserva ID, revisione, codice, nome e diametro degli stampi scelti.
Cambiare successivamente un'anagrafica non riscrive questi dati. Se lo stampo di
una bozza è stato aggiornato, prima di salvare occorre aggiornare l'elenco e
riselezionarlo esplicitamente: per gli stampi aggiuntivi togliere e rimettere la
spunta; per il riferimento scegliere la revisione corrente dal selettore.
Uno stampo archiviato non si può approvare in una nuova configurazione.

Le versioni precedenti alla V19 mantengono il diametro storico e nessuna
associazione inventata, anche se esiste uno stampo con lo stesso diametro.
Restano consultabili e utilizzabili. La loro copia conserva il valore storico,
ma per salvare o approvare la nuova bozza bisogna selezionare un riferimento reale.

## Salvataggio e versioni

**Salva bozza** salva insieme parametri, strati, fasce e selezione degli stampi. I parametri obbligatori
devono essere validi anche in bozza; le quote degli strati possono essere ancora
da completare come totale. Ogni riga compilata richiede una percentuale positiva,
massimo 100%. Non si arrotondano automaticamente valori oltre la precisione DB.

**Salva e approva** richiede almeno uno strato e totale esatto 100%. La versione
approvata è di sola lettura, inclusi note, sovramateriali e stampi. **Crea nuova versione**
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
Le tabelle sono `TON.MODELLI_DISCO`, `TON.MODELLO_STRATI`,
`TON.MODELLO_SOVRAMATERIALI`, `TON.MODELLO_STAMPO_RIF` e
`TON.MODELLO_STAMPI_AMMESSI`. Il salvataggio dei figli è atomico con i parametri.
Contatori di modifica e blocchi impediscono sovrascritture di versioni concorrenti.

Ricerche con debounce, letture e pagine superate vengono rilasciate alla chiusura.
Ogni risultato verifica che la richiesta sia ancora attuale prima di aggiornare
JavaFX. Nessuna query o transazione viene eseguita sul thread grafico.
