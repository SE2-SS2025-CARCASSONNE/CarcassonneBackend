![App Logo](docs/images/logo_pxart.png)

# 📗 Carcassonne: Install‑Guide (Backend & Frontend)

> **Wichtig:** Die auf itch.io zum Download bereitgestellte APK enthält eine fest kodierte IP. Für ein
> funktionierendes Spiel musst du **Backend + Frontend** selbst kompilieren und an deine
> lokale IP anpassen.

---
[Hier geht's zur detaillierten Projektbeschreibung!](https://github.com/SE2-SS2025-CARCASSONNE/CarcassonneFrontend)

## 📑 Inhaltsverzeichnis

1. [Backend einrichten](#backend-einrichten)
2. [Frontend einrichten](#frontend-einrichten)
3. [Spiel starten](#spiel-starten)
4. [FAQ & Troubleshooting](#faq--troubleshooting)
5. [Links](#links)

---

## 🖥️ Backend einrichten

1. **Repo klonen**
   ```bash
   git clone https://github.com/SE2-SS2025-CARCASSONNE/CarcassonneBackend.git
   cd CarcassonneBackend
   ```

2. **PostgreSQL-Datenbank anlegen**
   ```sql
   CREATE DATABASE carcassonne;
   CREATE USER carcassonne_user WITH PASSWORD 'geheim';
   GRANT ALL PRIVILEGES ON DATABASE carcassonne TO carcassonne_user;
   ```

3. **Zugangsdaten eintragen**  
   Datei: `src/main/resources/application.properties`
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/carcassonne    # 🔧 anpassen
   spring.datasource.username=carcassonne_user                          # 🔧 anpassen
   spring.datasource.password=geheim                                    # 🔧 anpassen
   ```

4. **Backend starten**
   ```bash
   ./gradlew bootRun
   ```
   → Läuft unter `http://<DEINE-IP>:8080`

---

## 📱 Frontend einrichten

1. **Repo klonen**
   ```bash
   git clone https://github.com/SE2-SS2025-CARCASSONNE/CarcassonneFrontend.git
   cd CarcassonneFrontend
   ```

2. **IP‑Adresse anpassen**
    - In `MyClient.kt`:
      ```kotlin
      webSocketURI = "ws://<DEINE-IP>:8080/game"  // 🔧 anpassen
      ```
    - In `ApiClient.kt`:
      ```kotlin
      baseUrl = "http://<DEINE-IP>:8080/api/"   // 🔧 anpassen
      ```

3. **Cleartext Traffic erlauben**  
   Datei: `app/src/main/res/xml/network_security_config.xml`
   ```xml
   <base-config cleartextTrafficPermitted="true" />  <!-- 🔧 -->
   ```

4. **App bauen & installieren**
   ```bash
   ./gradlew installDebug
   ```  
   Oder in Android Studio auf **Run** klicken.

---

## 🎮 Spiel starten

1. Sicherstellen, dass **Backend** unter `http://<DEINE-IP>:8080` erreichbar ist.
2. Android-Gerät oder Emulator im **gleichen LAN** starten.
3. App öffnen → Einloggen → Lobby erstellen/beitreten → Spiel beginnen!

---

## ❓ FAQ & Troubleshooting

| Problem                                   | Lösung                                                             |
|-------------------------------------------|--------------------------------------------------------------------|
| **„Cannot connect“ / Netzwerkfehler**     | 1. Backend läuft? 2. IP korrekt eingetragen? 3. Geräte im LAN?     |
| **Cleartext Traffic blockiert**           | network_security_config.xml + Manifest-Eintrag prüfen             |
| **DB-Migration schlägt fehl**             | Zugangsdaten in `application.properties` prüfen; Schema wird auto. erzeugt |

---

## 🔗 Links

- **Backend‑Repo:** https://github.com/SE2-SS2025-CARCASSONNE/CarcassonneBackend
- **Frontend‑Repo:** https://github.com/SE2-SS2025-CARCASSONNE/CarcassonneFrontend
- **itch.io-Page:** https://j0klar.itch.io/pixel-carcassonne  
