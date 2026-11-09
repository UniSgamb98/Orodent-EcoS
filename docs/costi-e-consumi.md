# Costi e consumi

La Home EcoS scopre il modulo `orodent-costi` tramite lo stesso contratto dei moduli
TON e Statistiche. Sono operative **Polveri e prezzi** e **Costi prodotti**.
Panoramica, Scenari e Consumi e fabbisogni sono ancora in preparazione.
La navigazione conserva i servizi condivisi e protegge le modifiche non salvate.

## Polveri e prezzi

La schermata usa un solo pannello di dettaglio. Ogni polvere TON compare una volta,
indipendentemente dal numero di revisioni della sua scheda tecnica. Le anagrafiche
si modificano in TON; qui si registrano soltanto dati economici e quantità dichiarate.

- **Semplice:** prezzo €/kg obbligatorio e positivo, quantità kg facoltativa.
  Una quantità assente rimane sconosciuta, distinta dallo zero.
- **Avanzata:** righe con lotto (facoltativo se sconosciuto), quantità kg e prezzo
  €/kg. Il lotto non è un identificatore univoco: lo stesso lotto può apparire
  in acquisti diversi, anche con prezzi diversi.
- Si conservano i dati di entrambe le modalità. Cambiare modalità non cancella
  l'altra compilazione; **Annulla** ripristina l'ultima fotografia caricata.
- La data **Dati della polvere al** appartiene alla polvere selezionata. Le altre
  polveri possono avere date diverse: non è una rivalutazione globale.
- Quantità: fino a 3 decimali; prezzi: fino a 4. Nei campi usare virgola o punto
  decimale senza separatori delle migliaia. Il salvataggio non arrotonda gli input.
- Quantità zero nei lotti consentita: una fotografia con totale zero non ha un
  prezzo medio ponderato calcolabile. Non si usa zero come prezzo mancante.

Il prezzo medio è `somma(kg × €/kg) / somma(kg)`. Gli importi si sommano in
`BigDecimal`, senza arrotondare ogni riga ai centesimi; il rapporto è conservato
nel modello applicativo con 8 decimali. Le etichette arrotondano soltanto la
presentazione. Le revisioni salvano sempre le quantità e i prezzi originari.

## Conservazione e ciclo di vita

La migrazione immutabile V7 crea lo schema `COSTI`, separato da TON e VENDITE.
`POLVERI_PREZZI` conserva una revisione immutabile per salvataggio e per identità
polvere; `POLVERI_LOTTI` conserva le righe di quella revisione. La schermata legge
l'ultima revisione. La coppia polvere/versione impedisce la sovrascrittura da parte
di due operatori concorrenti. Un conflitto conserva le modifiche nell'editor:
ricaricare prima di decidere cosa salvare. Le scritture non sono ritentate automaticamente.

Ricerche e salvataggi usano i worker EcoS, con paginazione e protezione dai risultati
obsoleti. Uscita verso Home e chiusura della finestra proteggono le modifiche non
salvate. La chiusura del modulo invalida le letture pendenti, senza arrestare i
servizi condivisi. Migrazione e backup sono gestiti dal normale avvio HOST EcoS;
i CLIENT controllano la compatibilità senza modificare lo schema.

È una **fotografia manuale**: nessun carico da ordini, scarico da produzione o
aggiornamento automatico del magazzino. Scenari, arrivi futuri e consumi saranno
implementati separatamente.

## Costi prodotti

Aprire **Home → Costi e consumi → Costi prodotti**. A sinistra si cercano i
prodotti per codice, linea o colore, con filtro di linea, pagine da 40 e opzione
per includere le anagrafiche archiviate. A destra ogni articolo/configurazione
mostra formato, grammi di miscela comprensivi delle perdite, costo polveri,
trasformazione, totale e stato di completezza.

Il catalogo usa normalmente le **configurazioni predefinite approvate**: non
sostituisce implicitamente una predefinita assente o ritirata con la versione più
recente. **Mostra tutte le configurazioni approvate** permette di confrontare le
alternative, mantenendole su righe distinte, senza sommarne i costi. Le revisioni
di composizione, modello e polvere rimangono quelle collegate alla configurazione.
Le misure provengono dalla revisione articolo congelata dalla configurazione;
per configurazioni precedenti allo storico si usano le misure correnti, con
indicazione esplicita nella scheda.

La tab **Dettaglio polveri** mostra strato, polvere e revisione tecnica, percentuale,
grammi da pesare, prezzo applicato e costo della riga. Indica modalità del prezzo,
revisione economica e data di riferimento. Non richiede che le polveri abbiano la
stessa data. Prezzi semplici e medie ponderate vengono dalla modalità attualmente
salvata per ciascuna polvere in **Polveri e prezzi**.

### Regola di calcolo COSTI-NOMINALE-1

Regola confermata dall'utente per questa implementazione:

1. Applicare le percentuali di altezza del modello all'altezza nominale articolo.
2. Aggiungere il sovramateriale superiore allo strato 1, quello inferiore all'ultimo.
   Nel monostrato aggiungerli entrambi. Le fasce `[da, fino a)` dell'altezza nominale
   sostituiscono i default del modello; fuori dalle fasce si usano i default.
3. Moltiplicare l'altezza risultante di ciascuno strato per il coefficiente g/mm.
4. Applicare il fattore **1,005 una sola volta** e distribuire la massa sulle
   polveri secondo le percentuali della ricetta dello strato.
5. Calcolare il costo di ogni polvere come `grammi / 1000 × prezzo EUR/kg`, poi
   aggiungere la trasformazione per articolo.

