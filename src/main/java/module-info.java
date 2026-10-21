module com.orodent.tonv3 {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.orodent.tonv3 to javafx.fxml;
    exports com.orodent.tonv3;
}