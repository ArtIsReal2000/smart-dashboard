# 📬 Smart Dashboard — Unified AI Email & Deadline Intelligence

A production-grade **Spring Boot 3.2.5** unified mail inbox dashboard with OAuth2, Gmail API & IMAP synchronization, natural language deadline mining (Natty NLP), corporate organization inference, dynamic tag taxonomy, and an ultra-modern glassmorphic UI.

---

## 🌟 Highlights & Features

- **Multi-Account Inbox**: Connect multiple Google accounts via OAuth2 (with automatic offline refresh token persistence encrypted via AES-256-GCM) and custom enterprise IMAP mailboxes.
- **Action Deadline Mining**: Employs Natty natural language date parsing to extract deadlines from phrases like *"due by tomorrow 5 PM"* or *"RSVP by Friday"*, confidence scores them, and alerts with pulsing countdown timers.
- **Calendar Integration**: Instant 1-click `.ics` calendar file generation for any detected deadline.
- **Corporate Organization Inference**: Automatically classifies senders by their corporate email domains (e.g., Stripe, AWS, Linear) while filtering out public webmail providers.
- **Smart Tagging**: Keyword matching and extensible tag taxonomy with custom hex palettes.
- **Ultra-Modern UI**: Designed with Tailwind CSS, Lucide icons, glassmorphism, responsive 3-column email workflow, and instant search.
- **Dual-Profile Database**:
  - `dev` profile: Instant in-memory H2 database with rich mock seed data (emails, organizations, deadlines). Works out-of-the-box without PostgreSQL or Google credentials.
  - `prod` profile: Robust PostgreSQL persistence with auto schema management.

---

## 📁 Project Structure

```
Smart_Dashboard_se/
├── pom.xml
├── mvnw / mvnw.cmd
├── src/main/
│   ├── java/com/smartdashboard/
│   │   ├── SmartDashboardApplication.java
│   │   ├── config/
│   │   │   ├── SecurityConfig.java
│   │   │   ├── OAuthSuccessHandler.java
│   │   │   └── AppConfig.java
│   │   ├── crypto/
│   │   │   └── AesEncryptor.java
│   │   ├── domain/
│   │   │   ├── User.java
│   │   │   ├── MailAccount.java
│   │   │   ├── EmailMessage.java
│   │   │   ├── EmailBody.java
│   │   │   ├── Tag.java
│   │   │   ├── Organization.java
│   │   │   └── Deadline.java
│   │   ├── repository/
│   │   │   ├── UserRepository.java
│   │   │   ├── MailAccountRepository.java
│   │   │   ├── EmailMessageRepository.java
│   │   │   ├── EmailBodyRepository.java
│   │   │   ├── TagRepository.java
│   │   │   ├── OrganizationRepository.java
│   │   │   └── DeadlineRepository.java
│   │   ├── service/
│   │   │   ├── oauth/TokenRefreshService.java
│   │   │   ├── sync/GmailSyncService.java
│   │   │   ├── sync/ImapSyncService.java
│   │   │   ├── sync/SyncOrchestrator.java
│   │   │   ├── parse/DeadlineParserService.java
│   │   │   ├── parse/OrganizationInferenceService.java
│   │   │   ├── parse/TagSuggestionService.java
│   │   │   ├── EmailQueryService.java
│   │   │   ├── ReminderScheduler.java
│   │   │   └── DevDataInitializer.java
│   │   └── web/
│   │       ├── DashboardController.java
│   │       ├── AccountController.java
│   │       ├── EmailApiController.java
│   │       ├── TagApiController.java
│   │       └── SyncApiController.java
│   └── resources/
│       ├── application.yml
│       ├── templates/
│       │   ├── login.html
│       │   ├── dashboard.html
│       │   └── fragment.html
│       └── static/css/dashboard.css
```

---

## 🚀 Quick Start (Development Mode)

1. **Run the Application:**
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
   *(Or on Linux/macOS: `./mvnw spring-boot:run`)*

2. **Open the Dashboard in your Browser:**
   - Navigate to: `http://localhost:8080/login`
   - Click **"Explore Demo Inbox (No OAuth required)"** or go straight to `http://localhost:8080/dev-login`.
   - You will immediately see the UI populated with realistic emails, deadlines, organizations, and tags.
   - H2 Web Console is available at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:smartdash`).

---

## 🔒 Production Setup (PostgreSQL + Google OAuth2)

### 1. Database Setup
```bash
createdb smartdash
```

### 2. Google Cloud Console Credentials
1. Visit [Google Cloud Console](https://console.cloud.google.com/).
2. Create or select a project.
3. Enable the **Gmail API**.
4. Configure **OAuth consent screen** (User Type: External, add your email under Test Users).
5. Add Scope: `https://www.googleapis.com/auth/gmail.readonly`.
6. Create **OAuth 2.0 Client IDs**:
   - Application Type: *Web application*
   - Authorized redirect URIs: `http://localhost:8080/login/oauth2/code/google`

### 3. Environment Variables
```bash
# Windows PowerShell:
$env:SPRING_PROFILES_ACTIVE="prod"
$env:GOOGLE_CLIENT_ID="your-client-id.apps.googleusercontent.com"
$env:GOOGLE_CLIENT_SECRET="your-client-secret"
$env:APP_ENCRYPTION_KEY="32-byte-base64-key"  # openssl rand -base64 32
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/smartdash"
$env:SPRING_DATASOURCE_USERNAME="smartdash"
$env:SPRING_DATASOURCE_PASSWORD="smartdash"
```

### 4. Run in Production Mode
```bash
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=prod
```

---

## 🔌 REST API Reference

| Endpoint | Method | Description |
| :--- | :--- | :--- |
| `/api/emails` | `GET` | List emails (supports `?tagId=`, `?orgId=`, `?hasDeadline=true`, `?q=`, `?sortBy=`) |
| `/api/emails/{id}` | `GET` | Get full email details (HTML & Plain Text body) |
| `/api/emails/{id}/tags/{tagId}` | `POST` | Add tag to an email |
| `/api/emails/{id}/tags/{tagId}` | `DELETE` | Remove tag from an email |
| `/api/tags` | `GET` / `POST` | List or create tags with custom colors |
| `/api/orgs` | `GET` | List inferred organizations |
| `/api/accounts` | `GET` | List connected Gmail & IMAP accounts |
| `/api/accounts/imap` | `POST` | Register an IMAP account with encrypted credentials |
| `/api/sync/now` | `POST` | Manually trigger sync cycle for all mail accounts |
| `/health` | `GET` | System health check |
