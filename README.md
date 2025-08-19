# 📗 Carcassonne: Server Setup Guide

👉 [Projekt-Homepage auf itch.io (+ APK Download)](https://j0klar.itch.io/pixel-carcassonne)

👉 [Frontend-Repository mit Projektbeschreibung](https://github.com/SE2-SS2025-CARCASSONNE/CarcassonneFrontend)

## 🖥️ Server einrichten

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

## 📱 App einrichten

1. **[Aktuelle APK von itch.io downloaden & installieren](https://j0klar.itch.io/pixel-carcassonne)**

---

## 🎮 Spiel starten

1. Sicherstellen, dass **Backend** unter `http://<DEINE-IP>:8080` erreichbar ist
2. Android-Gerät oder Emulator im **gleichen LAN** starten
4. App öffnen → **Server IP eintragen** → Registrieren/Einloggen → Lobby erstellen/beitreten → Spiel beginnen!

---

## ❓ FAQ & Troubleshooting

| Problem                                  | Lösung                                                                           |
|------------------------------------------|----------------------------------------------------------------------------------|
| **„Failed to connect“ / Netzwerkfehler** | 1. Server läuft? 2. IP korrekt eingetragen? 3. Geräte im LAN?                    |
| **Datenbankfehler**                      | Zugangsdaten in `application.properties` prüfen; Schema wird automatisch erzeugt |

