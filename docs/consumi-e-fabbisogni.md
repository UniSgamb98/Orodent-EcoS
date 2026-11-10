# Consumi e fabbisogni

Aprire **Home → Costi e consumi → Consumi e fabbisogni**. La sezione usa il DB
condiviso, i worker EcoS e il calcolo delle masse `COSTI-NOMINALE-1`. Nessuna
simulazione aggiorna disponibilità, prezzi, ricette, produzioni o vendite.

## Consumo globale

Selezionare un periodo storico concluso (massimo 3 anni), un orizzonte da 1 a 365
giorni e il calendario. Premere **Carica / aggiorna**. Cambiare periodo o calendario
invalida risultati ed esportazioni fino al nuovo caricamento.

Le basi disponibili sono **Da produzioni**, **Da vendite**, **Confronto**. Sono
previsioni alternative, mai sommate. Ogni curva rappresenta una singola polvere
**e una fonte**; le vendite hanno linea tratteggiata. I selettori Bianche, Gialle,
Rosa e Grigie nascondono/mostrano tutte le curve di quel colore, senza aggregarle.
La legenda permette anche di nascondere una sola polvere. I colori provengono da
`TON.POLVERI`, non dal colore commerciale del disco.

Il grafico può mostrare **Consumo** (kg del singolo giorno) o **Disponibilità**
(kg residui). Passare con il mouse su una curva mostra data esatta, codice polvere,
fonte, quantità e copertura. In confronto il popup mostra entrambe le fonti per
la polvere più vicina al puntatore. La linea verticale segue il giorno selezionato.
Il tracciato non viene ricalcolato a ogni movimento del mouse.

### Origine e qualità dei dati

- Produzioni: solo `TON.PRODUZIONI` confermate, con quantità delle righe e
  configurazioni collegate, comprese versioni successivamente ritirate. Bozze e
  annullate sono escluse. Le masse sono **teoriche**, non pesature registrate:
  non si attribuisce un significato arbitrario al CLOB `dosi_calcolate`.
- Vendite: righe `VENDITA`, collegate per codice articolo esatto e ricostruite con
  la configurazione predefinita **attualmente approvata**. Non è una ricetta storica
  della vendita. Resi e note di credito sono segnalati ed esclusi: non restituiscono
  automaticamente polvere utilizzabile. La stima considera vendite lorde.
- La massa usa percentuali sull'altezza nominale, sovramateriali sugli strati estremi,
  coefficiente g/mm e maggiorazione 1,005 una sola volta. Le revisioni tecniche sono
  quelle della configurazione; non occorrono prezzi o trasformazione per calcolare
  consumi e fabbisogni.
- La media divide il consumo per **tutti** i giorni del periodo selezionato,
  includendo quelli senza movimenti. Mancanza totale di dati non significa consumo
  certo pari a zero. Codici/configurazioni non calcolabili rendono incompleta la
  relativa fonte: si vedono gli storici ricostruibili, ma non si inventano medie o
  autonomie. Il riquadro qualità elenca gli errori.
- Il calendario alternativo è lunedì–venerdì, **festività incluse**; non è un
  calendario aziendale delle chiusure. Se lo storico contiene movimenti nel weekend,
  si richiede il calendario completo per evitare di perderli o spostarli.

### Disponibilità e scorta minima

Le quantità arrivano da **Polveri e prezzi**: quantità facoltativa della modalità
semplice oppure somma dei lotti della modalità avanzata. Quantità assente è
sconosciuta, distinta da zero. Ogni polvere conserva la sua data di dichiarazione.

Alla data dichiarata il dato è indicato come dichiarato. Dopo quella data viene
**stimato**, sottraendo il consumo medio dei giorni trascorsi; eventuali
rifornimenti successivi non sono noti. Prima della data dichiarata la giacenza è
non determinabile: non si ricostruisce uno storico di magazzino inesistente.
Una dichiarazione datata nel futuro non prova disponibilità oggi.

La tabella distingue quantità dichiarata e stima alla data odierna. Mostra consumo
medio, fabbisogno dell'orizzonte, residuo, copertura, scorta minima e acquisto
necessario. La copertura è residuo / consumo medio giornaliero. Con media assente
o nulla non si mostra una copertura infinita.

