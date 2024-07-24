package org.cip;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class TestDriver {
    public static void main(String[] args) {
        try {
            Class.forName("org.cip.Driver");
            Connection conn = DriverManager.getConnection("jdbc:salesforce:remote://localhost:1234/testdb", "username", "password");
            System.out.println("Connection established: " + (conn != null));
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }
}
