# Editor visuale dei modelli

In **TON → Documenti → Modelli di documento**, **Nuovo modello** apre un foglio bianco. L’editor non aggiunge intestazioni aziendali, righe decorative o piè di pagina. I modelli si salvano e si versionano nel database; non sono presenti funzioni di importazione o esportazione JSON.

## Pagina

Nel pannello sinistro, scheda **Pagina**, scegli:

- Utilizzo: foglio operativo di produzione oppure etichetta dischi. Dopo il primo salvataggio il tipo non cambia.
- A4 verticale/orizzontale oppure formato personalizzato con larghezza e altezza in millimetri.
- Margine di riferimento per il posizionamento iniziale dei nuovi oggetti.
- Numero di pagine per i documenti. Un’etichetta occupa una sola pagina.

Un foglio di 102 × 38 mm mantiene queste proporzioni nell’editor e nell’anteprima. Lo zoom ingrandisce la vista e non modifica le misure del documento. Maniglie e targhette mantengono una dimensione compatta sullo schermo: lo zoom del foglio non ingrandisce i controlli di selezione.

I pulsanti **Inserisci**, **Annulla/Ripeti**, **Verifica** e **Anteprima** sono sulla
stessa barra del titolo; la barra si adatta alle finestre più piccole.

## Oggetti

Inserisci riquadri di testo, immagini, simboli, GS1 DataMatrix, tabelle e blocchi automatici. Ogni oggetto ha coordinate e dimensioni proprie, indipendenti dagli altri elementi.

- Trascina la maniglia sopra l’oggetto per spostarlo. Immagini, dati automatici, codici e blocchi automatici si possono trascinare anche dal contenuto.
- Sul foglio vuoto il puntatore è una freccia; il cursore di testo compare dentro i riquadri di testo.
- Trascina sul foglio vuoto per disegnare un rettangolo: gli oggetti completamente racchiusi vengono selezionati. Puoi trascinare in entrambe le direzioni, anche con lo zoom.
- **Ctrl + clic** aggiunge o toglie elementi dalla selezione (Cmd su macOS).
  Funziona anche con un gruppo intero. Maiusc + clic rimane disponibile;
  all'interno di un testo in modifica, Maiusc mantiene la selezione del testo.
- Dopo una selezione con il rettangolo o con Ctrl + clic, premi **Raggruppa**
  nelle proprietà a destra. Il gruppo viene conservato nel modello: un clic
  su un membro seleziona tutto il gruppo e il trascinamento lo sposta insieme.
  **Separa gruppo** rende di nuovo indipendenti gli elementi. Entrambe le
  operazioni si possono annullare e ripetere. I gruppi contengono elementi
  della stessa pagina; raggruppando gruppi esistenti si forma un unico gruppo.
  Le proprietà mostrano X/Y del gruppo e le dimensioni complessive; queste
  ultime sono di sola lettura.
- **Maiusc + clic** aggiunge o rimuove un oggetto dalla selezione. **Maiusc + rettangolo** aggiunge altri oggetti.
- Trascina un elemento selezionato per spostare la selezione multipla, mantenendo le distanze e i limiti della pagina. Frecce e Maiusc + frecce agiscono sull’intera selezione. **Elimina selezionati** o Canc eliminano soltanto gli oggetti selezionati; Annulla ripristina l’operazione.
- Esc durante il trascinamento annulla il gesto. **Deseleziona** o un clic sul foglio vuoto cancellano la selezione.
- Trascina i bordi o gli angoli di un singolo oggetto per ridimensionarlo.
- Nel pannello destro inserisci **X, Y, larghezza e altezza** in pixel. Il riferimento è l’angolo superiore sinistro della pagina.
- Con l’oggetto selezionato, le frecce spostano di **1 px**; **Maiusc + frecce** spostano di **10 px**.
- Entra nel testo con un clic nel riquadro o con Invio. Esc torna alla selezione dell’oggetto.
- **Annulla/Ripeti** conserva anche spostamenti e ridimensionamenti. Un trascinamento costituisce una singola operazione.
- **Duplica**, **In primo piano**, **Sullo sfondo** ed **Elimina oggetto** si trovano nelle proprietà.

I pixel sono quelli di riferimento CSS, 96 per pollice: 1 px corrisponde a 25,4/96 mm. Il pannello mostra anche l’equivalente fisico del riquadro. Il rapporto con lo zoom e con la risoluzione della stampante non modifica le coordinate salvate.

Gli oggetti rimangono entro la pagina. Se un nuovo formato non contiene gli oggetti esistenti, l’editor segnala il problema e ripristina i valori precedenti; puoi prima spostarli o ridimensionarli.

## Allineamento e snap

Il pulsante **Snap**, vicino a **Deseleziona**, attiva o disattiva le guide di
allineamento. All’apertura è spento. Durante il trascinamento aggancia bordi e
centri degli altri elementi della stessa pagina, oltre a bordi, margini e centro
del foglio. Le guide rosa compaiono soltanto vicino a un riferimento (6 pixel
sullo schermo, indipendenti dallo zoom) e scompaiono al rilascio.

