# agenda-connectors

## How to set up?

eXo Platform offers integration with three calendar providers: Google, Office365, and Outlook Exchange.

To start, ensure the add-on `exo-agenda-connectors` is installed (pre-packaged for eXo Enterprise Edition).

- for Linux:

```bash
./addon install exo-agenda-connectors
```
- for Windows:
  
```batch
addon.bat install exo-agenda-connectors
```

### Google 

- Refer to Google Clould platform > API & Services > Credentials. Create an OAuth client ID as follows:

![image](https://github.com/exoplatform/agenda-connectors/assets/27370604/e2bd9cfc-3094-46af-be32-f6883974dd97)

  Replace `instance-domaine.com` with the instance domain name and set a significant account name (eg `Tenant name - Ouath`).
  
  and then click on the `Save` button. A client ID and Client Secret should appear.

- At the eXo Server. edit the `exo.properties` file placed under `gatein/conf` folder and add the following lines:
  ```properties
  exo.agenda.google.connector.enabled=true
  exo.agenda.google.connector.key=XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX.apps.googleusercontent.com
  exo.agenda.google.connector.secret=XXXXXXXXXXXXXXXXXXX
  ```
- Start eXo Server, refer to the administration > Agenda, and Ensure that client ID and client Secret are correct.
- Refer to the personal settings page > Connect your personal Calendar > Select Google and follow authentication instructions

### Outlook 365 

- Refer to the Microsoft Azure platform using this [link](https://portal.azure.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade). Click on `+ New registration`:

![image](https://github.com/exoplatform/agenda-connectors/assets/27370604/8c3fb4eb-3129-4a7e-b22c-b4588ff10051)


  Replace `instance-domaine.com` with the instance domain name and set a significant account name (eg `Tenant name - Ouath`).
  
  and then click on the `Register` button. 

  Refer to API Permission, and ensure the below permissions are added:

  ![image](https://github.com/exoplatform/agenda-connectors/assets/27370604/54eab08b-a36b-4c4f-ae6c-2fe18840fc7a)

  Refer to Authentication, ensure all inputs are fulfilled as follows 

  ![image](https://github.com/exoplatform/agenda-connectors/assets/27370604/d3e52804-888c-43ba-beb8-0e51215ab271)

  Refer to Overview, get `Application (client) ID`. It will be used for the eXo Server setup.
  
- At the eXo Server. edit the `exo.properties` file placed under `gatein/conf` folder and add the following lines:
  ```properties
  exo.agenda.office.connector.enabled=true
  exo.agenda.office.connector.key=XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXX
  ```
- Start eXo Server, refer to the administration > Agenda, and Ensure that the client ID is correct.
- Refer to the personal settings page > Connect your personal Calendar > Select Outlook 365 and follow authentication instructions

### Microsoft Outlook exchange

- At the eXo Server. edit the `exo.properties` file placed under `gatein/conf` folder and add the following lines:
  ```properties
  exo.exchange.server.url=https://outlookexchange.url
  ```
- Start eXo Server, refer to the administration > Agenda, and Ensure that the client ID is correct.
- Refer to the personal settings page > Connect your personal Calendar > Select Exchange and follow authentication instructions


  
