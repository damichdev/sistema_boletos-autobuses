module michelle.zaca_bus {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.postgresql.jdbc;
    requires jbcrypt;

    opens michelle.zaca_bus.controller to javafx.fxml;
    opens michelle.zaca_bus.controller.admin_controller to javafx.fxml;
    opens michelle.zaca_bus.controller.despachador_controller to javafx.fxml;
    opens michelle.zaca_bus.controller.taquillero_controller to javafx.fxml;
    exports michelle.zaca_bus;
}
