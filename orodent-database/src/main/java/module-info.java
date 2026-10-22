module com.orodent.ecos.database {
    requires transitive com.orodent.ecos.core;
    requires org.apache.derby.server;
    requires org.apache.derby.tools;
    requires org.apache.derby.client;
    exports com.orodent.ecos.database;
    exports com.orodent.ecos.database.discovery;
    exports com.orodent.ecos.database.csv;
    exports com.orodent.ecos.database.model;
    exports com.orodent.ecos.database.repository;
    exports com.orodent.ecos.database.repository.impl;
    exports com.orodent.ecos.database.service;
}
