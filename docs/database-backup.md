# Backup e ripristino del database

## Backup automatico prima delle migrazioni

Quando l'HOST rileva un archivio supportato da aggiornare, legacy `APP` o schema
2 o 3, prima di qualsiasi scrittura di baseline o migrazione esegue `SYSCS_UTIL.SYSCS_BACKUP_DATABASE`.
È il motore Derby a produrre una copia consistente, completa di dati e log.
In questo momento il server di rete e il discovery non sono ancora pubblicati.
Un backup fallito interrompe l'avvio: nessuna modifica dello schema viene applicata.

I backup sono fuori dalla directory del database attivo, sotto la stessa
`database.home` scelta da file di configurazione o override. Con il percorso
di produzione attuale, la struttura è:

```text
I:/CliZr/Tommaso/Database/
├── OrodentEcoS/                         # database attivo
└── backups/
    └── before-schema-4-<data-UTC>-<id>/
        ├── backup.properties           # marcatore di backup completato
        └── OrodentEcoS/                 # copia consistente precedente alla migrazione
            ├── service.properties
            ├── seg0/
            └── log/
```

`backup.properties` contiene directory di origine, data UTC e versione di
migrazione prevista; viene pubblicato soltanto dopo il completamento della copia.
Una directory senza questo file è incompleta e non va usata per il ripristino.
I tentativi successivi usano directory diverse e non sovrascrivono le copie.
Una migrazione fallita conserva il backup completato e annulla la transazione.

Un database nuovo non richiede un backup di migrazione. Un normale riavvio su
schema già corrente e le verifiche CLIENT non creano backup. I database futuri
o con storico corrente incompatibile vengono rifiutati senza modificarli.
Il backup copre i passaggi dagli schemi 1, 2 o 3 allo schema corrente 4 e precede
anche la registrazione di una baseline mancante. Il ripristino della copia conserva
i dati precedenti e l'identità del database, quando già presente.

Le copie non vengono cancellate automaticamente. Controllare spazio e permessi
prima della distribuzione e conservare separatamente le copie importanti.
Un backup sullo stesso disco protegge da una migrazione errata; per guasti del
disco servono anche copie su un altro supporto. Non è ancora un backup periodico
delle modifiche quotidiane.

## Ripristino manuale su Windows

Il ripristino riporta **tutto** il database al momento del backup: tutte le
modifiche successive a quel momento non saranno presenti nella copia ripristinata.

1. Chiudere tutte le istanze HOST e CLIENT e sospendere i riavvii sulle postazioni.
   Individuare la `database.home` realmente usata dall'HOST, considerando anche gli
   override JVM/variabile d'ambiente. Non lavorare sulla cartella di sviluppo.
2. Selezionare un backup con `backup.properties`, `OrodentEcoS/service.properties`,
   `OrodentEcoS/seg0` e `OrodentEcoS/log` presenti. Copiarlo su un altro supporto
   prima di intervenire.
3. Nella directory HOST rinominare la cartella attiva `OrodentEcoS`, ad esempio in
   `OrodentEcoS-prima-ripristino-20261023-1800`. Conservarla per recuperi e diagnosi.
   Non cancellare file di lock mentre un'applicazione è ancora aperta.
4. Copiare **l'intera cartella** `OrodentEcoS` contenuta nel backup nella directory
   HOST. Il risultato deve essere `database.home/OrodentEcoS/service.properties`,
   senza una sottocartella `OrodentEcoS/OrodentEcoS`. Non unire file al database
   precedente e non copiare la sola `seg0`.
5. Avviare prima l'HOST con una release compatibile con il database ripristinato.
   La versione attuale sa migrare gli schemi 1, 2 e 3: al riavvio crea un nuovo backup
   e riprova la migrazione. Se si sta investigando una migrazione fallita,
   correggere la causa prima di riprovarla. Per rimanere sulla vecchia release,
   disabilitare temporaneamente `updates.enabled` nel suo config prima dell'avvio.
6. Verificare avvio, modalità HOST e dati; quindi riavviare i CLIENT con la
   release prevista per lo schema scelto.

Il launcher aggiorna i componenti, ma non ripristina o retrocede automaticamente
il database. Se una migrazione fallisce e la transazione viene annullata, il
database originale è normalmente già integro: correggere la causa segnalata e
riprovare può essere sufficiente. Il ripristino rimane un intervento esplicito.

Le prove automatiche creano un database su disco con clienti e vendite legacy,
generano il backup, migrano l'originale e riaprono il backup come database
separato. Verificano dati, ID e assenza delle scritture di migrazione nella copia;
verificano anche blocco in caso di backup non scrivibile e conservazione della
copia se la migrazione successiva fallisce.
