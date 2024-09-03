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
3. Click on New and enter the Driver name and then Driver Class as ```org.cip.CIPDriver```.

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

By following these steps and configurations, you should be able to connect to CIP data warehouse seamlessly.
