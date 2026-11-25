module com.orodent.ecos.misurazioni {
    requires com.orodent.ecos.module.api;
    requires com.orodent.ecos.documents;
    requires j2mod;
    requires com.google.gson;

    provides com.orodent.ecos.module.EcosModule with
            com.orodent.ecos.misurazioni.MisurazioniModule;
}