Selezionare una polvere e premere **Imposta scorta minima**. Si può scegliere:

- **KG**: quantità fissa;
- **GIORNI**: valore convertito in kg con media e calendario della fonte selezionata.

La stessa politica in giorni può quindi avere equivalenti diversi nei due scenari.
Gli acquisti necessari coprono deficit e scorta minima; se la minima non è impostata,
si mostra solo l'eventuale deficit. Se manca il dato necessario non si inventa uno
zero. I giorni alla scorta minima sono distinti dai giorni all'esaurimento.

## Simula produzione

Aggiungere righe, scegliere articoli/configurazioni approvate e quantità intere.
La predefinita ha precedenza nell'elenco. Selezionare **Consumo di riferimento per
l'autonomia** (produzioni o vendite) e premere **Calcola fabbisogno**.

La tabella mostra materia prima necessaria, residuo e autonomia prima/dopo. Il
riepilogo considera sia le polveri dell'ordine sia quelle consumate dal mix abituale,
anche se non coinvolte nell'ordine o nascoste nel grafico. Le polveri non usate
nell'ordine hanno fabbisogno zero e disponibilità invariata. Una disponibilità
insufficiente viene segnalata; valori non determinabili rendono esplicita la
copertura incompleta. La polvere limitante riguarda il mix rappresentato dalla
media, non ogni possibile produzione aziendale.

Il pannello espandibile **Contributo delle singole righe del piano** mostra kg per
articolo, configurazione, strato e polvere. Le righe ripetute sono additive.
Modificare il piano invalida i suoi risultati ed esportazioni. Uscita e ricaricamento
chiedono di scartare il piano transitorio: non è una produzione emessa né un ordine
salvato. Non sono effettuati scarichi sul magazzino.

## CSV e aggiornamento

- **Esporta CSV**: tabella della scheda corrente; nella simulazione tutte le polveri
  rilevanti, indipendentemente dai filtri del grafico.
- **Esporta serie giornaliere**: polveri/fonti visibili e quantità per data.
- **Esporta dettaglio piano**: contributi delle righe del piano, con metodo di calcolo.

Si usa il componente CSV condiviso: anteprima, conteggio, selezione file,
UTF-8/BOM, `;`, virgola decimale, numeri non arrotondati, celle vuote per dati mancanti.
Ricostruzione, preparazione delle serie e scrittura avvengono sui worker; la UI
invalida i callback obsoleti e non arresta i servizi condivisi alla chiusura.

La V9 aggiunge `COSTI.SCORTA_MINIMA`: polvere, numero revisione, unità, valore e
timestamp. I salvataggi sono versionati e protetti dai conflitti; non vengono
ritentati automaticamente. Non si salvano finte giacenze o risultati di previsione.
Il normale aggiornamento dei componenti include la migrazione, con backup HOST
prima delle modifiche e verifica CLIENT in sola lettura. Non serve un nuovo runtime.

## Arrivi previsti

**Includi arrivi previsti** acquisisce gli ordini insieme alla fotografia degli altri
dati. Ricaricare dopo modifiche agli ordini. Il residuo si aggiunge alla disponibilità
alla data prevista; righe escluse, scadute o senza data non vengono aggiunte.
Le scadute devono essere riscadenzate nella sezione Ordini. La quantità dichiarata
non viene modificata e, se manca, gli arrivi non inventano una giacenza iniziale.

Il grafico conserva una curva per polvere/fonte; il popup mostra anche gli arrivi
cumulati. La prima carenza e gli acquisti necessari considerano tutto l'orizzonte:
una consegna futura non nasconde un esaurimento precedente. Le stime sono giornaliere,
con consegna disponibile in quel giorno, senza orari di scarico.
Nella simulazione scegliere **Data produzione** tra oggi e la fine della previsione.
Il residuo è quello stimato a tale data meno il piano, e la copertura residua include
le consegne successive finché non si verifica il primo esaurimento. Non si sommano
previsioni basate su vendite e produzioni. Il CSV riporta inclusione degli arrivi,
prima carenza e data della produzione simulata.