Si usano `BigDecimal` senza arrotondamenti intermedi. Il prezzo medio ponderato
mantiene gli 8 decimali del modulo Polveri e prezzi. L'arrotondamento alla bilancia
(0,1 g) appartiene al futuro foglio operativo di produzione, non viene introdotto
nel calcolo economico. Il riferimento al calcolo produttivo TON rimane distinto
da `COSTI-NOMINALE-1` ed è riportato nel CSV: questa sezione non emette produzioni.

Prezzi, configurazioni, strati o trasformazione mancanti producono **Incompleto**
e un totale vuoto. Non si normalizzano ricette errate e non si presenta una somma
parziale delle polveri come costo completo. Il costo polveri può essere noto anche
quando manca la trasformazione. I prodotti senza articoli rimangono riconoscibili
nell'esportazione generale, con riga incompleta e importi vuoti.

### Parametri di trasformazione

Compilare **Trasformazione comune** in EUR/articolo. Se serve, selezionare un
articolo e attivare **Trasformazione specifica per questo articolo**. L'eccezione
vale per le sue configurazioni; rimuoverla ripristina l'eredità dal prodotto.
Zero è ammesso e distinto da un valore sconosciuto. Sono accettati fino a 4
decimali, senza arrotondare gli input.

Le modifiche aggiornano l'anteprima. **Salva parametri** conserva una nuova
revisione economica di prodotto con tutte le eccezioni; **Annulla** ripristina
l'ultima revisione caricata. Navigazione e chiusura proteggono le modifiche non
salvate. Le scritture concorrenti producono un conflitto senza sovrascrivere le
modifiche di un altro operatore o ritentare il salvataggio automaticamente.

La V8 aggiunge `COSTI.PRODOTTI_PARAMETRI` e `COSTI.ARTICOLI_TRASFORMAZIONE`.
Non riscrive dati TON, prezzi polveri o magazzino. Il normale avvio HOST applica
backup e migrazione; i CLIENT verificano lo schema senza modificarlo. Sono
sufficienti i normali aggiornamenti firmati dei componenti.

### Esportazione CSV

**Esporta tutti i prezzi**, in alto a destra, esporta i costi dell'**intero catalogo**,
comprese anagrafiche archiviate, usando le configurazioni predefinite approvate.
Ignora la ricerca, la linea selezionata, la pagina e la spunta delle alternative.
Una riga corrisponde a un articolo/configurazione, non a una media dei formati del
prodotto. Anche prodotti senza articoli o articoli non calcolabili sono presenti,
con uno stato esplicito e senza costi inventati.

Il menu **Esporta CSV** permette inoltre:

- **Prodotto selezionato · Articoli e costi**;
- **Prodotto selezionato · Dettaglio polveri**;
- **Prodotti filtrati · Articoli e costi**, su tutte le pagine.

Questi tre comandi rispettano le opzioni di consultazione della scheda. Il
pannello condiviso mostra intestazioni, anteprima delle prime 8 righe e numero
totale di righe. **Salva CSV** apre la scelta del file con conferma di sostituzione;
il salvataggio avviene sui worker, con conteggio e percorso al termine. I file
usano UTF-8 con BOM, separatore `;`, virgola decimale, unità nelle intestazioni e
nessun simbolo monetario nelle celle numeriche. I numeri mantengono la precisione
di calcolo; i valori mancanti restano celle vuote, distinti dallo zero.

L'esportazione è abilitata dopo aver salvato o annullato le modifiche ai parametri.
Usa la stessa fotografia immutabile di prezzi e dati tecnici della schermata.
**Ricarica prezzi e dati** acquisisce una nuova fotografia. Il CSV riporta il
momento di lettura, metodo di calcolo, revisioni tecniche ed economiche; il dettaglio
riporta anche la data dei singoli prezzi.

Le letture sono raggruppate in una transazione in sola lettura con isolamento
serializzabile; si carica soltanto il catalogo tecnico necessario alle
configurazioni approvate, senza leggere vendite o produzioni. JDBC, calcoli e
scrittura CSV usano i worker condivisi, con protezione dai risultati obsoleti.
La chiusura invalida i callback senza arrestare i servizi di EcoS.

Il [motore CSV condiviso](csv-export.md) è in `orodent-core`; il pannello di
anteprima/salvataggio riutilizzabile è in `orodent-module-api`. I mapper specifici
rimangono nel modulo Costi. Nessuna esportazione modifica ricette, prezzi o scenari.

## TODO — Importazione CSV degli acquisti polveri

**Da implementare dopo aver ricevuto un documento reale di esempio.** Il pulsante
è indicato come in preparazione e non simula un'importazione già disponibile.

- [ ] Definire formato, intestazioni, separatori, codifica, unità, valuta e convenzioni
      di prezzo (netto, sconti e decimali).
- [ ] Collegare la tipologia/codice importato all'identità della polvere TON,
      gestendo codici sconosciuti senza creare anagrafiche o revisioni implicite.
- [ ] Importare lotto, quantità acquistata, prezzo e riferimento al documento/riga.
- [ ] Distinguere quantità **acquistata** nel documento e quantità **ancora disponibile**
      dichiarata dall'utente. Un acquisto storico non prova una disponibilità attuale.
- [ ] Conservare più acquisti dello stesso lotto, anche con prezzi diversi.
- [ ] Definire una chiave di provenienza documento/riga per riconoscere duplicati
      e reimportazioni; il solo lotto non basta.
- [ ] Prevedere anteprima con errori e scelta esplicita fra aggiungere o sostituire
      le quantità della fotografia, senza duplicarle implicitamente.
- [ ] Salvare tutta l'importazione scelta in transazione, senza alterare le
      fotografie economiche precedenti e senza introdurre movimenti automatici.

Questi punti sono un promemoria funzionale, non un tracciato CSV definitivo.
