# Scenari di costo

Aprire **Costi e consumi → Scenari**. Il modulo usa dati reali acquisiti in una
fotografia e applica ipotesi indipendenti: non aggiorna TON, magazzino, ordini,
prezzi reali o parametri di trasformazione reali.

## Base e ambito

**Nuovo scenario** richiede nome e periodo storico (fino a ieri, massimo tre anni).
La lettura JDBC è una sola transazione serializzabile in sola lettura. Conserva
catalogo tecnico, configurazioni approvate, revisioni di composizioni/modelli,
misure articolo, prezzi e valutazioni economiche, lotti e ordini residui, parametri
di trasformazione, quantità vendute/prodotte e relativi avvisi di collegamento.
Le basi sono indipendenti dai record TON: una modifica successiva o un'archiviazione
non cambia un confronto salvato.

In **Base reale** scegliere i prodotti coinvolti. Inizialmente sono inclusi i
prodotti attivi; il selettore permette di cercare linea/prodotto/colore e includere
o escludere tutte le righe visibili. Per ciascun prodotto si considerano tutte le
anagrafiche articolo, incluse le archiviate, usando la sola **configurazione
predefinita approvata nella fotografia**. Una predefinita mancante resta incompleta,
non viene sostituita automaticamente con la revisione più recente.

Il selettore Semplice/Avanzata e l'opzione degli ordini rivalutano i dati già
acquisiti. La conferma precisa che non si tratta di una nuova lettura. Gli aumenti
percentuali lavorano sul prezzo applicato risultante da questa valutazione, non
sui singoli prezzi di acquisto dei lotti. La fonte resta identificabile nel confronto.

Le date nella base indicano il periodo della prossima acquisizione: diventano
parte della fotografia soltanto premendo **Aggiorna base** e confermando. I volumi
pesano gli articoli, non stimano automaticamente consumi o nuove giacenze.

## Modifiche: Applica prima di Salva

Le quattro schede hanno buffer separati dall'ipotesi applicata. Nei prezzi polveri
il tipo di variazione e il valore sono controlli sempre visibili: scegliere
Invariato / Variazione % / Prezzo EUR/kg e digitare direttamente. L'unità cambia
con il tipo; Invariato disabilita il valore. Passando al prezzo diretto si propone
il prezzo risultante dal buffer corrente; tornando alla percentuale si riparte da
zero, senza interpretare un prezzo come percentuale. Le altre tabelle si modificano
con doppio clic; Invio o uscita dal campo confermano la cella.
**Applica ... allo scenario** valida e aggiorna l'anteprima. Finché una scheda ha
modifiche non applicate, salvataggio ed esportazione sono disabilitati. Cambiando
scheda/origine si può annullare o scartare il buffer, senza sostituire dati alla cieca.

- **Prezzi polveri:** Invariato / Variazione % / Prezzo EUR/kg. Ricerca per codice
  o nome, filtro colore e variazione di gruppo sulle sole righe visibili. L'aumento
  è sempre relativo alla base, non cumulativo. Ripristino della singola polvere o
  di tutti i prezzi. Un prezzo assoluto può completare la sola ipotesi quando il
  prezzo base manca: il delta rispetto alla base resta non determinabile.
- **Ricette multistrato:** scegliere la composizione di partenza tra quelle usate
  dalle predefinite dell'ambito. Il pannello elenca gli strati reali, non un numero
  fisso. Modificare percentuali, aggiungere/rimuovere polveri, copiare una miscela
  da uno strato, aggiungere/rimuovere/riordinare strati. Zero rimuove l'ingrediente;
  ogni strato applicato deve arrivare al 100%, senza duplicare la stessa identità
  di polvere. Le nuove polveri usano la revisione approvata presente nella fotografia;
  gli ingredienti originali mantengono le revisioni congelate. Si può caricare una
  composizione alternativa approvata dello stesso prodotto e adattarne la copia.
- **Modelli disco:** copia locale o alternativa approvata, coefficiente g/mm,
  proporzioni nominali degli strati, sovramateriali superiore/inferiore e fasce di
  altezza. Le fasce sono ordinate senza sovrapposizioni, Da incluso/A escluso;
  fuori dalle fasce valgono i sovramateriali comuni. Il totale delle proporzioni
  deve essere 100%, g/mm positivo, sovramateriali non negativi. Questo editor tratta
  i parametri usati dal calcolo economico; non cambia parametri delle presse reali.