Funziona anche sugli spostamenti di selezioni multiple contenute nella stessa
pagina, conservando le distanze fra gli oggetti. Nei ridimensionamenti liberi
aggancia il bordo trascinato; i ridimensionamenti con proporzioni bloccate
mantengono il rapporto originale. **Alt** sospende lo snap durante il gesto.
Le frecce restano spostamenti precisi di 1 o 10 pixel, senza snap. Attivare lo
snap non modifica il modello: cambia soltanto il comportamento dell’editor.

Un gruppo offre i quattro bordi esterni e il centro del suo rettangolo
complessivo. I bordi e i centri dei singoli membri non sono riferimenti di
snap. Spostando un gruppo o una selezione multipla puoi allinearne anche il
centro al foglio o agli altri elementi. Il contorno comune resta visibile
anche selezionando altri elementi insieme al gruppo.

## Testo e contenuti variabili

Selezionando un riquadro di testo, sulla destra compaiono stile del paragrafo, carattere, dimensione, grassetto, corsivo, sottolineatura, colori, allineamento, elenchi e interlinea. Con una porzione di testo selezionata puoi formattare quella porzione; selezionando l’intero oggetto modifichi il riquadro.

Nel pannello **Dati automatici**, codice articolo, lotto, GTIN e gli altri valori
sono oggetti autonomi: non si può scrivere prima o dopo il dato. La maniglia e le proprietà mostrano il nome del dato effettivo, ad esempio **Codice articolo** o **Lotto di cottura**. Il contenuto parte
dall’angolo superiore sinistro del riquadro, senza margini di paragrafo. Le proprietà
consentono di cambiare carattere, dimensione, colore, grassetto, corsivo e
sottolineatura; **Adatta al dato** calcola larghezza e altezza usando il valore
visualizzato. I valori si aggiornano dal lavoro alla generazione dell’etichetta.

I normali riquadri di testo restano disponibili per le scritte libere. I campi
operativi dei documenti di produzione possono ancora essere inseriti nel testo;
i dati dei blocchi continuano a provenire dal piano, senza alterare pesi o calcoli.

Tra i simboli sono disponibili anche **# · Numero modello**, **Tenere all’asciutto**
(ombrello) e **Data di scadenza** (clessidra), oltre a quelli già presenti.
I simboli della libreria non hanno margini trasparenti esterni e vengono
inseriti con le loro proporzioni: i bordi usati dallo snap coincidono con i
limiti visibili del simbolo. Il blocco delle proporzioni vale anche quando
modifichi larghezza o altezza nei campi numerici. Aprendo una bozza precedente in modifica, il
riquadro viene adattato senza spostare o ingrandire il disegno visibile.
Le versioni approvate e i documenti già emessi restano invariati. Le immagini
caricate dal PC non vengono ritagliate automaticamente.

Il riquadro dei nuovi **GS1 DataMatrix** coincide con il codice visibile. Lo
spazio bianco necessario alla lettura, pari ad almeno un modulo, viene
disegnato all'esterno del riquadro anche in stampa. La verifica di stampa
controlla che anche questo spazio resti entro la pagina e l'area stampabile.
Aprendo in modifica una
bozza precedente, il riquadro si restringe sul codice conservandone posizione
e dimensione visibili. Le versioni approvate conservano il rendering precedente.

**Adatta al dato** imposta larghezza e altezza usando il valore visualizzato,
senza cambiare il carattere. Nell’editor quel valore è un esempio: un valore
reale più lungo può richiedere più spazio. **Adatta altezza** mantiene la
larghezza e modifica soltanto l’altezza per contenere il contenuto.
L’**evidenziatore** imposta il colore di sfondo dietro al testo selezionato;
non cambia il colore delle lettere. È disponibile sul testo libero, insieme
agli elenchi e agli altri strumenti di paragrafo.

Un contenuto troppo grande viene segnalato nelle proprietà. Puoi aumentare il riquadro, adattarne l’altezza o ridurre il testo. La stampa dei nuovi modelli controlla che gli oggetti e il loro contenuto rientrino nel formato, per evitare tagli silenziosi. I dati reali possono occupare più spazio rispetto agli esempi: verifica anche l’anteprima del documento generato.

## Versioni precedenti

I modelli approvati e i documenti già emessi conservano il contenuto originale. Quando apri una bozza precedente per modificarla, l’editor racchiude i blocchi esistenti in oggetti posizionabili; le sezioni a colonne vengono mantenute come gruppi. I riquadri contenenti unicamente un campo etichetta vengono convertiti in dati automatici autonomi nelle bozze modificabili, conservandone posizione e stile; i testi misti restano conservati. Controlla la disposizione prima di approvare la nuova versione.

Per etichette, GTIN, immagini dal PC e collegamenti alle specifiche di produzione, vedi [Etichette dei dischi](ton-etichette.md).
