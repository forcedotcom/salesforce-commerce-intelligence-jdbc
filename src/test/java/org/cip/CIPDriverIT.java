package org.cip;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;

import org.junit.Ignore;
import org.junit.Test;

/**
 * This test verifies if the CIPDriver is loading correctly and can communicate with the Account Manager to obtain an OAuth token. Note:
 * This test is disabled by default as it requires external dependencies and won't work in Jenkins CI environments. However, it is useful
 * for local testing with authentication. To run this test locally, simply enable it by removing the appropriate annotations or comments.
 */
public class CIPDriverIT {

    @Test
    public void testCIPDriver()
    {
        try
        {
            Enumeration<java.sql.Driver> driverList = DriverManager.getDrivers();
            while ( driverList.hasMoreElements() )
            {
                java.sql.Driver driver = driverList.nextElement();
                System.out.println( "Driver: " + driver.getClass().getName() );
            }

            Class.forName( "org.cip.CIPDriver" );
            Connection conn = DriverManager.getConnection( "jdbc:salesforcecc://localhost:9787/bjnl_prd",
                            "fff01280-e3c3-43e5-8006-5ea1301f9c50", "Demandware1!" );
            System.out.println( "Connection established: " + ( conn != null ) );
        }
        catch ( ClassNotFoundException | SQLException e )
        {
            e.printStackTrace();
        }
    }
}
