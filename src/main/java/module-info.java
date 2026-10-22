module com.orodent.tonv3 {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.apache.derby.server;
    requires org.apache.derby.tools;
    requires org.apache.derby.client;
    exports com.orodent.tonv3.app;
    exports com.orodent.tonv3.core;
    exports com.orodent.tonv3.core.database;


    opens com.orodent.tonv3 to javafx.fxml;
    exports com.orodent.tonv3;
}