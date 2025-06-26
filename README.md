# CIP JDBC Driver

The CIP JDBC Drive allows you to connect to your CIP data warehouse using a JDBC interface, providing seamless integration with various SQL editors and tools.

## Installation

To build and install the JDBC driver, follow these steps:

1. **Clone the Repository**
   ```bash
   git clone https://git.soma.salesforce.com/cc-commerce-intelligence-platform/cip-client-dataconnector.git
   cd cip-client-dataconnector
   ```

2. **Build the Driver**
   Use Maven to clean and build the project, generating a fat JAR file:
   ```bash
   mvn clean install
   ```

   This command will compile the code and package it into a single JAR file located in the `target` directory.

## Usage

After building the JAR file, you can install it in your SQL editor of choice. The JDBC driver exposes three main properties that you need to configure for connecting to your data sources:

**Registering the CIP Driver in DBeaver**
Assuming you are using DBeaver, follow these steps to register the CIPDriver:

1. Open DBeaver
2. Go to Database -> Driver Manager.
3. Click on New and enter the Driver name and then Driver Class as ```com.salesforce.commerce.intelligence.jdbc.client.CIPDriver```.

<img width="496" alt="image" src="https://git.soma.salesforce.com/storage/user/8475/files/99dd309a-4cba-4e4d-ae76-6100d53bbb46">

Navigate to the Libraries tab and click on Add File. Choose the location of the CIP Driver JAR file you built earlier. Click on Find driver.

<img width="500" alt="image" src="https://git.soma.salesforce.com/storage/user/8475/files/c8f6dc51-5610-4d7a-9451-a8a1507ed004">

Click on OK.

Now, Navigate to Database -> New Database Connection. Click the driver you registered in the step above and Click on Next.

### Properties

1. **JDBC URL**
   - **Description**: Specifies the CIP database connection string, including the instance you want to connect to.
   - **Type**: String
   - **Required**: Yes
   - **Example**: `jdbc:salesforcecc://localhost:9787/bjnl_prd`

For QA env: use 'jdbc:salesforcecc://jdbc.qa.analytics-dev.commercecloud.salesforce.com:443/bjnl_prd'

2. **Username**
   - **Description**: The Client ID provided by the Account Manager.
   - **Type**: String
   - **Required**: Yes

For QA env: use 'fff01280-e3c3-43e5-8006-5ea1301f9c50'

3. **Password**
   - **Description**: The Client Secret provided by the Account Manager.
   - **Type**: String
   - **Required**: Yes

<img width="798" alt="image" src="https://git.soma.salesforce.com/storage/user/8475/files/e633e5ca-18fd-44d6-9727-f87b5dd4816e">


### User-Defined Properties

You can override the AM host name by adding a user-defined property as shown below: (Default value for this is Production AM URL)

```properties
amOauthHost=https://account-pod5.demandware.net
```
<img width="806" alt="image" src="https://git.soma.salesforce.com/storage/user/8475/files/3fe34db4-6b43-4681-bc1b-53f9038280ba">

### Connecting to the Database

In your SQL editor, use the JDBC URL format and provide the necessary credentials to establish a connection. The driver handles the authentication and connection setup using the provided properties.

### Network Configuration (Proxy/SSL Support)

The JDBC driver automatically uses system-level network settings when making HTTP requests to the backend.  
This is possible because the driver configures the HTTP client with `.useSystemProperties()`.

This allows you to configure proxy or SSL settings using standard Java system properties, without changing any code.

**Examples:**

```bash
-Dhttp.proxyHost=proxy.mycompany.com
-Dhttp.proxyPort=8080
-Dhttps.proxyHost=proxy.mycompany.com
-Dhttps.proxyPort=8443
-Djavax.net.ssl.trustStore=/path/to/truststore.jks
-Djavax.net.ssl.trustStorePassword=changeit


By following these steps and configurations, you should be able to connect to data warehouse seamlessly.

## Logging

This driver uses SLF4J with Logback. By default, it logs only errors (root logger level is set to ERROR).

- To customize logging, add your own `logback.xml` to your application's classpath. Your configuration will override the default provided by the driver.
- To enable debug logging for HTTP client internals, add the following to your `logback.xml`:
  ```xml
  <logger name="org.apache.hc.client5.http.impl.classic" level="DEBUG"/>
  ```
- For more information on Logback configuration, see the [Logback documentation](https://logback.qos.ch/manual/configuration.html).

### Example: Enable File Logging in Your Application

If you want to log to a file as well as the console, add the following to your `logback.xml` (placed in your application's classpath):

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

**Note:**
- Make sure the `logs/` directory exists and is writable by your application, or change the `<file>` path to a location your app can write to.
- You can adjust the `<root level="ERROR">` to `WARN`, `INFO`, or `DEBUG` as needed.
