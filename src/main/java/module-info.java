module mx.edu.utch.melo {
    requires javafx.controls;
    requires javafx.fxml;

    opens mx.edu.utch.melo.controller to javafx.fxml;

    exports mx.edu.utch.melo;
    exports mx.edu.utch.melo.nav;
    exports mx.edu.utch.melo.model;
    exports mx.edu.utch.melo.util;
    exports mx.edu.utch.melo.validation;
    exports mx.edu.utch.melo.view;
    exports mx.edu.utch.melo.controller;
}
