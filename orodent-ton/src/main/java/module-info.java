module com.orodent.ecos.ton {
    requires com.orodent.ecos.module.api;
    requires com.orodent.ecos.documents;
    requires transitive javafx.controls;
    requires javafx.web;
    requires jdk.jsobject;
    requires com.google.gson;

    provides com.orodent.ecos.module.EcosModule with
            com.orodent.ecos.ton.TonModule;

    exports com.orodent.ecos.ton.view;

    opens com.orodent.ecos.ton.features.production.model to
            com.google.gson;
}
