module michelle.zaca_bus {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.postgresql.jdbc;
    requires jbcrypt;
    requires java.desktop; // BufferedImage de la cámara
    requires com.google.zxing; // generar y leer el QR del boleto
    requires webcam.capture; // cámara del abordaje

    // FXMLLoader instancia los controladores por reflexión
    opens michelle.zaca_bus.controller to javafx.fxml;
    opens michelle.zaca_bus.controller.admin to javafx.fxml;
    opens michelle.zaca_bus.controller.despachador to javafx.fxml;
    opens michelle.zaca_bus.controller.taquillero to javafx.fxml;
    opens michelle.zaca_bus.controller.jefetaller to javafx.fxml;
    exports michelle.zaca_bus;
}
