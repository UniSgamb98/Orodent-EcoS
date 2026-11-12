package com.orodent.ecos.crm;

import javafx.scene.*;
import javafx.scene.control.*;

import java.util.*;

/** Captures form values, excluding table skins and other generated controls. */
final class EditorGuard {
    private final Parent page;
    private List<String> baseline;

    EditorGuard(Parent page) {
        this.page = page;
    }

    void clean() {
        if (page
                        instanceof
                        com.orodent.ecos.crm.feature.clienti.schedacliente.view.SchedaClienteView
                                        profile
                && profile.isInline()) profile.markInlineClean();
        baseline = values();
    }

    boolean dirty() {
        if (page
                        instanceof
                        com.orodent.ecos.crm.feature.clienti.schedacliente.view.SchedaClienteView
                                        profile
                && profile.isInline()) return profile.hasUnsavedChanges();
        return baseline != null && !baseline.equals(values());
    }

    private List<String> values() {
        var out = new ArrayList<String>();
        collect(page, out);
        return out;
    }

    private void collect(Node node, List<String> out) {
        if (node instanceof TextInputControl text) out.add(text.getText());
        else if (node instanceof DatePicker date)
            out.add(Objects.toString(date.getValue(), "") + date.getEditor().getText());
        else if (node instanceof ComboBox<?> box)
            out.add(
                    Objects.toString(box.getValue(), "")
                            + (box.isEditable() ? box.getEditor().getText() : ""));
        else if (node instanceof ChoiceBox<?> box) out.add(Objects.toString(box.getValue(), ""));
        else if (node instanceof CheckBox box) out.add(Boolean.toString(box.isSelected()));
        else if (node instanceof TabPane tabs)
            tabs.getTabs()
                    .forEach(
                            t -> {
                                if (t.getContent() != null) collect(t.getContent(), out);
                            });
        else if (node instanceof ScrollPane scroll && scroll.getContent() != null)
            collect(scroll.getContent(), out);
        else if (node instanceof TitledPane titled && titled.getContent() != null)
            collect(titled.getContent(), out);
        else if (!(node instanceof Control) && node instanceof Parent parent)
            parent.getChildrenUnmodifiable().forEach(n -> collect(n, out));
    }
}
