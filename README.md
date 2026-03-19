
# Mini-CDN

Public showcase mirror of our distributed systems university project.

## Overview
Mini-CDN is a student project that implements core CDN components such as origin, router, edge, and CLI tooling.

## Note
This repository is a public showcase copy. The original university submission and internal project management stayed on GitLab.

## Explore the Project

For architecture notes, meeting logs, and technical documentation, check out the [Wiki](https://github.com/Origin-Masters/Mini-CDN/wiki).

For the development backlog, implemented features, and tracked tasks, see the [Issues](https://github.com/Origin-Masters/Mini-CDN/issues).


### Was ist Mini CDN ?

Mini-CDN ist ein von Gruppe 8 im Modul Verteilte Systeme entwickeltes vereinfachtes Content Delivery Network (CDN).  
Das System besteht aus einem Origin-Server, einem Router und mehreren Edge-Servern.

Der Origin-Server speichert die Originaldateien. Edge-Server cachen Inhalte regional, um Downloads näher am Benutzer bereitzustellen. Der Router übernimmt die Weiterleitung von Anfragen und entscheidet anhand der Region, welche Edge-Instanz verwendet wird.

Benutzer können über die CLI Dateien herunterladen. Administratoren können Dateien hochladen, die Infrastruktur verwalten sowie Statistiken und Laufzeitinformationen des Systems abrufen.

Im Projekt bezeichnet der Begriff *Server* jeweils einen eigenständigen HTTP-Port.

Die Anwendung basiert auf einer Client-Server-Architektur und ist als Multi-Maven-Projekt umgesetzt.

---

### Voraussetzungen unter Linux / macOS

```shell
java -version   # Java 21
mvn -version
cp .env.example .env
```

---
## CLI Befehle  @ Mini-CDN

### Für einen vollständigen Build inklusive Prüfungen
```
mvn clean verify 
```

### Falls nur die Artefakte gebaut werden sollen
```
mvn clean package
```

### Nur die CLI bauen 
```
mvn -pl cli -am package -DskipTests
```

---
#### Code Formatierung & Analyse

##### Spotless Formartierung 
```
mvn spotless:apply
```

##### Spotbugs Analyse
```
mvn spotbugs:check
```

## Start der Anwendung
```shell
java -jar cli/target/cli-1.0-SNAPSHOT-exec.jar 
```

```shell
system init
```

### Login in der CLI

###### Login als bestehender Benutzer:
```
system login --name admin
```

>[!IMPORTANT]
> Einige Benutzerbefehle wie `user stats`  ... setzen einen Login voraus.

---
###### Prüfen der laufenden Dienste
```
lsof -i:8080 -i:8081 -i:8082
```
###### Health-Endpoint des Origin prüfen:
```
curl http://localhost:8080/api/origin/health
```
###### Health-Endpoint des Routers prüfen:
```
curl http://localhost:8082/api/cdn/health
```

--- 

# Beispielbefehle der CLI 

### Admin: Datei hochladen
```shell
admin file upload --region EU --path docs/a.pdf --file ./a.pdf
```

***Optional mit expliziter Router-URL:***

```
admin file upload --router http://localhost:8082 --region EU --path docs/a.pdf --file ./a.pdf
```

---

### Admin: Dateien anzeigen

###### Dateiliste über den Router:
```shell
admin file list
```

###### Metadaten zu einer Datei anzeigen:
```shell
admin file show --path docs/a.pdf
```

###### Datei über den Router herunterladen:
```shell
admin file download --region EU --path docs/a.pdf --out ./a.pdf
```

###### Datei löschen:
```shell
admin file delete --region EU --path docs/a.pdf
```

---

### Admin: Statistiken anzeigen
```shell
admin stats show
```

###### Mit explizitem Host:
```shell
admin stats show --host http://localhost:8082
```

###### JSON-Ausgabe:
```shell
admin stats show --json
```

---

### Admin: Verwaltete Edge-Instanzen anzeigen
```shell
admin edge managed
```

---

### Admin: Neue Edge-Instanz starten
```shell
admin edge start --region EU --port 8085 --origin http://localhost:8080 --auto-register=true --wait-ready
```

### Admin: Origin-Konfiguration

###### Aktuelle Origin-Konfiguration anzeigen:
```
admin config origin show
```

###### Origin-Konfiguration ändern:
```shell
admin config origin set --max-upload-bytes 1048576 admin config origin set --log-level INFO
```

###### Expliziten Origin angeben:
```shell
admin config origin show --origin http://localhost:8080
```


---

### Admin: Origin-Spare-Verwaltung

###### Registrierte aktive/spare Origins anzeigen:
```shell
admin config origin spare show
```

###### Optional mit Health-Check:
```shell
admin config origin spare show --check-health
```


###### Spare Origin hinzufügen:
```shell
admin config origin spare add --url http://localhost:8084
```


###### Weitere nützliche Befehle:
```shell
admin config origin spare remove --url http://localhost:8084 admin config origin spare promote --url http://localhost:8084 admin config origin spare failover-check
```


---

### Admin: Edge-Konfiguration

###### Aktuelle Edge-Konfiguration anzeigen:
```shell
admin config edge show
```

###### Edge-Konfiguration ändern:
```shell
admin config edge set --default-ttl-ms 120000 --max-entries 200
```


###### Explizite Edge-URL angeben:
```shell
admin config edge show --edge http://localhost:8081
```

###### Optional können auch weitere Parameter gesetzt werden:
```shell
admin config edge set --region EU --replacement-strategy LFU
```


---

### Admin: Verwaltete Edge-Instanzen

###### Neue Edge-Instanz starten:
```shell
admin edge start --region EU --port 8088 --origin http://localhost:8080 --wait-ready
```

###### Verwaltete Edge-Instanzen anzeigen:
```shell
admin edge managed
```

###### Spezifische Edge stoppen:
```shell
admin edge stop edge-94371 --force
```


###### Alle Edges einer Region stoppen:
```shell
admin edge stop-region --region EU --force
```

---

### Admin: Benutzerverwaltung

###### Neuen Benutzer anlegen:
```shell
admin user add --name alice --role 1
```

Rollen:

- 0 = USER
- 1 = ADMIN

###### Benutzer auflisten:
```shell
admin user list
```

###### Benutzer löschen:
```shell
admin user delete --id 3
```


---

### Admin: Health-Checks mit Ping

###### Router prüfen:
```shell
admin ping -H http://localhost:8082 -p api/cdn/health
```

###### Origin prüfen:
```shell
admin ping -H http://localhost:8080 -p api/origin/health
```


---

### User: Datei herunterladen

###### Datei über den Router herunterladen:
```shell
user file download --region EU --path pdfTest.pdf --out ./manual3.pdf --host http://localhost:8082

```


---

### User: Dateistatistiken

##### Statistiken für eine bestimmte Datei anzeigen:
```shell
user stats file --file-id 1
```

###### Dateistatistikliste anzeigen:
```shell
user stats list user stats list --limit 20
```

Gesamtstatistik des aktuell eingeloggten Benutzers anzeigen:
```shell
user stats overall 

user stats overall --window-sec 7200
```
