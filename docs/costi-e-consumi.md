# Costi e consumi

La Home EcoS scopre il modulo `orodent-costi` tramite lo stesso contratto dei moduli
TON e Statistiche. Sono operative **Polveri e prezzi**, **Costi prodotti** e
[Consumi e fabbisogni](consumi-e-fabbisogni.md). Panoramica e Scenari sono in preparazione.
La navigazione conserva i servizi condivisi e protegge le modifiche non salvate.

## Polveri e prezzi

La schermata usa un solo pannello di dettaglio. Ogni polvere TON compare una volta,
indipendentemente dal numero di revisioni della sua scheda tecnica. Le anagrafiche
si modificano in TON; qui si registrano soltanto dati economici e quantità dichiarate.

- **Vista semplice:** tutte le polveri, quantità totale e prezzo di riferimento €/kg.
  I dati mancanti rimangono sconosciuti, distinti dallo zero.
- **Vista avanzata:** tutte le polveri, con dettaglio facoltativo per lotto, quantità
  e prezzo di acquisto. Il lotto può ripetersi per acquisti con prezzi diversi.
- I lotti descrivono il totale dei dati base: **non assegnata = totale − somma lotti**.
  Il dettaglio mancante, parziale o eccedente è segnalato. Cambiare vista non modifica
  dati e non crea una revisione. **Annulla** ripristina la fotografia salvata.
- Il prezzo medio ponderato riguarda soltanto i lotti compilati: non sostituisce
  il riferimento: la fonte dei Costi prodotti si sceglie nel relativo selettore.
- La data è quella della fotografia della singola polvere. Quantità: 3 decimali;
  prezzi dei lotti: 4; riferimento: fino a 8 per conservare le medie pregresse.
  Virgola o punto decimale, senza separatori delle migliaia. Nessun arrotondamento degli input.

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
aggiornamento automatico del magazzino. Gli arrivi futuri sono separati dalle scorte dichiarate; gli scenari sono in preparazione.
I consumi sono consultabili in [Consumi e fabbisogni](consumi-e-fabbisogni.md).

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
stessa data. La fonte si sceglie con **Prezzi polveri** in Costi prodotti: riferimento semplice oppure valutazione avanzata. La vista aperta in **Polveri e prezzi** non cambia questa scelta.

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

## Ordini di approvvigionamento

Aprire **Ordini in arrivo**, oppure il pulsante in **Polveri e prezzi**.
**Importa CSV ordini** legge il formato del gestionale con separatore `;`, UTF-8
(anche BOM) o Windows-1252, date `gg/mm/aaaa`, quantità KG/KG. e prezzo netto €/kg.
Intestazioni richieste:

```text
Cons. prev.;Data ordine;Ordine;Pos.;Fornitore;Articolo;Descrizione;Un. Mis.;Quantita';UnitValue;Consegnata;Da consegn.
```

L'anteprima mostra tutte le righe valide e gli errori. Gli articoli vengono
riconosciuti per codice TON o collegamento fornitore già salvato. Se sconosciuti,
selezionare esplicitamente la polvere TON prima di confermare. Non vengono create
anagrafiche tecniche. Le associazioni sono conservate per le prossime importazioni.

La chiave è **fornitore + ordine + posizione**, non il lotto. Reimportare aggiorna
le righe cambiate e lascia invariate le identiche, senza duplicarle. Le righe
assenti dal file restano presenti. Tutta l'importazione è atomica e versionata;
se nel frattempo i dati cambiano occorre ricaricare l'anteprima.

**Data prevista / esclusione** consente una nuova data concordata o l'esclusione
dalle simulazioni. Queste scelte sopravvivono alle reimportazioni; selezionare
**Usa la data del CSV** per rimuovere la data manuale. Il residuo da ricevere è
`Da consegn.`: il consegnato non carica automaticamente il magazzino.
Il file ordini non contiene lotti; la futura importazione dei documenti d'acquisto
per compilare i lotti del magazzino rimane da definire con un esempio reale.

La V10 aggiunge tabelle ordini, associazioni fornitore e nuovi dati base magazzino.
I vecchi snapshot restano immutati; una nuova revisione inizializza i totali delle
vecchie modalità avanzate dalla somma dei lotti e il riferimento dalla loro media
ponderata. Backup e migrazione sono gestiti dall'HOST nel normale aggiornamento.

