# Costi e consumi — Polveri e prezzi

La Home EcoS scopre il modulo `orodent-costi` tramite lo stesso contratto dei moduli
TON e Statistiche. La prima sezione operativa è **Polveri e prezzi**; Panoramica,
Costi prodotti, Scenari e Consumi e fabbisogni sono ancora in preparazione.

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
aggiornamento automatico del magazzino. Scenari, calcolo costi articoli, arrivi
futuri e consumi saranno implementati separatamente.

## Design — Costi prodotti ed esportazione

La prossima sezione prevista è **Costi prodotti**: elenco prodotti per linea e
colore, tabella degli articoli/formati del prodotto selezionato e dettaglio delle
polveri. Un prodotto può avere costi diversi per diametro, altezza e configurazione
di composizione/modello. Devono essere visibili le revisioni utilizzate.

Il calcolo deve distinguere costo polveri, maggiorazione perdite (fattore 1,005,
applicato una sola volta), trasformazione in EUR/articolo e totale. Si prevede un
valore di trasformazione del prodotto con eventuale eccezione per articolo.
Prezzi o configurazioni mancanti producono uno stato **Incompleto**, senza un
totale che faccia sembrare completo un calcolo parziale.

### Esporta CSV

Un pulsante **Esporta CSV** apre un pannello con:

1. **Prospetto:** Articoli e costi / Dettaglio polveri.
2. **Ambito:** Prodotto selezionato / Tutti i prodotti filtrati.
3. Anteprima delle prime righe, colonne e numero totale di righe da esportare.
4. **Salva CSV**, scelta del percorso e conferma se il file esiste già.

Il riepilogo contiene una riga per articolo e configurazione valutata: codice
articolo, linea, prodotto, colore, diametro, altezza, composizione e revisione,
modello e revisione, peso miscela, fattore perdite, costo polveri, trasformazione,
costo totale e stato di completezza. Le unità appartengono alle intestazioni.

Il dettaglio contiene una riga per articolo/configurazione, strato e polvere:
identificativi dell'articolo e delle revisioni, numero strato, codice polvere,
revisione tecnica utilizzata, percentuale, grammi necessari inclusa maggiorazione,
prezzo EUR/kg applicato, modalità e revisione economica, data del prezzo e costo
della riga. Il riepilogo non somma costi di configurazioni alternative fra loro.

L'esportazione usa una fotografia degli stessi risultati e parametri mostrati al
momento della richiesta, senza ricalcolarli con prezzi cambiati nel frattempo.
L'ambito filtrato comprende tutte le pagine. Il dettaglio resta riconducibile
alle singole revisioni dei prezzi delle polveri, che possono avere date diverse.
Valori mancanti: celle vuote e stato esplicito; valori zero: numero zero.
I numeri esportati mantengono la precisione di calcolo, anche se la UI mostra
importi arrotondati. Nessun simbolo monetario nelle celle numeriche.

Il [motore CSV condiviso](csv-export.md) è già implementato in `orodent-core`, con
la stessa separazione reader/writer, mapper e orchestrazione dell'importazione di
Statistiche. Pannello, mapper specifici e calcolo Costi prodotti saranno collegati
quando implementeremo questa schermata. L'esportazione non salva né modifica
ricette, prezzi o scenari.

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
