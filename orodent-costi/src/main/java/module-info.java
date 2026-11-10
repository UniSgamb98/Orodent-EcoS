module com.orodent.ecos.costi {
    requires com.orodent.ecos.module.api;
    requires javafx.controls;
    requires com.google.gson;
    opens com.orodent.ecos.costi.products.model to com.google.gson;
    opens com.orodent.ecos.costi.scenarios to com.google.gson;

    provides com.orodent.ecos.module.EcosModule with
            com.orodent.ecos.costi.CostiModule;
}
