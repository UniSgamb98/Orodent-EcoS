# TON: navigazione e cataloghi tecnici

TON dispone di **Panoramica**, **Produzione**, **Forni e cotture**, **Dati tecnici**,
**Tracciabilità** e **Documenti**. Il percorso indica la pagina corrente;
**Home Orodent-EcoS** torna al catalogo principale e **Panoramica** resta in TON.

Dati tecnici mantiene al massimo due colonne, disposte per righe:

| Sinistra | Destra |
| --- | --- |
| Articoli | Composizioni |
| Prodotti tecnici | Modelli disco |
| Linee | Polveri |

Tutti e sei i cataloghi tecnici sono operativi e si aprono anche dalla
Panoramica. Le altre funzioni sono indicate come **In preparazione**, senza
contatori fittizi o comandi non implementati. Il cubaggio resta rinviato.

- [Linee, prodotti, articoli, storico e specifiche produttive](ton-cataloghi.md).
- [Polveri, ossidi e schede tecniche approvate](ton-polveri.md).
- [Modelli disco, strati, sovramateriali e versioni](ton-modelli-disco.md).
- [Composizioni, ricette per strato, ossidi e utilizzi](ton-composizioni.md).
- [Proposta dell'editor visuale dei documenti](ton-documenti-proposta.md), non ancora integrato.
- [Struttura e collegamenti del dominio TON](ton-schema-proposta.md).
- [Proposta grafica di riferimento](ton-cataloghi-proposta.md).

**Menu TON / Chiudi menu** richiude la barra laterale. Sotto 1020 pixel si
richiude automaticamente e dopo la scelta di un'area lascia spazio al contenuto.
**Esc** la richiude nella modalità compatta; allargando la finestra torna visibile.
Le schede e gli elenchi scorrono quando lo spazio non basta.

## Ciclo di vita

Il provider del modulo non apre il DB e non costruisce controlli prima della
selezione. TonController crea la pagina richiesta e ne rilascia letture,
listener e timer quando la si lascia. Tutti i cataloghi ricevono provider ed
executor di EcoS; non aprono un database o un pool di thread autonomo.

FeatureController espone salvataggio, verifica delle modifiche non salvate e
chiusura della pagina. Durante le scritture vengono disabilitati la Home di
EcoS e i collegamenti interni. I salvataggi non sono cancellati né ripetuti
quando si interrompe la connessione.

TonView mantiene header, menu, percorso e contenuto; TonFeature definisce ordine
e disponibilità delle funzioni; TonPages costruisce le pagine introduttive senza
query DB. Gli stili sono limitati alla radice TON. Le aree riutilizzano la stessa
finestra, senza avviare un processo distinto per catalogo.

## Database e aggiornamenti

V3 crea il catalogo prodotti, V4 aggiunge il dominio produttivo e documentale,
V5 il colore delle polveri e V6 la gerarchia dei cataloghi con storico reale.
Il dominio TON comprende 28 tabelle. Le migrazioni pubblicate restano immutabili.

L'HOST salva il backup prima delle scritture e applica le migrazioni pendenti in
transazione, prima di pubblicare il servizio. Il CLIENT verifica lo schema
corrente in sola lettura; il failover non applica DDL. Clienti, vendite, ID e UUID
restano conservati. Vedere [schema condiviso e migrazioni](database-schema.md).

L'aggiornamento arriva con la normale release firmata dei componenti: non occorre
sostituire Java o launcher. Per migrare un archivio esistente chiudere le istanze
e riavviare prima l'HOST aggiornato, poi i CLIENT.
