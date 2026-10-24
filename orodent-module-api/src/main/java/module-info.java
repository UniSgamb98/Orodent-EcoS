/** Shared entry point for office applications hosted by EcoS. */
module com.orodent.ecos.module.api {
    requires transitive com.orodent.ecos.core;
    requires transitive com.orodent.ecos.database;
    requires transitive javafx.controls;
    exports com.orodent.ecos.module;
}