## Prezzo nominale della miscela

In **Costi prodotti** l'elenco segue l'ordine **Linea → Prodotto → Miscela €/kg**.
La tabella articoli e il CSV riportano lo stesso dato nominale per configurazione:
`somma(quota nominale strato × quota polvere nello strato × prezzo riferimento €/kg)`.
Entrambe le quote sono percentuali, quindi il prodotto viene diviso per 10.000.
Sono esclusi sovramateriali, maggiorazione perdite e trasformazione; diametro e
altezza non intervengono. Il costo **Polveri €** conserva il calcolo effettivo del
disco con sovramateriali e maggiorazione 1,005.

L'elenco usa le configurazioni predefinite approvate degli articoli del prodotto,
comprese quelle degli articoli archiviati. Se non è possibile determinare un dato
completo mostra `—`; se i prezzi nominali differiscono mostra **Più valori**.
Le configurazioni alternative rimangono distinguibili nella tabella articoli.

## Fonte dei prezzi: semplice e avanzata

Il selettore **Prezzi polveri**, sopra ai due pannelli di Costi prodotti, si applica
all'intero catalogo, al dettaglio e a tutte le esportazioni. Cambia la valutazione,
non i dati salvati del magazzino o i parametri di trasformazione. Si può cambiare
fonte anche durante la compilazione dei parametri, conservando la bozza.

- **Semplice · Riferimento**: prezzo dei dati base di ciascuna polvere.
- **Avanzata · Media ponderata**: valore dei lotti più valore della parte non
  assegnata, diviso per la quantità totale dichiarata. La parte non assegnata viene
  valorizzata al prezzo di riferimento. Con tutti i kg assegnati ai lotti non serve
  un riferimento per il calcolo avanzato.

Esempio: totale 100 kg, lotti 80 kg a 80 €/kg, riferimento 100 €/kg.
La media è `(80 × 80 + 20 × 100) / 100 = 84 €/kg`.
Il triangolo giallo accanto al prezzo, in **Dettaglio polveri**, segnala il ricorso
al riferimento per i kg non assegnati. Il tooltip lo spiega; al clic si apre il
popup con totale, kg nei lotti, kg non assegnati, valore e formula del calcolo.
Un avviso sopra ai pannelli apre anche il riepilogo delle polveri interessate.
Gli altri prezzi hanno un pulsante informativo per consultare la provenienza.

**Includi ordini in arrivo**, disponibile solo in avanzata e disattivato all'apertura,
aggiunge quantità **residue** e relativo valore di tutti gli ordini aperti con
consegna dalla data della fotografia in poi. Non c'è un orizzonte temporale di
costo: la media include tutti gli arrivi futuri scelti. Righe escluse, consegnate,
scadute o senza data non partecipano. Il popup distingue i singoli contributi,
riporta documento/posizione e revisione, e segnala righe scadute/senza data omesse.
Per includerle bisogna aggiornare la data in **Ordini in arrivo** e ricaricare.
La stima non carica il magazzino né altera gli ordini.

Totale sconosciuto, lotti oltre il totale, riferimento mancante per la parte non
assegnata o nessuna quantità positiva rendono il prezzo avanzato non calcolabile.
Non si sostituisce silenziosamente una media parziale al costo dell'intera scorta.
Scorta zero con un ordine futuro valido può essere valorizzata sul solo arrivo;
scorta sconosciuta resta sconosciuta anche in presenza di ordini.

La media è arrotondata a 8 decimali, dopo aver sommato i valori senza arrotondamenti
intermedi. Il costo nominale della miscela continua a escludere sovramateriali,
perdite e trasformazione. La sorgente compare anche nel CSV riepilogativo; il CSV
di dettaglio comprende flag di stima e spiegazione completa delle valutazioni.
I dati usati sono la fotografia acquisita con **Ricarica prezzi e dati**, mantenuta
immutabile: le variazioni in altri moduli richiedono una nuova lettura.
Questa politica di valutazione è riutilizzabile dai futuri scenari; qui non si
salvano scenari o prezzi alternativi.