- **Trasformazione:** comune per prodotto ed eccezioni per articolo. Un'eccezione
  vuota eredita la comune; zero è esplicito. Le eccezioni originarie restano visibili
  e conservate finché non vengono cambiate o rimosse.

**Configurazioni coinvolte** elenca articoli e revisioni predefinite interessati
nell'ambito scelto. Una modifica a una composizione o a un modello condiviso vale
per le predefinite che referenziano quella stessa origine nei prodotti selezionati.
Non è una nuova approvazione tecnica. Le copie hanno stato locale `SCENARIO`.

Una ricetta e un modello con numero di strati incompatibile danno risultati
incompleti. Si può conservare la bozza per completare l'altra scheda, senza
inventare quantità o costi. Ripristina originale elimina l'override locale.

## Anteprima e confronto

L'anteprima mostra un solo articolo: **Linea → Prodotto → Articolo/formato**,
con ricerca digitabile nei selettori. **Trova articolo per codice** cerca almeno due
caratteri e visualizza al massimo 100 risultati; selezionandone uno imposta anche
linea e prodotto. La scelta resta stabile durante le modifiche.

Mostra Base e Scenario affiancati, totale per articolo, delta, miscela nominale
EUR/kg, grammi e spessore totale. Prezzi a due decimali e variazioni a un decimale
rendono leggibile la sintesi; tooltip, dettagli e CSV conservano la precisione.
Il pulsante Nascondi elenco / Mostra elenco scenari permette di recuperare spazio
senza cambiare scenario né perdere modifiche. **Dettaglio per strato** apre riepilogo, grammi per strato e
contributi di ciascuna polvere; doppio clic sulla polvere mostra la provenienza dei
prezzi. Il calcolo di massa riusa `ProductCostCalculator`: percentuali sull'altezza
nominale, sovramateriali agli strati esterni, perdite 1,005 una sola volta. Il
nominale EUR/kg esclude sovramateriali e perdite. Le pressioni non cambiano i costi.

Il confronto parte dalle **Linee**; doppio clic o Apri gruppo scende ai **Prodotti**
e poi agli **Articoli**. Il percorso in alto permette di risalire. I pulsanti
nell'anteprima aprono direttamente il confronto della linea o del prodotto scelti.
Filtro Solo variati e ordine per nome/delta EUR/delta %. Ogni riga mostra due
barre orizzontali con entrambi i prezzi affiancati e delta EUR/%. Le barre hanno
la stessa scala per tutti i risultati filtrati, anche fra pagine. Si mostrano
12 righe per pagina con navigazione esplicita: nessun risultato è scartato.
Le righe incomplete restano visibili con valori sconosciuti, senza inventare zeri.
Clic sul nome apre il gruppo o il dettaglio articolo. La tabella completa è
richiudibile sotto Mostra dettagli del confronto.

I tre riquadri Base media / Scenario media / Variazione riassumono l'ambito e il
filtro visualizzati, considerando tutte le pagine. Le medie sono calcolate sugli
articoli sottostanti con la ponderazione scelta, mai facendo la media delle medie
di linea o prodotto. La copertura indica articoli confrontabili e, per le medie
storiche, quanti hanno volumi. Periodo e fonte restano visibili; le spiegazioni
del calcolo sono nel pulsante Come leggere il confronto e nel tooltip della fonte.

Tre ponderazioni:

- **Media per articolo:** stesso peso 1 a ogni articolo confrontabile.
- **Vendite:** quantità positive di righe VENDITA, nel periodo acquisito. Resi e
  rettifiche non entrano nel peso. Eventuali codici non collegati sono segnalati.
- **Produzioni:** quantità prodotte positive delle sole produzioni CONFERMATE,
  collegate alle identità articolo tramite le configurazioni di produzione.

