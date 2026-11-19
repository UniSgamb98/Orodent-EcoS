# Alias delle polveri: proposta da concordare

Questa è una proposta di funzionamento, senza modifiche al riconoscimento attuale.

## Punto di partenza

Gli ordini CSV contengono un codice articolo e un identificativo fornitore.
Il catalogo TON ha un proprio codice polvere e il nome del fornitore: il numero
fornitore del CSV e il nome del fornitore TON sono informazioni distinte.
Già oggi l’importazione memorizza le associazioni nella tabella
`COSTI.POLVERI_CODICI_FORNITORE`, per la coppia fornitore CSV + codice esterno.
Le riutilizza nelle importazioni successive. Queste associazioni vanno recuperate,
non richieste di nuovo all’utente.

## Interfaccia proposta

Nella scheda TON di una polvere aggiungere la scheda **Alias**, con un elenco di
codici alternativi, il fornitore CSV e la provenienza (inserito manualmente o
collegato durante un’importazione). La polvere mantiene il proprio codice TON;
gli alias rimandano al suo identificativo stabile.

Esempio: la polvere TON `ZPEX4.M` può riconoscere `ZRZPEX4M` per il fornitore
`2501018`, più eventuali altri codici dello stesso o di un altro fornitore.
Una polvere può avere molti alias. La stessa coppia fornitore CSV + alias può
identificare una sola polvere; se è già usata, mostrare quale polvere la possiede.
Un codice uguale di un altro fornitore può identificare una polvere diversa.

Quando si collega un codice nell’anteprima CSV, quel collegamento diventa un alias
solo con la conferma dell’importazione, come oggi. Nella scheda Alias si potrà
anche preparare il collegamento prima di importare il file.

## Riconoscimento proposto

1. Cercare un alias esatto per fornitore CSV + codice articolo.
2. In assenza di alias, mantenere il riconoscimento del codice TON esatto.
3. Se non c’è corrispondenza, chiedere il collegamento nell’anteprima.

Ignorare maiuscole/minuscole e spazi alle estremità dei codici, conservando la
scrittura visualizzata. Trattini, punti e prefissi ZR/ZS restano significativi:
non rimuoverli automaticamente, perché possono distinguere materiali diversi.
Eventuali somiglianze potranno proporre candidati, senza confermare associazioni.

Modificare o eliminare un alias influenzerà i riconoscimenti successivi; non dovrà
riscrivere ordini già importati, lotti, prezzi o versioni tecniche. Un cambio
polvere del collegamento va mostrato esplicitamente prima di salvarlo.

## Scelta ancora aperta

Per la prima versione partire dai **codici esterni per fornitore**, che risolvono
il riconoscimento dei CSV. Gli eventuali nomi commerciali alternativi per la
ricerca libera sono una seconda esigenza: conviene distinguerli dagli alias usati
per importare, soprattutto quando uno stesso nome commerciale è ambiguo.

Prima dell’implementazione definire insieme se servono entrambi i tipi e come
identificare comodamente il fornitore del CSV quando si inserisce un alias a mano.
