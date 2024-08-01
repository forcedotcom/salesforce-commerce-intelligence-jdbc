# CIP JDBC Data Connector

The CIP JDBC Data Connector allows you to connect to your CIP data warehouse using a JDBC interface, providing seamless integration with various SQL editors and tools.

## Installation

To build and install the JDBC driver, follow these steps:

1. **Clone the Repository**
   ```bash
   git clone https://git.soma.salesforce.com/cc-commerce-intelligence-platform/cip-service-dataconnector.git
   cd cip-service-dataconnector
   ```

2. **Build the Driver**
   Use Maven to clean and build the project, generating a fat JAR file:
   ```bash
   mvn clean install
   ```

   This command will compile the code and package it into a single JAR file located in the `target` directory.

## Usage

After building the JAR file, you can install it in your SQL editor of choice. The JDBC driver exposes three main properties that you need to configure for connecting to your data sources:

<img width="500" alt="image" src="https://git.soma.salesforce.com/storage/user/8475/files/c8f6dc51-5610-4d7a-9451-a8a1507ed004">


### Properties

1. **JDBC URL**
   - **Description**: Specifies the CIP database connection string, including the instance you want to connect to.
   - **Type**: String
   - **Required**: Yes
   - **Example**: `jdbc:salesforcecc://localhost:9787/bjnl_prd`

2. **Username**
   - **Description**: The Client ID provided by the Account Manager.
   - **Type**: String
   - **Required**: Yes

3. **Password**
   - **Description**: The Client Secret provided by the Account Manager.
   - **Type**: String
   - **Required**: Yes

<img width="798" alt="image" src="https://git.soma.salesforce.com/storage/user/8475/files/e633e5ca-18fd-44d6-9727-f87b5dd4816e">


### User-Defined Properties

You can override the AM host name by adding a user-defined property as shown below:

```properties
amOauthHost=https://account-pod5.demandware.net
```
<img width="806" alt="image" src="https://git.soma.salesforce.com/storage/user/8475/files/3fe34db4-6b43-4681-bc1b-53f9038280ba">

### Connecting to the Database

In your SQL editor, use the JDBC URL format and provide the necessary credentials to establish a connection. The driver handles the authentication and connection setup using the provided properties.

By following these steps and configurations, you should be able to connect to CIP data warehouse seamlessly.
