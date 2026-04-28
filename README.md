# ExamArchive / Klausurarchiv

## English Version

### Architecture
ExamArchive is built using Java and the Javalin web framework. It uses the Java Template Engine (JTE) for server-side HTML rendering. Data is stored in a PostgreSQL database, managed through a HikariCP connection pool and Flyway database migrations. File storage is handled via an S3-compatible service, such as MinIO.

### Features
* **Authentication (OIDC)**: The application uses Keycloak for OpenID Connect authentication. It supports two login paths using Identity Provider hints: `unims` for regular users and `microsoft` for administrators. Access roles (`USER`, `ADMIN`, or `BLOCKED`) are assigned dynamically by validating specific `affiliation` claims returned by Keycloak.
* **Internationalization**: The interface supports English and German. The language is selected automatically based on the HTTP `Accept-Language` header sent by the user's browser.
* **Search Functionality**: Users can search for modules and exams using a text query. The search relies on PostgreSQL trigram similarity (`pg_trgm`) to perform fuzzy matching across module names, module keywords, and professor names. Users can also filter results by degree programs. If the search field is empty or contains fewer than three characters, the system defaults to displaying the top 10 most downloaded modules from the last two years.
* **Exam Upload**: Users can submit new exams as PDF documents. The upload form requires selecting an existing module, entering the year and semester, and providing the professor's first and last name. The interface provides auto-suggestions for professors based on the selected module. Uploaded exams receive a `PENDING` status and remain hidden from standard users until approved.
* **PDF Sanitization**: Upon upload, the PDF is processed using Apache PDFBox to mitigate security risks. The sanitization process strips the file of embedded JavaScript, embedded files, open actions, and interactive widget actions.
* **Exam Download**: Exams are served securely via temporary S3 pre-signed URLs that expire after 10 minutes. Every download action is recorded in the database to generate usage statistics.
* **Administration**:
    * **Exam Review**: Administrators can review `PENDING` exams, approve them (`ACCEPTED`), modify metadata (module, year, semester, professor), or delete them entirely.
    * **Module & Degree Management**: Modules and academic degrees can be created or deleted. Degrees can be mapped to specific modules to aid filtering. Admins can also attach search keywords to modules to improve discoverability.
    * **User Management**: The admin panel allows searching for users based on name or email. Admins can manually block or unblock users by altering their account roles.
    * **Dashboard & Statistics**: The admin index displays system metrics, including the number of accounts per role and download statistics broken down by week, month, year, and all-time.
    * **System Settings**: Administrators can configure a Message of the Day (MOTD) with a specific expiration timestamp.

### Environment Variables
* `EXAMARCHIVE_DEV_MODE`: If set to `true`, enables development mode. This turns off secure HTTP-only requirements for session cookies.
* `EXAMARCHIVE_DB_HOSTNAME`: The hostname or IP address of the PostgreSQL database.
* `EXAMARCHIVE_DB_PORT`: The port number of the PostgreSQL database.
* `EXAMARCHIVE_DB_USERNAME`: The username for database access.
* `EXAMARCHIVE_DB_PASSWORD`: The password for database access.
* `EXAMARCHIVE_DB_DATABASE`: The name of the specific database to connect to.
* `KEYCLOAK_ISSUER`: The base URL of the Keycloak realm used for OIDC authentication.
* `KEYCLOAK_CLIENT_ID`: The client identifier registered in Keycloak.
* `KEYCLOAK_CLIENT_SECRET`: The client secret for the Keycloak application.
* `KEYCLOAK_REDIRECT_URI`: The specific callback endpoint where Keycloak returns the user after authentication.
* `KEYCLOAK_USER_AFFILIATION`: The required claim value a user must possess to be granted standard `USER` access.
* `KEYCLOAK_ADMIN_AFFILIATION`: The required claim value a user must possess to be granted `ADMIN` access.
* `EXAMARCHIVE_STORAGE_ENDPOINT`: The URL of the MinIO/S3 object storage instance.
* `EXAMARCHIVE_STORAGE_ACCESS_KEY`: The access key credential for the storage service.
* `EXAMARCHIVE_STORAGE_SECRET_KEY`: The secret key credential for the storage service.
* `EXAMARCHIVE_STORAGE_BUCKET`: The name of the storage bucket. The application automatically creates this bucket on startup if it does not already exist.
* `EXAMARCHIVE_ADMIN_EMAIL` : The email address of the admin user. For example, @example.com
* `EXAMARCHIVE_SMTP_HOST` : The SMTP host to use for sending emails.
* `EXAMARCHIVE_SMTP_PORT` : The SMTP port to use for sending emails.
* `EXAMARCHIVE_SMTP_USERNAME` : The username for SMTP authentication.
* `EXAMARCHIVE_SMTP_PASSWORD` : The password for SMTP authentication.
* `EXAMARCHIVE_SMTP_FROM` : The email address to use as the sender for all outgoing emails.

