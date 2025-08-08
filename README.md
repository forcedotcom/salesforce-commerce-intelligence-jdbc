# CIP JDBC Driver

The CIP JDBC Driver allows you to connect your analytics and BI tools to your Salesforce Commerce Intelligence Platform (CIP) data warehouse using a standard JDBC interface.

## Download

Download the JDBC driver JAR from the root of this repository.

Example file: `cip-client-dataconnector-x.x.xx.jar`

## Quick Start

1. **Add the JAR to your SQL tool or application.**
   - Most SQL editors (e.g., DBeaver, SQuirreL SQL, DataGrip) allow you to add a custom JDBC driver by specifying the JAR file and the driver class name.
2. **Register the driver class:**
   - Driver class: `com.salesforce.commerce.intelligence.jdbc.client.CIPDriver`
3. **Configure your connection:**
   - See below for required properties.

## Configuration

### JDBC URL
- **Format:**
  ```
  jdbc:salesforcecc://<host>:<port>/<database>
  ```
- **Example:**
  ```
  jdbc:salesforcecc://localhost:9787/bjnl_prd
  ```

### Required Properties
- **Username:** The Client ID set up in Account Manager for your application
- **Password:** The Client Secret set up in Account Manager for your application

### Network Configuration (Proxy/SSL)
The driver uses your system's Java network properties. To use a proxy or custom SSL trust store, set standard Java system properties:

```
-Dhttp.proxyHost=proxy.mycompany.com
-Dhttp.proxyPort=8080
-Dhttps.proxyHost=proxy.mycompany.com
-Dhttps.proxyPort=8443
-Djavax.net.ssl.trustStore=/path/to/truststore.jks
-Djavax.net.ssl.trustStorePassword=changeit
```

### Default Fetch Size

The JDBC driver sets a default fetch size of **1000** rows per request to match the server's maximum frame size. This ensures efficient paging and prevents the client from requesting more rows than the server will return. If you set a higher fetch size, the server will cap it at 1000.

You can override the fetch size using the `fetchSize` connection property, but values above 1000 will be capped by the server.

## Logging

This driver uses SLF4J for logging. By default, only errors are logged. To customize logging:

- Add your own `logback.xml` to your application's classpath.
- Example to enable debug logging for HTTP client internals:
  ```xml
  <logger name="org.apache.hc.client5.http.impl.classic" level="DEBUG"/>
  ```
- For more information, see the [Logback documentation](https://logback.qos.ch/manual/configuration.html).

### Example: Enable File Logging
```xml
<configuration>
    <appender name="Console" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <appender name="File" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/app.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/app-%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                <maxFileSize>10MB</maxFileSize>
            </timeBasedFileNamingAndTriggeringPolicy>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="ERROR">
        <appender-ref ref="Console"/>
        <appender-ref ref="File"/>
    </root>
</configuration>
```
**Note:** Ensure the `logs/` directory exists and is writable, or change the `<file>` path as needed.

## Troubleshooting
- **Connection issues:** Double-check your JDBC URL, username, and password.
- **Proxy/SSL errors:** Ensure your Java system properties are set correctly.
- **Logging:** If you do not see logs, ensure your `logback.xml` is on the classpath and properly configured.

## Support
For help or to report issues, contact the Salesforce Support team.
