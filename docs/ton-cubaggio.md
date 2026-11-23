# Cubaggio

Aprire **TON → Cubaggio → Regole di cubatura**, nel gruppo **Lavoro**.
La pagina usa l'elenco comune TON: ricerca, **Aggiorna**, filtro degli archiviati,
tabella che occupa lo spazio disponibile e **Nuova regola** in alto a destra.
Il doppio clic su una riga apre l'editor.

Ogni regola ha un **codice univoco**, un **nome**, una **descrizione** e lo stato
attiva/archiviata. **Salva regola** registra i dati; modifiche concorrenti e codici
duplicati sono segnalati. Uscire da un editor modificato richiede conferma;
durante il salvataggio la navigazione è bloccata.

Nelle **Specifiche di produzione** del prodotto, **Riferimento metodo di calcolo**
è un selettore non modificabile a testo libero. Mostra le regole del catalogo e
salva il codice della regola scelta. Il pulsante che apriva la pagina è stato rimosso.
Le specifiche approvate rimangono di sola lettura; le regole archiviate restano
visibili per le associazioni esistenti e non sono accettate nei nuovi salvataggi.

La migrazione **V26** crea `TON.REGOLE_CUBATURA` e registra una regola per ogni
riferimento distinto già presente nelle specifiche dei prodotti, conservando
esattamente il testo salvato e tutte le versioni delle specifiche. La colonna
`TON.CONFIGURAZIONI_PRODOTTO.versione_calcolo` diventa una chiave esterna verso
il codice univoco della regola. Un codice utilizzato non può essere cambiato;
nome e descrizione sono metadati modificabili. Le produzioni già preparate
conservano i propri riferimenti e valori originali.

L'esecuzione di formule, i parametri numerici e **Invia misurazioni** saranno
definiti nel passaggio successivo. La descrizione è documentazione del metodo,
non una formula eseguibile.