***

## Deutsche Version

### Architektur
ExamArchive basiert auf Java und nutzt das Javalin-Webframework. Das serverseitige Rendering der HTML-Oberfläche erfolgt über die Java Template Engine (JTE). Für die Datenspeicherung wird eine PostgreSQL-Datenbank verwendet, deren Verbindung über einen HikariCP-Pool verwaltet wird. Datenbankmigrationen werden durch Flyway automatisiert. Dokumente werden in einem S3-kompatiblen Object Storage, wie beispielsweise MinIO, abgelegt.

### Funktionen
* **Authentifizierung (OIDC)**: Das System verwendet Keycloak für OpenID Connect. Beim Login wird durch Identity-Provider-Hints unterschieden: `unims` leitet reguläre Benutzer weiter, `microsoft` wird für Administratoren genutzt. Die Zuweisung der Rollen (`USER`, `ADMIN` oder `BLOCKED`) erfolgt basierend auf bestimmten `affiliation`-Claims, die im Token des Benutzers enthalten sein müssen.
* **Internationalisierung**: Die Applikation unterstützt die deutsche und englische Sprache. Die Auswahl erfolgt automatisch anhand des `Accept-Language`-Headers des Browsers.
* **Suchfunktion**: Die Suche nach Modulen und Klausuren verwendet PostgreSQL-Trigramm-Ähnlichkeit (`pg_trgm`). Dies erlaubt eine unscharfe Suche über Modulnamen, Modul-Schlagwörter (Keywords) und Professorennamen. Die Suchergebnisse lassen sich nach Studiengängen filtern. Wenn kein Suchbegriff eingegeben wird oder dieser kürzer als drei Zeichen ist, zeigt das System standardmäßig die Top 10 der am häufigsten heruntergeladenen Module der letzten zwei Jahre an.
* **Klausur-Upload**: Nutzer können Klausuren als PDF-Dateien hochladen. Der Prozess erfordert die Auswahl eines Moduls, die Angabe von Jahr und Semester sowie Vor- und Nachnamen der Lehrperson. Abhängig vom gewählten Modul schlägt das System bereits hinterlegte Professoren vor. Neue Uploads erhalten den Status `PENDING` und sind für normale Nutzer nicht sichtbar.
* **PDF-Bereinigung (Sanitization)**: Jede hochgeladene PDF-Datei wird mittels Apache PDFBox verarbeitet. Um Sicherheitsrisiken zu minimieren, entfernt das System sämtliches eingebettetes JavaScript, angehängte Dateien, Open-Actions sowie interaktive Widget-Aktionen aus dem Dokument.
* **Download-Verwaltung**: Klausuren werden nicht direkt über den Webserver ausgeliefert. Das System generiert signierte S3-URLs (Pre-signed URLs), die exakt 10 Minuten gültig sind. Jeder Download wird in der Datenbank erfasst, um Nutzungsstatistiken zu berechnen.
* **Administration**:
    * **Klausuren prüfen**: Administratoren sichten `PENDING`-Klausuren. Sie können diese freigeben (`ACCEPTED`), ablehnen und löschen oder die Metadaten (Modul, Jahr, Semester, Professor) bearbeiten.
    * **Module & Studiengänge**: Neue Module und Studiengänge (Degrees) können angelegt oder gelöscht werden. Module lassen sich mit Studiengängen verknüpfen. Zudem können Suchbegriffe (Keywords) zu Modulen hinzugefügt werden, um die Suchergebnisse zu optimieren.
    * **Benutzerverwaltung**: Im Admin-Bereich kann nach Benutzern gesucht werden. Administratoren haben die Möglichkeit, Konten manuell zu sperren (Block) oder wieder freizugeben.
    * **Dashboard & Statistiken**: Die Admin-Startseite bietet eine Übersicht der aktiven und blockierten Benutzer sowie detaillierte Download-Statistiken (wöchentlich, monatlich, jährlich, gesamt).
    * **Einstellungen**: Administratoren können eine Systemnachricht (Message of the Day - MOTD) konfigurieren, die nach Ablauf eines festgelegten Datums automatisch verschwindet.

