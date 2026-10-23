module com.orodent.ecos.statistiche {
    requires com.orodent.ecos.module.api;
    requires com.orodent.ecos.core;
    requires com.orodent.ecos.database;
    requires transitive javafx.controls;

    exports com.orodent.ecos.statistiche;
    provides com.orodent.ecos.module.EcosModule with com.orodent.ecos.statistiche.StatisticheModule;
}
