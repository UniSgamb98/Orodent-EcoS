# Modelli disco

Le schede usano il [contenitore comune di Dati tecnici](ton-schede-tecniche.md).

Aprire **TON → Dati tecnici → Modelli disco**, oppure **Apri modelli disco**
dalla Panoramica TON. La scheda usa lo stesso DB, provider ed executor di EcoS.
La V19 collega gli stampi ai modelli senza assegnarli automaticamente ai modelli
già presenti. La V20 aggiunge i gruppi per diametro nominale dell’articolo.
Vedere [schema e migrazioni](database-schema.md#migrazione-dalla-versione-19-alla-20).

## Elenco e scheda

La pagina usa il [componente comune degli elenchi TON](ton-elenchi.md).

L'elenco mostra tutti i risultati filtrati in una sola tabella scorrevole,
con una sola riga per modello:
codice, diametri articolo e strati della versione più recente, ultima approvata ed eventuale
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

**Nuovo modello** prepara la prima versione. Sono richiesti codice, pressione e
almeno un gruppo con diametro articolo, stampo di riferimento e peso per
millimetro; i sovramateriali partono da zero.
Il codice è normalizzato in maiuscolo e identifica tutte le versioni del modello.
Dopo il primo salvataggio rimane fisso; per una diversa identità si crea un nuovo
modello. Il numero della versione viene assegnato dal servizio.

- **Parametri**: codice, pressione target in kg/cm² e note comuni; una o più
  righe con **diametro articolo → stampo di riferimento → g/mm**.
  **+ Aggiungi diametro** crea una riga indipendente; **Rimuovi diametro** la
  elimina dalla bozza, mantenendo almeno una riga. Il diametro fisico deriva
  dallo stampo registrato in [Macchinari e attrezzature](ton-macchinari.md);
  quello nominale è inserito esplicitamente. I valori non devono coincidere.
- **Strati**: percentuali di altezza, totale e anteprima in sezione. Aggiungere,
  rimuovere o spostare una riga conserva i valori delle altre. **Distribuisci
  equamente** richiede conferma e assegna l'eventuale residuo all'ultimo strato
  per ottenere esattamente 100%, con quattro decimali.
- **Sovramateriali**: valori superiore/inferiore predefiniti e fasce opzionali
  `[da, fino a)`. I limiti iniziali sono inclusi, quelli finali esclusi.
  Le fasce adiacenti sono ammesse; le sovrapposte sono rifiutate anche dal servizio.
  Fuori dalle fasce restano i default. Tutte le misure sono in mm.
- **Stampi ammissibili**: scelta manuale degli altri stampi utilizzabili per
  ciascun diametro articolo di questa versione. Il selettore in alto cambia il
  gruppo confrontato; ogni gruppo ha il proprio riferimento, sempre incluso. Nessuna
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

## Gruppi per diametro articolo

Ogni diametro compare una sola volta nella versione del modello. Accetta punto
o virgola, è positivo e usa al massimo due decimali, come il diametro dell’articolo.
Ogni gruppo ha un coefficiente g/mm positivo, fino a cinque decimali, e una
selezione indipendente di stampi. Cambiare i g/mm di un gruppo non modifica gli altri.
Pressione target, quote degli strati e sovramateriali, comprese le fasce per
altezza, restano comuni.

Per esempio, un solo modello può definire Ø articolo 98 → stampo Ø 99 → 25 g/mm
e Ø articolo 95 → stampo Ø 96 → 20 g/mm. Il calcolo sceglie il gruppo per
uguaglianza numerica esatta del diametro articolo: 98 e 98,00 coincidono, 97,99 no.
Non esiste ricerca del diametro più vicino. L’approvazione di una specifica con
un diametro non configurato viene rifiutata; produzione e costi segnalano la
configurazione mancante senza usare il primo coefficiente disponibile.

Produzione, costi e scenari usano i g/mm del diametro interessato. Negli scenari
si seleziona il diametro prima di modificare il coefficiente; gli altri gruppi
restano conservati anche nel salvataggio della fotografia economica.
L’associazione delle specifiche all’articolo rimane invariata in questo passo;
il trasferimento al prodotto tecnico sarà un intervento successivo.

## Stampi e scostamenti teorici

Il selettore di ogni riga in **Parametri** propone gli stampi attivi, senza una scelta iniziale.
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
Δ peso g/mm = scostamento × g/mm del gruppo selezionato
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

Le versioni precedenti alla V20 conservano diametro fisico e g/mm storici,
insieme agli eventuali stampi associati in V19. Non viene inventato il diametro
nominale dell’articolo. Restano consultabili e utilizzabili con il comportamento
precedente. La copia conserva questi dati e mostra il diametro articolo vuoto:
per salvare o approvare la nuova bozza bisogna compilarlo esplicitamente e
selezionare uno stampo reale se manca. La versione originale rimane invariata.

## Salvataggio e versioni

**Salva bozza** salva insieme parametri, gruppi per diametro, strati, fasce e
selezioni degli stampi. I parametri obbligatori
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
corrispondenti, totali validi e presenza del diametro articolo nei modelli con
gruppi espliciti. La produzione usa il coefficiente e il diametro dello stampo
del gruppo corretto; l’altezza continua ad appartenere all’articolo.

## Organizzazione del codice

`features/diskmodels` separa valori immutabili, SQL, transazioni, controller e viste.
Le tabelle sono `TON.MODELLI_DISCO`, `TON.MODELLO_STRATI`,
`TON.MODELLO_SOVRAMATERIALI`, `TON.MODELLO_DIAMETRI` e
`TON.MODELLO_DIAM_STAMPI`. `TON.MODELLO_STAMPO_RIF` e
`TON.MODELLO_STAMPI_AMMESSI` conservano le sole associazioni storiche V19.
Il salvataggio dei figli è atomico con i parametri. Le colonne storiche di
diametro e g/mm nella testata rispecchiano il primo gruppo, ma i calcoli dei
modelli V20 usano sempre il gruppo corrispondente al diametro articolo.
Contatori di modifica e blocchi impediscono sovrascritture di versioni concorrenti.

Ricerche con debounce, letture e pagine superate vengono rilasciate alla chiusura.
Ogni risultato verifica che la richiesta sia ancora attuale prima di aggiornare
JavaFX. Nessuna query o transazione viene eseguita sul thread grafico.
