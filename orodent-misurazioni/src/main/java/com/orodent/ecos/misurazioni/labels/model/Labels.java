package com.orodent.ecos.misurazioni.labels.model;

import com.orodent.ecos.documents.DocumentPaper;
import com.orodent.ecos.misurazioni.jobs.model.MeasurementJobs.Job;

import java.time.LocalDate;
import java.util.*;

public final class Labels {
    private Labels() {}

    public record Template(long id, String code, int version, String name, String content) {
        @Override
        public String toString() {
            return code + " · v" + version + " · " + name;
        }
    }

    public record Issued(long id, long templateId, String data, String html, DocumentPaper paper) {}

    public record Source(
            Job job,
            Long assignedTemplate,
            List<Template> templates,
            LocalDate produced,
            String gtin,
            String name,
            String line,
            int articleRevision,
            Issued latest,
            Integer specificationVersion,
            Integer preferredSpecificationVersion) {
        public LocalDate expiry() {
            return produced.plusYears(5);
        }

        public String specificationLabel() {
            return specificationVersion == null
                    ? "Specifica precedente"
                    : "Specifica v" + specificationVersion;
        }

        public String missingTemplateMessage() {
            return "Nessun modello etichetta assegnato nella "
                    + specificationLabel().toLowerCase(Locale.ITALIAN)
                    + " del piano "
                    + job.production()
                    + ". "
                    + (preferredSpecificationVersion == null
                            ? ""
                            : "La predefinita attuale del prodotto è v"
                                    + preferredSpecificationVersion
                                    + ". ")
                    + "Per le nuove produzioni imposta come predefinita la specifica con"
                    + " l’etichetta. I piani già salvati e i dischi prodotti conservano la"
                    + " specifica originale.";
        }

        public Source {
            templates = List.copyOf(templates);
        }
    }

    public static Source withLatest(Source source, Issued latest) {
        return new Source(
                source.job(),
                source.assignedTemplate(),
                source.templates(),
                source.produced(),
                source.gtin(),
                source.name(),
                source.line(),
                source.articleRevision(),
                latest,
                source.specificationVersion(),
                source.preferredSpecificationVersion());
    }

    public record Prepared(
            Source source,
            Template template,
            LocalDate expiry,
            String data,
            String html,
            DocumentPaper paper) {}
}
