# Registro attività CRM

Il modulo `orodent-registro` è nascosto nella Home come TON e Costi e consumi.
**Ctrl+L** mostra/nasconde tutte e tre le schede. La registrazione è attiva anche
quando il modulo è chiuso o nascosto: non dipende dalla schermata di consultazione.

La pagina contiene periodo (ultimi sette giorni inizialmente), operatore, tipo di
azione e ricerca per nome/ID cliente. **Applica** esegue i filtri; **Aggiorna** aggiorna
anche gli operatori disponibili. I totali contano salvataggi e creazioni/modifiche/
eliminazioni appartenenti ai salvataggi filtrati. Ogni pagina carica al massimo
100 salvataggi completi. Anche il dettaglio delle grandi importazioni è paginato
a 100 elementi e costruisce i confronti solo quando si espandono le relative voci. L'aggiornamento automatico avviene ogni 30 secondi, è
disattivabile e conserva il dettaglio selezionato quando resta nella pagina.

Selezionando un salvataggio si vedono autore, data, postazione, origine,
identificativi e modifiche raggruppate. Il confronto mostra i campi cambiati;
creazioni ed eliminazioni conservano tutti i campi disponibili, compreso il testo
completo delle note. I testi sono selezionabili e copiabili, in sola lettura.
Doppio clic sulla riga o **Apri cliente** porta alla scheda CRM, passando dalla
scelta operatore se necessaria. Un cliente cancellato resta consultabile nelle
istantanee del registro, senza tentare di ricrearlo.

## Registrazione e transazioni

La migrazione V24 aggiunge `AUDIT.OPERATIONS`, `AUDIT.CHANGES`, due routine Java e
60 trigger per INSERT/UPDATE/DELETE sulle 19 tabelle CRM di dominio più
`ECOS.OPERATORI`. I contatori tecnici `CRM.CATALOGHI_VERSIONI` sono esclusi.
La migrazione usa il consueto backup preventivo sull'HOST e non crea uno storico
artificiale dei dati già presenti. CLIENT con schema precedente deve essere aggiornato.

Il CRM e l'importatore CRM2 dichiarano il contesto sulla stessa connessione JDBC
usata per salvare: UUID/nome operatore al momento dell'azione, postazione,
origine e ID di salvataggio. Il contesto è locale alla connessione e si svuota al
commit. L'ID dell'autore dell'attività e quello di chi la modifica sono distinti:
il registro conserva il secondo senza riscrivere il primo. Nomi e identificativi
storici del registro non vengono cambiati da rinomine o cancellazioni successive.

Le routine usano la transazione del chiamante: un rollback annulla anche gli eventi.
Un errore di registrazione fa fallire il salvataggio, senza lasciare modifiche prive
di registro. Gli INSERT/UPDATE/DELETE sullo stesso elemento nello stesso salvataggio
sono accorpati confrontando stato iniziale e finale. Un elemento aggiunto e rimosso
prima del commit non genera un evento. Le riscritture interne delle risorse con
valori invariati e le sole variazioni di UPDATED_AT/CREATED_AT/VERSIONE_MODIFICA
non creano finte modifiche; le istantanee conservano anche questi campi quando
esiste una modifica effettiva ai dati.

Sono intercettate anche scritture SQL esterne e cancellazioni in cascata. Senza
contesto esplicito si registra **SQL esterno / origine non dichiarata**, senza
inventare autore o postazione, mantenendo l'utente database. Queste scritture sono
registrate per elemento, senza attribuire loro un raggruppamento applicativo che
il database non conosce. Le letture, i tentativi annullati e le modifiche ad altri
moduli non fanno parte di questo primo ambito.

Il modulo non espone comandi di modifica/cancellazione del registro né ripristino
automatico. Non è un sistema antimanomissione contro un amministratore con accesso
SQL al database. I dati sono inclusi nei normali backup Derby. Non viene applicata
una cancellazione automatica dello storico.
