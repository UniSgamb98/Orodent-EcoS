# Scenari — proposta di navigazione e comportamento

Proposta grafica, non implementazione del modulo Scenari. Il
[prototipo navigabile](prototypes/scenari.html) usa soltanto dati dimostrativi,
funziona senza CDN e mantiene le bozze nella memoria della pagina. Ricaricare la
pagina elimina queste bozze. L'esportazione CSV contiene esclusivamente esempi.
La scelta Semplice/Avanzata nel prototipo illustra il flusso di conferma ma non
rilegge i dati EcoS e usa gli stessi prezzi dimostrativi.

## Struttura

Il menu dei moduli rimane a sinistra. Dentro Scenari, un elenco secondario mostra
le bozze e il comando Nuovo scenario. Il lavoro selezionato occupa tutto il resto
della finestra. Tre passaggi in alto, liberamente navigabili:

1. **Base reale**: nome, nota, prodotti/linee/configurazioni da includere, fonte
   Semplice/Avanzata, inclusione facoltativa degli ordini, data della fotografia.
2. **Modifiche**: tre schede separate, Prezzi polveri / Ricette / Trasformazione.
   A destra rimane l'anteprima dell'impatto sul prodotto e articolo selezionati.
3. **Confronto**: base e scenario affiancati, differenze EUR e %, grafico per
   formato, elenco delle ipotesi, dettaglio dei contributi e CSV.

La base è sempre visibile sopra i passaggi, con data e politica prezzi. La parola
"reale" indica la provenienza della fotografia, non una lettura continuamente
aggiornata del database. I dati dimostrativi del prototipo hanno un prodotto
omogeneo e tre formati, non sono valori dell'ufficio.

## Modifiche con significato esplicito

**Prezzi polveri**: per ogni identità della polvere, mostrare prezzo base, modalità
Invariato / Variazione % / Prezzo EUR/kg, input e prezzo scenario risultante.
L'input modifica il **prezzo applicato** della polvere dopo la valutazione della
fonte scelta; non modifica i prezzi dei singoli acquisti o lotti reali.
Un aumento percentuale parte sempre dalla base. Le variazioni successive non si
moltiplicano implicitamente. L'applicazione a gruppi (bianche, gialle, rosa, grigie)
sostituisce le ipotesi delle sole righe selezionate. Una freccia ripristina ogni
riga. Le quantità dichiarate a magazzino e la relativa data restano consultabili.

**Ricette**: selezionare il prodotto e la composizione di partenza. Scegliere una
revisione alternativa approvata oppure creare una copia nello scenario. Per ogni
strato, mostrare ingredienti e percentuali Base/Scenario. Ogni strato deve
raggiungere 100%. Aggiunte e rimozioni di polveri appartengono soltanto alla copia.
Il prototipo permette di provare le percentuali su un singolo strato omogeneo;
non comprende ancora la selezione delle ricette TON o l'aggiunta di ingredienti.
Non sono pubblicate composizioni e non vengono modificate configurazioni TON.

**Trasformazione**: costo comune per prodotto e override facoltativo per articolo.
Campo eccezione vuoto = eredita. Zero = costo esplicitamente nullo.

Le modifiche da prezzo e ricetta possono coesistere; il confronto elenca entrambe.
"Ripristina tutto" richiede conferma. Quantità e disponibilità non si scaricano
perché un costo viene simulato.

## Confronti leggibili

La miscela nominale EUR/kg resta separata dal costo del singolo articolo.
Il nominale esclude sovramateriali e perdite; il costo reale del disco conserva
il calcolo condiviso con sovramateriali e fattore perdite, più trasformazione.
Il confronto riporta sempre la configurazione esatta, evitando medie tra formati.
Se le configurazioni danno prezzi nominali diversi, restano distinguibili.

Ogni riga articolo mostra Base, Scenario, Delta EUR e Delta %. Il dettaglio apre
contributi delle singole polveri e trasformazione. Il filtro "Solo variati" nasconde
le righe uguali; l'ambito del CSV deve essere indicato chiaramente, con possibilità
di esportare tutto l'ambito dello scenario. Nel prototipo il CSV esporta i tre
articoli dimostrativi anche se il filtro Solo variati è attivo.

Non presentare una media di costo globale o un totale aziendale senza quantità
esplicite. In seguito un piano di produzione potrà pesare gli articoli e mostrare
consumi e coperture con le ricette dello scenario, riutilizzando Consumi e fabbisogni.
Per la prima versione ci concentriamo sulla valutazione economica.

## Fotografie, conservazione e aggiornamento

Uno scenario salvato deve conservare i valori e le revisioni della base:
composizioni e ingredienti, modelli e sovramateriali, specifiche articolo,
valutazioni delle polveri e relativi lotti/ordini residui, parametri economici,
politica prezzi e data di acquisizione. Riferimenti ai record correnti da soli
non bastano: una modifica successiva non deve cambiare un confronto già salvato.

Salva bozza crea una revisione dello scenario. Duplica conserva la base e le
ipotesi in una nuova scheda. Prevedere archivio reversibile e controllo dei
salvataggi concorrenti. Nome e base rimangono sempre identificabili nel CSV.
Uscita e cambio scenario devono proteggere le modifiche non salvate.

**Aggiorna base** apre un confronto prima di applicare i nuovi dati:

- prezzi, giacenze, ordini e revisioni tecniche cambiati;
- percentuali di variazione che verranno ricalcolate sulla nuova base;
- prezzi assoluti e override che rimarranno quelli impostati;
- ricette o collegamenti non più compatibili, da risolvere manualmente.

L'aggiornamento crea una nuova revisione e conserva la precedente. Non applicare
una modifica allo strato o alla polvere sbagliati quando una struttura cambia;
segnalare gli override non più collegabili e bloccare i relativi risultati finché
non vengono risolti. Il prototipo dimostra il rinnovo del prezzo base e la
distinzione tra aumento percentuale e prezzo assoluto, non questa migrazione
completa di modelli/ricette.

## Completezza e prestazioni

Un dato assente non è zero. Se la base manca ma il prezzo scenario è compilato,
il costo scenario può essere noto mentre la differenza resta non determinabile.
Mostrare esplicitamente i costi incompleti, senza inventare risparmi o percentuali.
Le stime della base (per esempio scorte non assegnate ai lotti valorizzate al
riferimento) mantengono una provenienza consultabile. Un prezzo imposto nello
scenario è un'ipotesi esplicita e deve essere distinto da quel warning.

JDBC, salvataggi ed esportazioni restano sui worker EcoS. Calcoli condivisi,
cache delle combinazioni ricetta/modello/prezzi, aggiornamenti ritardati durante
la digitazione e annullamento dei risultati obsoleti evitano di bloccare la UI.
Niente riscrittura parallela delle formule già usate da Costi prodotti.
