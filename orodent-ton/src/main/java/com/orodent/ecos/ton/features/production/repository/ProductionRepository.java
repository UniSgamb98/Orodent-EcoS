package com.orodent.ecos.ton.features.production.repository;

import static com.orodent.ecos.ton.features.production.model.Production.*;

import com.google.gson.Gson;
import com.orodent.ecos.database.PowderDisplayCatalogue;
import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels;
import com.orodent.ecos.ton.features.catalog.repository.*;
import com.orodent.ecos.ton.features.compositions.repository.CompositionRepository;
import com.orodent.ecos.ton.features.diskmodels.repository.DiskModelRepository;
import com.orodent.ecos.ton.features.documents.repository.DocumentTemplateRepository;

import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Reads real approved specifications; all writes use the caller's transaction. */
public final class ProductionRepository {
    private final Connection c;
    private static final Gson JSON = new Gson();

    public ProductionRepository(Connection c) {
        this.c = c;
    }

    public Catalogues catalogues() throws SQLException {
        var lines = new ArrayList<LineChoice>();
        var products = new ArrayList<ProductChoice>();
        var templates = new ArrayList<TemplateChoice>();
        try (var q = c.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT linea_id,codice,nome FROM TON.LINEE WHERE attivo=TRUE ORDER"
                                        + " BY codice")) {
            while (r.next())
                lines.add(new LineChoice(r.getLong(1), r.getString(2), r.getString(3)));
        }
        try (var q = c.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT"
                                    + " p.prodotto_id,p.linea_id,l.codice,l.nome,p.codice,p.descrizione,p.colore_commerciale"
                                    + " FROM TON.PRODOTTI p JOIN TON.LINEE l ON"
                                    + " l.linea_id=p.linea_id WHERE p.attivo=TRUE AND l.attivo=TRUE"
                                    + " AND p.colore_commerciale IS NOT NULL ORDER BY p.codice")) {
            while (r.next())
                products.add(
                        new ProductChoice(
                                r.getLong(1),
                                r.getLong(2),
                                r.getString(3),
                                r.getString(4),
                                r.getString(5),
                                r.getString(6),
                                r.getString(7)));
        }
        try (var q = c.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT modello_documento_id FROM TON.MODELLI_DOCUMENTO WHERE"
                                        + " tipo='PRODUZIONE' AND stato='APPROVATA' ORDER BY"
                                        + " nome,numero_versione DESC")) {
            while (r.next()) templates.add(template(r.getLong(1)));
        }
        return new Catalogues(lines, products, templates, PowderDisplayCatalogue.read(c));
    }

    public TemplateChoice template(Long id) throws SQLException {
        if (id == null) return null;
        var detail = new DocumentTemplateRepository(c).load(id);
        var t = detail.template();
        if (t.state()
                != com.orodent.ecos.ton.features.documents.model.DocumentTemplates.State.APPROVATA)
            throw invalid(
                    "Il modello documento non è più approvato. Seleziona un modello disponibile.");
        return new TemplateChoice(t.id(), t.code(), t.number(), t.name(), detail.content());
    }

    public List<BigDecimal> diameters(long product) throws SQLException {
        var values = new ArrayList<BigDecimal>();
        try (var q =
                c.prepareStatement(
                        "SELECT DISTINCT diametro_mm FROM TON.ARTICOLI WHERE prodotto_id=? AND"
                                + " attivo=TRUE ORDER BY diametro_mm DESC")) {
            q.setLong(1, product);
            try (var r = q.executeQuery()) {
                while (r.next()) values.add(r.getBigDecimal(1));
            }
        }
        return values;
    }

    public Workspace workspace(long productId, BigDecimal diameter, Map<Long, Long> pinned)
            throws SQLException {
        var repo = new CatalogRepository(c);
        var p = (CatalogModels.Product) repo.load(CatalogKind.PRODUCTS, productId);
        var line = (CatalogModels.Line) repo.load(CatalogKind.LINES, p.line().id());
        var product =
                new ProductChoice(
                        p.id(), line.id(), line.code(), line.name(), p.code(), p.name(), p.color());
        var entries =
                repo.page(
                                CatalogKind.ARTICLES,
                                new CatalogModels.Filter("", true, null, productId, diameter, null),
                                0,
                                Integer.MAX_VALUE)
                        .rows();
        var pinnedSpecifications = new HashSet<>(pinned.values());
        if (pinnedSpecifications.size() > 1)
            throw invalid(
                    "La produzione contiene specifiche diverse: ricarica la specifica comune del"
                            + " prodotto.");
        Long selectedSpecification =
                pinnedSpecifications.isEmpty() ? null : pinnedSpecifications.iterator().next();
        var articles = new ArrayList<ArticleChoice>();
        for (var entry : entries) {
            var article = (CatalogModels.Article) entry;
            articles.add(article(article, p, selectedSpecification));
        }
        return new Workspace(product, diameter, articles);
    }

    private ArticleChoice unavailable(CatalogModels.Article a, String reason) {
        return new ArticleChoice(
                a.id(),
                a.code(),
                a.diameter(),
                a.height(),
                0,
                0,
                0,
                0,
                0,
                0,
                "",
                null,
                null,
                reason,
                List.of(),
                null,
                null);
    }

    private ArticleChoice article(
            CatalogModels.Article a, CatalogModels.Product product, Long pinned)
            throws SQLException {
        if (product.specificationConflict())
            return unavailable(
                    a,
                    "Specifiche del prodotto da verificare: scegli la predefinita nella scheda"
                            + " prodotto");
        Long id = pinned != null ? pinned : product.defaultSpecification();
        if (id == null)
            return unavailable(
                    a, "Specifica del prodotto da approvare e impostare come predefinita");
        var spec = new SpecificationRepository(c).load(id);
        if (spec.productId() != product.id())
            throw invalid("Specifica non coerente con il prodotto.");
        if (!spec.recorded())
            return unavailable(a, "Specifica precedente da verificare nella scheda prodotto");
        var compRepo = new CompositionRepository(c);
        var comp = compRepo.load(spec.compositionId());
        var model = new DiskModelRepository(c).load(spec.modelId());
        var m = model.data();
        String reason =
                !product.available() || !a.active()
                        ? "Articolo, prodotto o linea archiviati"
                        : !"APPROVATA".equals(spec.state())
                                ? "Specifica non più approvata"
                                : comp.state()
                                                != com.orodent.ecos.ton.features.compositions.model
                                                        .Compositions.State.APPROVATA
                                        ? "Composizione non più approvata"
                                        : model.state()
                                                        != com.orodent.ecos.ton.features.diskmodels
                                                                .model.DiskModels.State.APPROVATA
                                                ? "Modello non più approvato"
                                                : "";
        if (comp.data().productId() != a.product().id())
            throw invalid("Composizione non coerente con il prodotto.");
        try {
            comp.data().requireApprovable();
            m.requireApprovable();
            m.forArticleDiameter(a.diameter());
        } catch (IllegalArgumentException bad) {
            return unavailable(a, bad.getMessage());
        }
        if (m.layers().size() != comp.data().layers().size())
            return unavailable(a, "Strati di composizione e modello non corrispondenti");
        var revisionIds =
                comp.data().layers().stream()
                        .flatMap(l -> l.ingredients().stream())
                        .map(i -> i.powderRevisionId())
                        .toList();
        var powders = compRepo.powders(revisionIds);
        BigDecimal top = m.top(), bottom = m.bottom();
        for (var range : m.ranges())
            if (a.height().compareTo(range.from()) >= 0 && a.height().compareTo(range.to()) < 0) {
                top = range.top();
                bottom = range.bottom();
                break;
            }
        var doses = new ArrayList<Dose>();
        for (int i = 0; i < m.layers().size(); i++) {
            var height = a.height().multiply(m.layers().get(i)).movePointLeft(2);
            if (i == 0) height = height.add(top);
            if (i == m.layers().size() - 1) height = height.add(bottom);
            var shares = new ArrayList<PowderShare>();
            for (var ingredient : comp.data().layers().get(i).ingredients()) {
                var powder = powders.get(ingredient.powderRevisionId());
                if (powder == null || !powder.usable())
                    reason = "Una polvere della composizione non è più disponibile";
                if (powder != null)
                    shares.add(
                            new PowderShare(
                                    powder.id(), powder.code(), powder.name(), ingredient.mass()));
            }
            doses.add(new Dose(i + 1, height, height.multiply(m.gramsFor(a.diameter())), shares));
        }
        return new ArticleChoice(
                a.id(),
                a.code(),
                a.diameter(),
                a.height(),
                spec.id(),
                spec.number(),
                comp.id(),
                comp.number(),
                model.id(),
                model.number(),
                m.code(),
                m.pressure(),
                m.mouldDiameterFor(a.diameter()),
                reason,
                doses,
                spec.labelTemplateId(),
                spec.labelTemplateId() == null ? "Non assegnata" : spec.labelTemplateLabel());
    }

    public void lockSelection(Workspace shown, Request request) throws SQLException {
        var catalog = new CatalogRepository(c);
        catalog.lock(CatalogKind.LINES, shown.product().lineId());
        catalog.lock(CatalogKind.PRODUCTS, shown.product().id());
        if (!((CatalogModels.Product) catalog.load(CatalogKind.PRODUCTS, shown.product().id()))
                .available()) throw invalid("Prodotto o linea non più disponibili.");
        var comp = new CompositionRepository(c);
        var models = new DiskModelRepository(c);
        var selected =
                shown.articles().stream()
                        .filter(a -> request.quantities().getOrDefault(a.id(), 0) > 0)
                        .sorted(Comparator.comparingLong(ArticleChoice::id))
                        .toList();
        for (var a : selected) catalog.lock(CatalogKind.ARTICLES, a.id());
        for (long id :
                selected.stream()
                        .map(ArticleChoice::compositionId)
                        .filter(id -> id > 0)
                        .distinct()
                        .sorted()
                        .toList()) comp.lock(id);
        for (long id :
                selected.stream()
                        .map(ArticleChoice::modelId)
                        .filter(id -> id > 0)
                        .distinct()
                        .sorted()
                        .toList()) models.lock(id);
        comp.lockPowders(
                selected.stream()
                        .flatMap(a -> a.doses().stream())
                        .flatMap(d -> d.powders().stream())
                        .map(PowderShare::revisionId)
                        .distinct()
                        .toList());
        for (long id :
                selected.stream()
                        .map(ArticleChoice::specificationId)
                        .filter(id -> id > 0)
                        .distinct()
                        .sorted()
                        .toList())
            try (var q =
                    c.prepareStatement(
                            "UPDATE TON.CONFIGURAZIONI_PRODOTTO SET"
                                    + " versione_modifica=versione_modifica WHERE"
                                    + " configurazione_id=?")) {
                q.setLong(1, id);
                q.executeUpdate();
            }
        if (request.templateId() != null)
            try (var q =
                    c.prepareStatement(
                            "UPDATE TON.MODELLI_DOCUMENTO SET versione_modifica=versione_modifica"
                                    + " WHERE modello_documento_id=?")) {
                q.setLong(1, request.templateId());
                q.executeUpdate();
            }
    }

    public Saved save(Saved expected, Plan plan, boolean prepare) throws SQLException {
        long id;
        String state = prepare ? "PREPARATA" : "BOZZA";
        String values =
                "prodotto_id=?,diametro_mm=?,data_preparazione=?,modello_documento_id=?,note=?,contenuto_piano=?,stato=?,etichetta_prodotto=?,numero_formati=?,numero_dischi=?,titolo_documento=?,preparato_il="
                        + (prepare ? "CURRENT_TIMESTAMP" : "NULL");
        if (expected == null) {
            try (var q =
                    c.prepareStatement(
                            "INSERT INTO"
                                + " TON.PIANI_PRODUZIONE(prodotto_id,diametro_mm,data_preparazione,modello_documento_id,note,contenuto_piano,stato,etichetta_prodotto,numero_formati,numero_dischi,titolo_documento,preparato_il)"
                                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,"
                                    + (prepare ? "CURRENT_TIMESTAMP" : "NULL")
                                    + ")",
                            Statement.RETURN_GENERATED_KEYS)) {
                parameters(q, plan, state);
                q.executeUpdate();
                try (var r = q.getGeneratedKeys()) {
                    r.next();
                    id = r.getLong(1);
                }
            }
        } else {
            id = expected.id();
            try (var q =
                    c.prepareStatement(
                            "UPDATE TON.PIANI_PRODUZIONE SET "
                                    + values
                                    + ",versione_modifica=versione_modifica+1,aggiornato_il=CURRENT_TIMESTAMP"
                                    + " WHERE piano_id=? AND versione_modifica=? AND"
                                    + " stato='BOZZA'")) {
                parameters(q, plan, state);
                q.setLong(12, id);
                q.setInt(13, expected.token());
                if (q.executeUpdate() != 1)
                    throw invalid(
                            "La bozza è stata modificata da un'altra postazione. Ricarica i dati"
                                    + " prima di salvare.");
            }
            for (String table : List.of("PIANO_PRODUZIONE_RIGHE", "PIANO_PRODOTTO_RIGHE"))
                try (var q = c.prepareStatement("DELETE FROM TON." + table + " WHERE piano_id=?")) {
                    q.setLong(1, id);
                    q.executeUpdate();
                }
        }
        try (var q =
                c.prepareStatement(
                        "INSERT INTO"
                            + " TON.PIANO_PRODOTTO_RIGHE(piano_id,numero_riga,configurazione_id,quantita_richiesta,articolo_id,prodotto_id,articolo_revisione)"
                            + " VALUES (?,?,?,?,?,?,?)")) {
            int n = 0;
            for (var f : plan.formats()) {
                q.setLong(1, id);
                q.setInt(2, ++n);
                q.setLong(3, f.article().specificationId());
                q.setInt(4, f.quantity());
                q.setLong(5, f.article().id());
                q.setLong(6, plan.product().id());
                var article = new CatalogRepository(c).load(CatalogKind.ARTICLES, f.article().id());
                new CatalogHistoryRepository(c).verifyCurrent(article);
                q.setInt(7, article.revision());
                q.addBatch();
            }
            q.executeBatch();
        }
        return load(id);
    }

    private void parameters(PreparedStatement q, Plan plan, String state) throws SQLException {
        q.setLong(1, plan.product().id());
        q.setBigDecimal(2, plan.diameter());
        q.setDate(3, java.sql.Date.valueOf(plan.date()));
        q.setObject(4, plan.template() == null ? null : plan.template().id(), Types.BIGINT);
        q.setString(5, plan.notes());
        q.setCharacterStream(6, new StringReader(JSON.toJson(plan)));
        q.setString(7, state);
        q.setString(8, plan.product().toString());
        q.setInt(9, plan.formats().size());
        q.setInt(10, plan.count());
        q.setString(
                11,
                plan.template() == null
                        ? "Non richiesto"
                        : plan.template().name() + " · v" + plan.template().version());
    }

    public Saved load(long id) throws SQLException {
        try (var q =
                c.prepareStatement(
                        "SELECT p.*,d.contenuto_html FROM TON.PIANI_PRODUZIONE p LEFT JOIN"
                                + " TON.PIANI_DOCUMENTI d ON d.piano_id=p.piano_id WHERE"
                                + " p.piano_id=?")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                if (!r.next()) throw invalid("Produzione non trovata.");
                var plan = JSON.fromJson(r.getString("contenuto_piano"), Plan.class);
                if ((plan.schemaVersion() != 1 && plan.schemaVersion() != 2)
                        || !RULE.equals(plan.calculation()))
                    throw invalid("Versione del piano non supportata.");
                return new Saved(
                        id,
                        r.getInt("versione_modifica"),
                        State.valueOf(r.getString("stato")),
                        plan,
                        r.getString("contenuto_html"));
            }
        }
    }

    public List<Summary> list(String search, State state) throws SQLException {
        var values = new ArrayList<Summary>();
        try (var q =
                c.prepareStatement(
                        "SELECT"
                            + " piano_id,versione_modifica,stato,data_preparazione,etichetta_prodotto,numero_formati,numero_dischi,titolo_documento,note"
                            + " FROM TON.PIANI_PRODUZIONE "
                                + (state == null ? "" : "WHERE stato=? ")
                                + "ORDER BY data_preparazione DESC,piano_id DESC")) {
            if (state != null) q.setString(1, state.name());
            try (var r = q.executeQuery()) {
                while (r.next()) {
                    var row =
                            new Summary(
                                    r.getLong(1),
                                    r.getInt(2),
                                    State.valueOf(r.getString(3)),
                                    r.getDate(4).toString(),
                                    r.getString(5),
                                    r.getInt(6),
                                    r.getInt(7),
                                    r.getString(8));
                    if ((row.number() + " " + row.product() + " " + r.getString(9))
                            .toLowerCase(Locale.ROOT)
                            .contains(search.strip().toLowerCase(Locale.ROOT))) values.add(row);
                }
            }
        }
        return values;
    }

    public String document(long id, String html) throws SQLException {
        if (html == null || html.isBlank() || html.length() > 16_000_000)
            throw invalid("Documento vuoto o troppo grande.");
        try (var lock =
                c.prepareStatement(
                        "UPDATE TON.PIANI_PRODUZIONE SET versione_modifica=versione_modifica WHERE"
                                + " piano_id=? AND stato='PREPARATA'")) {
            lock.setLong(1, id);
            if (lock.executeUpdate() != 1) throw invalid("Prima conferma la produzione.");
        }
        var saved = load(id);
        if (saved.plan().template() == null) throw invalid("Nessun modello documento selezionato.");
        if (saved.document() != null) return saved.document();
        try (var q =
                c.prepareStatement(
                        "INSERT INTO TON.PIANI_DOCUMENTI(piano_id,contenuto_html) VALUES (?,?)")) {
            q.setLong(1, id);
            q.setCharacterStream(2, new StringReader(html));
            q.executeUpdate();
        }
        return html;
    }
}
