module mx.edu.utch.melo {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;

    opens mx.edu.utch.melo to javafx.fxml;
    exports mx.edu.utch.melo;
}