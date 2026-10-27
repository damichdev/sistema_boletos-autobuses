module michelle.zaca_bus {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.postgresql.jdbc;
    requires jbcrypt;

    opens michelle.zaca_bus.controller to javafx.fxml;
    exports michelle.zaca_bus;
}
