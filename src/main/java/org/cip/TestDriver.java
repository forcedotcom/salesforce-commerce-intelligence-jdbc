package org.cip;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;

public class TestDriver {
    public static void main(String[] args) {
        try {

            Enumeration<java.sql.Driver> driverList = DriverManager.getDrivers();
            while (driverList.hasMoreElements()) {
                java.sql.Driver driver = driverList.nextElement();
                System.out.println("Driver: " + driver.getClass().getName());
            }

            Class.forName( "org.cip.CIPDriver" );
            Connection conn = DriverManager.getConnection("jdbc:salesforce:remote://localhost:1234/testdb", "username", "password");
            System.out.println("Connection established: " + (conn != null));
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }
}