Per ogni gruppo si usano solo coppie con entrambi i costi totali calcolabili. Per
le ponderazioni storiche, peso zero esclude la riga; in assenza di volumi non si
inventa una media. Copertura indica articoli confrontabili, articoli con peso e
somma dei pesi. Base media e Scenario media sono `somma(costo × peso)/somma(peso)`.
Il delta percentuale è la variazione del costo medio (`(sommaScenario − sommaBase)
/sommaBase × 100`), **non** la media aritmetica delle percentuali degli articoli.
Se il denominatore è zero la percentuale resta sconosciuta. Non viene dichiarato
un risparmio aziendale assoluto senza un piano e quantità esplicite.

## Salvataggi, storico e aggiornamento

**Salva bozza** aggiunge una revisione e usa controllo ottimistico. Un conflitto
non sovrascrive dati: la bozza rimane nell'editor, per ricaricarla o duplicarla.
**Duplica** conserva base e ipotesi in un nuovo scenario. **Duplica da storico**
apre una revisione precedente come nuova bozza, senza riscrivere lo storico.
**Archivia/Ripristina** sono reversibili e versionati. La ricerca negli scenari e
l'opzione Includi archiviati facilitano la consultazione. Uscita, cambio scenario
e chiusura proteggono le modifiche non salvate.

**Aggiorna base** legge una nuova fotografia e mostra le differenze prima di
applicarla. Le variazioni percentuali si ricalcolano sulla nuova base; prezzi
assoluti e modifiche compatibili vengono mantenuti. Se un'origine tecnica o una
trasformazione modificata è cambiata/scomparsa o non è più usata dalle predefinite,
l'aggiornamento viene bloccato con l'elenco dei conflitti. La vecchia base rimane
intatta. Ripristinare le modifiche indicate nelle rispettive schede e riacquisire,
oppure creare uno scenario indipendente. Non si trasferisce un override a un'altra
polvere, composizione, modello o strato per somiglianza del nome o posizione.
Salvare dopo l'aggiornamento conserva la nuova base in una nuova revisione.

## CSV

Salvare la bozza prima di esportare, per identificare la revisione esatta.

- **Questa vista:** gruppi/articoli visualizzati, rispettando percorso, filtro e ordine.
- **Tutti gli articoli:** intero ambito dello scenario, incluse righe incomplete.
- **Dettaglio articolo:** materiali e strati di BASE e SCENARIO, con prezzi,
  quantità e provenienza. Selezionare un articolo nel confronto o nell'anteprima.

Il componente CSV condiviso mostra anteprima e conteggio e scrive sui worker:
UTF-8/BOM, `;`, virgola decimale, numeri non arrotondati alla presentazione. Il CSV
riporta identità/revisione dello scenario, base, fonte prezzi, periodo, ponderazione,
metriche e copertura; le righe articolo includono revisioni tecniche e grammi.
Valori mancanti restano vuoti. Non si esportano vecchi risultati durante un calcolo
o mentre esistono modifiche non applicate/non salvate.

## Persistenza e prestazioni

La V11 aggiunge solo `COSTI.SCENARI`, `SCENARI_BASI` e `SCENARI_REVISIONI`.
Una base immutabile è conservata una sola volta e condivisa dalle revisioni e dalle
copie che la usano. Le revisioni contengono JSON degli override, metadati e riferimento
alla base; non duplicano tutta la fotografia a ogni salvataggio. JSON formato 1,
BigDecimal e adattatori espliciti per date/istanti, limite di dimensione e nessuna
Java Object Serialization. Non ci sono FK verso dati TON correnti: la fotografia
resta consultabile indipendentemente dalle modifiche alle anagrafiche reali.

L'HOST applica backup e migrazione con il normale aggiornamento dei componenti;
i CLIENT verificano la compatibilità senza DDL. Il runtime già distribuito con
Gson usato da TON è sufficiente. Le librerie dei componenti includono la dipendenza.
JDBC e calcoli completi usano i worker condivisi, con invalidazione dei risultati
obsoleti; la chiusura non arresta servizi altrui. Il calcolo attraversa gli articoli
una volta, non esegue query per riga. Tabelle virtualizzate e grafico limitato a 12
categorie mantengono gestibile la consultazione di cataloghi grandi.

Il prototipo in `prototypes/scenari.html` resta una proposta grafica separata;
la funzione effettiva è JavaFX e usa i dati reali tramite i servizi sopra descritti.
