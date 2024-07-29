# CIP JDBC Data Connector

The CIP JDBC Data Connector allows you to connect to your CIP data warehouse using a JDBC interface, providing seamless integration with various SQL editors and tools.

## Installation

To build and install the JDBC driver, follow these steps:

1. **Clone the Repository**
   ```bash
   git clone https://git.soma.salesforce.com/cc-commerce-intelligence-platform/cip-service-dataconnector.git
   cd <repository_directory>
   ```

2. **Build the Driver**
   Use Maven to clean and build the project, generating a fat JAR file:
   ```bash
   mvn clean install
   ```

   This command will compile the code and package it into a single JAR file located in the `target` directory.

## Usage

After building the JAR file, you can install it in your SQL editor of choice. The JDBC driver exposes three main properties that you need to configure for connecting to your data sources:

### Properties

1. **instanceId**
   - **Description**: Specifies the database instance you want to connect to.
   - **Type**: String
   - **Required**: Yes

2. **clientId**
   - **Description**: The Client ID provided by the Account Manager.
   - **Type**: String
   - **Required**: Yes

3. **clientSecret**
   - **Description**: The Client Secret provided by the Account Manager.
   - **Type**: String
   - **Required**: Yes

### Example Usage

To use the driver, configure your SQL editor or application with the following properties:

```properties
jdbc.url=jdbc:cip://<your_database_url>
jdbc.instanceId=<your_instance_id>
jdbc.clientId=<your_client_id>
jdbc.clientSecret=<your_client_secret>
```

Replace `<your_database_url>`, `<your_instance_id>`, `<your_client_id>`, and `<your_client_secret>` with your actual database URL and credentials.

### Connecting to the Database

In your SQL editor, use the JDBC URL format and provide the necessary credentials to establish a connection. The driver handles the authentication and connection setup using the provided properties.
