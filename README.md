# CIP JDBC Driver

The CIP JDBC Driver allows you to connect your analytics and BI tools to your Salesforce Commerce Intelligence Platform (CIP) data warehouse using a standard JDBC interface.

## Download

Download the JDBC driver JAR from the root of this repository.

**Starting with version 0.1.25**, the JAR file includes `-shaded` in the filename:
- Example: `cip-client-dataconnector-0.1.25-shaded.jar`

The driver is self-contained and includes all required dependencies.

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

This driver uses SLF4J with Logback. Neither the shaded nor the unshaded JAR ships a `logback.xml` — logging is controlled entirely by the host application (DBeaver, ECOM, your Java app, etc.). By default, if no `logback.xml` is present on the classpath, only `ERROR`-level messages are printed to the console.

### Logger Names

| Logger | What it covers |
|--------|----------------|
| `com.salesforce.commerce.intelligence` | All CIP driver internals (auth, request handling, versioning) |
| `org.apache.hc.client5.http.impl.classic` | HTTP request/response details |

### Enable Logging in a Java Application

Add a `logback.xml` to your application's classpath (e.g. `src/main/resources/logback.xml`):

```xml
<configuration>
    <appender name="Console" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- CIP driver logs -->
    <logger name="com.salesforce.commerce.intelligence" level="DEBUG"/>
    <!-- Optional: HTTP client internals -->
    <logger name="org.apache.hc.client5.http.impl.classic" level="DEBUG"/>

    <root level="ERROR">
        <appender-ref ref="Console"/>
    </root>
</configuration>
```

### Enable Logging in DBeaver

DBeaver manages its own Logback configuration. To enable CIP driver debug logging:

1. Locate your DBeaver workspace directory (default: `~/.dbeaver-data/` on macOS/Linux, `%APPDATA%\DBeaverData\` on Windows).
2. Create or edit `logback.xml` in that directory and add the CIP logger:
   ```xml
   <configuration>
       <appender name="Console" class="ch.qos.logback.core.ConsoleAppender">
           <encoder>
               <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
           </encoder>
       </appender>

       <!-- CIP driver logs -->
       <logger name="com.salesforce.commerce.intelligence" level="DEBUG"/>

       <root level="ERROR">
           <appender-ref ref="Console"/>
       </root>
   </configuration>
   ```
3. Restart DBeaver for the configuration to take effect.

### Enable File Logging

To also write logs to a file, add a `RollingFileAppender`:

```xml
<configuration>
    <appender name="Console" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <appender name="File" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/cip-driver.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/cip-driver-%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                <maxFileSize>10MB</maxFileSize>
            </timeBasedFileNamingAndTriggeringPolicy>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="com.salesforce.commerce.intelligence" level="DEBUG"/>

    <root level="ERROR">
        <appender-ref ref="Console"/>
        <appender-ref ref="File"/>
    </root>
</configuration>
```

**Note:** Ensure the `logs/` directory exists and is writable, or update the `<file>` path as needed.

## Troubleshooting
- **Connection issues:** Double-check your JDBC URL, username, and password.
- **Proxy/SSL errors:** Ensure your Java system properties are set correctly.
- **Logging:** If you do not see logs, ensure your `logback.xml` is on the classpath and properly configured.

## Support
For help or to report issues, contact the Salesforce Support team.