### Umgebungsvariablen (Environment Variables)
* `EXAMARCHIVE_DEV_MODE`: Bei dem Wert `true` startet die Anwendung im Entwicklungsmodus. Dadurch werden strikte Cookie-Sicherheitsrichtlinien (wie Secure-Flags) deaktiviert.
* `EXAMARCHIVE_DB_HOSTNAME`: Der Hostname der PostgreSQL-Datenbank.
* `EXAMARCHIVE_DB_PORT`: Der Port der PostgreSQL-Datenbank.
* `EXAMARCHIVE_DB_USERNAME`: Der Benutzername zur Datenbankanmeldung.
* `EXAMARCHIVE_DB_PASSWORD`: Das Passwort zur Datenbankanmeldung.
* `EXAMARCHIVE_DB_DATABASE`: Der Name der genutzten Datenbank.
* `KEYCLOAK_ISSUER`: Die URL des Keycloak-Realms für die OIDC-Authentifizierung.
* `KEYCLOAK_CLIENT_ID`: Die Client-ID für die OIDC-Schnittstelle.
* `KEYCLOAK_CLIENT_SECRET`: Das Client-Secret für die OIDC-Schnittstelle.
* `KEYCLOAK_REDIRECT_URI`: Die Callback-URL, auf die der Nutzer nach dem Keycloak-Login weitergeleitet wird.
* `KEYCLOAK_USER_AFFILIATION`: Der zwingend erforderliche Claim-Wert für reguläre Benutzerzugänge (z.B. Kennung für Studierende).
* `KEYCLOAK_ADMIN_AFFILIATION`: Der zwingend erforderliche Claim-Wert für administrative Zugänge.
* `EXAMARCHIVE_STORAGE_ENDPOINT`: Die URL des verwendeten S3/MinIO-Speichers.
* `EXAMARCHIVE_STORAGE_ACCESS_KEY`: Der Zugriffsschlüssel (Access Key) für den S3-Speicher.
* `EXAMARCHIVE_STORAGE_SECRET_KEY`: Der geheime Schlüssel (Secret Key) für den S3-Speicher.
* `EXAMARCHIVE_STORAGE_BUCKET`: Der Name des S3-Buckets. Falls der Bucket nicht existiert, erstellt die Anwendung diesen beim Startvorgang automatisch.
* `EXAMARCHIVE_ADMIN_EMAIL` : Die Domain, mit der sich admins anmelden. Beispielweise: @example.com
* `EXAMARCHIVE_SMTP_HOST` : Der SMTP Host zur Versand von Emails.
* `EXAMARCHIVE_SMTP_PORT` : Der SMTP Port zur Versand von Emails.
* `EXAMARCHIVE_SMTP_USERNAME` : Der SMTP Benutzername zur Authentifizierung.
* `EXAMARCHIVE_SMTP_PASSWORD` : Der SMTP Passwort zur Authentifizierung.
* `EXAMARCHIVE_SMTP_FROM` : Die Email-Adresse, von der aus Emails gesendet werden.


###### **AI-Info:**
- Diese Readme wurde durch Gemini erstellt, aber sorgsam geprüft und manuell im weiteren Verlauf erweitert.
- Kleinere Teile des Frontends wurden mittels Gemini erstellt (bspw. CSS)
- Einige Queries wurden mittels Gemini optimiert (bspw. Klausursuche)