# Dal backup CRM2 al CRM di EcoS

Adattamento dei tuoi script `generate_import_sql.py`, `merge.ps1` e dei BAT Derby.
L'origine resta il backup del vecchio **CRM2**, non il database aggiornato Clients.
Gli identificativi UUID5 mantengono lo stesso namespace del generatore originale.

## Uso rapido su Windows

1. Aggiorna EcoS a una versione che includa questo strumento e avvialo almeno una
   volta sull'HOST, così viene preparato lo schema CRM (V13 o successivo).
2. Nella cartella di questo strumento crea `input`, metti il backup chiamato
   `clients.txt` e copia gli XML originali in `input\note`.
3. Doppio clic su **1-Prepara-importazione.bat**. Serve Python 3.10 o successivo,
   come per il tuo vecchio generatore. Non serve più lanciare Merge.ps1: gli XML
   vengono letti direttamente, rispettando la codifica indicata nel file.
4. Leggi `output\import_report.txt`. Eventuali anomalie bloccano l'importazione.
5. Sul PC **HOST di EcoS**, con l'app nella Home e senza altre postazioni che
   modificano il CRM, esegui **2-Importa-in-EcoS.bat**. Prima mostra la destinazione
   e i conteggi; poi chiede conferma, crea il backup Derby e importa tutto insieme.
6. Riapri il CRM o premi **Ricarica pagina**. Gli operatori del vecchio CRM saranno
   disponibili con i loro nomi originali normalizzati in maiuscolo.

Lo strumento è in `scripts\crm-migration` nel progetto e in
`tools\crm-migration` nello ZIP Java. Nel pacchetto Windows con EXE può trovarsi in
`app\tools\crm-migration`. Usa le librerie e, se disponibile, Java di EcoS: non
serve installare una seconda distribuzione Derby.

È un'importazione iniziale: **il CRM di destinazione deve essere vuoto**. Se hai
creato anagrafiche di prova, la verifica si ferma e le conserva. Non cancella
automaticamente dati, non sovrascrive schede esistenti e non permette di importare
una seconda volta sullo stesso CRM già popolato. I cataloghi iniziali e gli
operatori già presenti vengono conservati; i nomi operatore coincidenti riusano
il relativo UUID (anche con maiuscole/minuscole diverse). Omonimi ambigui o
operatori disattivati richiedono una correzione prima dell'importazione.

I file di origine non vengono modificati. I file `input`/`output` sono esclusi da
Git e dai pacchetti distribuiti. Nessun dato reale è incluso nello strumento.

## File note già unito e comandi manuali

Puoi continuare a usare il vecchio `tutte_le_note.txt`: mettilo in `input` al
posto degli XML. Il primo BAT lo preferisce quando è presente. Supporta i vecchi
marcatori `===== FILE: nome.xml =====` / `===== END FILE: nome.xml =====`, anche
se il file unito è UTF-16 con BOM. Non occorre rigenerarlo con PowerShell.

```powershell
py -3 generate_import_sql.py --clients input/clients.txt --notes-dir input/note --output output
# In alternativa:
py -3 generate_import_sql.py --clients input/clients.txt --notes input/tutte_le_note.txt --output output
```

Il backup anagrafiche deve restare nel formato originale di 29 campi separati da
`;`, UTF-8 anche con BOM. È ammesso il separatore finale del vecchio formato.
Un campo testuale con un `;` non codificato rende la riga ambigua e viene segnalato.
Non vengono troncati valori troppo lunghi o inventate date non valide.

Se un XML non è collegabile a un'anagrafica, contiene errori, ci sono date o
coinvolgimenti non validi, la preparazione produce il report ma blocca il
manifest. Correggere gli input e ripetere la preparazione.
`--allow-anomalies` è disponibile soltanto da terminale per accettare
esplicitamente le anomalie del report: XML non validi/non collegati non verranno
importati, date non valide diventano sconosciute e i loro valori originali sono
conservati nelle note. Non supera errori strutturali, UUID duplicati o campi troppo
lunghi. Usarlo solo dopo aver verificato ogni anomalia.

Gli storici XML mancanti per clienti già contattati sono avvisi non bloccanti:
le anagrafiche vengono importate, conservando le date disponibili in `clients.txt`,
ma senza le note XML assenti. Gli identificativi restano elencati nel report.

## Contenuto trasferito

Anagrafiche e stato finale, operatori, contatti/referenti, indirizzi, telefoni,
email, siti, note e chiamate con prossimo richiamo. I tipi e gli stati presenti
nel backup completano i cataloghi di EcoS senza sostituire quelli esistenti.
Le date storiche e l'ordine delle chiamate sono conservati; gli esiti riconosciuti
sono mappati sui codici di EcoS. Campi CRM2 senza una colonna equivalente (come
checkpoint e numero contatti) sono riportati in una nota riepilogativa.
Per le chiamate prive di data, la data di contatto resta sconosciuta; la data di
creazione registra l'importazione. Piccoli scarti nei secondi conservano l'ordine
delle chiamate quando il backup non specifica un orario.
Un operatore mancante usa il segnaposto esplicito `NON SPECIFICATO CRM2`:
le anagrafiche non scompaiono così dal calendario per un operatore nullo.

## Destinazione, backup ed errori

I vecchi percorsi `I:/.../Clients` e le vecchie porte non vengono riutilizzati.
Il BAT cerca il `config/application.properties` della distribuzione e si collega
al server EcoS locale, usando `database.port` (normalmente **1528**) e il database
**OrodentEcoS**. Se usi un altro file di configurazione, imposta `ECOS_CONFIG` prima
di eseguire il BAT. Si possono impostare anche `ECOS_LIB`, `ECOS_HOME` e `ECOS_JAVA`.
L'HOST deve essere acceso: lo strumento non crea DB, non avvia un altro server e
non esegue migrazioni di schema.

L'esecutore crea un backup consistente con `SYSCS_BACKUP_DATABASE` sotto
`database.home/backups/before-crm2-import-...` prima della prima scrittura.
Poi verifica di nuovo il CRM vuoto sotto lock ed esegue i file in un'unica
transazione. Se un'istruzione fallisce, effettua rollback dell'intera importazione.
Una connessione persa durante il commit può lasciare un esito non confermato:
verificare i conteggi sul DB prima di ripetere, senza cancellare il backup.

`output/migration.properties` contiene versione, conteggi e SHA-256 dei file SQL.
L'esecutore verifica il pacchetto, ammette solo gli INSERT delle tabelle CRM e
operatori previsti, e riconosce correttamente apici, punti e virgola e righe multiple
nelle note. Alterare a mano i file SQL invalida la preparazione: correggere gli
input e rigenerarli. La preparazione fallita invalida anche il vecchio manifest,
così non si esegue per errore una precedente esportazione.

L'esecutore Java può essere usato manualmente dal progetto, dopo `mvnw.cmd package`:

```powershell
java -cp "../../orodent-launcher/target/distribution/app/lib/*" com.orodent.ecos.crm.migration.LegacyCrmImport --dir output --config "../../orodent-launcher/target/distribution/config/application.properties"
# Il comando sopra verifica soltanto. Aggiungere --apply per importare con backup.
```

Non è stata eseguita una migrazione sui tuoi dati reali: questi script sono stati
verificati con dati sintetici. Per l'operazione reale servono il tuo `clients.txt`
e i relativi XML, nella cartella `input` sul tuo PC.
