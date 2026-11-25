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
            Issued latest) {
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
                latest);
    }

    public record Prepared(
            Source source,
            Template template,
            LocalDate expiry,
            String data,
            String html,
            DocumentPaper paper) {}
}
