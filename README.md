# Control de presencia

Proyecto extraordinario de Ingeniería de Software 2 (EII, ULPGC). Sistema de control de presencia
con un **backend HTTP/JSON** de arquitectura hexagonal, una **base de datos H2** y **tres aplicaciones
frontend separadas**: registro de asistencia, consulta de presencia e incidencias, y definición de
horarios.

## Índice

1. [Objetivo](#1-objetivo)
2. [Dependencias y versión de JDK](#2-dependencias-y-versión-de-jdk)
3. [Cómo compilar y ejecutar](#3-cómo-compilar-y-ejecutar)
4. [Arquitectura de sistemas](#4-arquitectura-de-sistemas)
5. [Arquitectura del backend (hexagonal)](#5-arquitectura-del-backend-hexagonal)
6. [Estructura de la entrega y clases principales](#6-estructura-de-la-entrega-y-clases-principales)
7. [Modelo de datos](#7-modelo-de-datos)
8. [Tipos de horario soportados](#8-tipos-de-horario-soportados)
9. [Reglas de cómputo implementadas](#9-reglas-de-cómputo-implementadas)
10. [Contrato de comunicación](#10-contrato-de-comunicación)
11. [Idea propia](#11-idea-propia-incidencias-justificadas-y-calendario-de-festivos)
12. [Capturas](#12-capturas)
13. [Verificación realizada](#13-verificación-realizada)
14. [Límites conocidos](#14-límites-conocidos)

---

## 1. Objetivo

Registrar entradas y salidas de personas, evaluarlas contra el horario que tienen asignado y consultar
el resultado: presencia actual, ausencias, retrasos, salidas anticipadas, salidas pendientes, horas
trabajadas y desviación respecto a lo esperado.

La dificultad está en el dominio: el sistema soporta **horario corrido** (oficina), **horario partido**
(tienda o restaurante), **turnos rotatorios** y **guardias** (hospital, de presencia física o
localizadas), además de festivos e incidencias justificadas.

## 2. Dependencias y versión de JDK

| Herramienta / librería | Versión | Uso |
|---|---|---|
| JDK | **21 o superior** (se compila con `release 21`; funciona también con JDK 25/26) | Lenguaje y servidor HTTP del JDK (`com.sun.net.httpserver`) |
| Maven | 3.9 o superior (IntelliJ IDEA ya lo incluye) | Compilación, pruebas y empaquetado |
| Jackson (`jackson-databind`, `jackson-datatype-jsr310`) | 2.18.1 | JSON del contrato HTTP |
| H2 Database | 2.3.232 | Base de datos embebida en fichero |
| JUnit Jupiter | 5.11.3 | Pruebas automatizadas |

No hacen falta servidores externos: la base de datos se crea sola en `./data/` y los frontends son
HTML + JavaScript (módulos ES) servidos por un pequeño servidor Java. Solo se necesita un navegador.
Node.js **no** es necesario (únicamente para el script opcional que regenera las capturas).

## 3. Cómo compilar y ejecutar

### Con IntelliJ IDEA

1. *File → Open…* y selecciona la carpeta del repositorio (IntelliJ detecta el `pom.xml` multimódulo).
2. *File → Project Structure → Project SDK*: un JDK 21 o superior.
3. En el desplegable de ejecución aparecen las configuraciones compartidas de la carpeta `.run/`:
   - **Sistema completo**: arranca backend y frontends a la vez.
   - **Backend** (`software.ulpgc.presencia.BackendApplication`) y **Frontend**
     (`software.ulpgc.presencia.frontend.FrontendServer`) por separado.
   - **Todas las pruebas**: `mvn test` de los dos módulos.
4. Abre <http://localhost:8081/>.

Las configuraciones usan `$PROJECT_DIR$` como directorio de trabajo, por lo que la base de datos se
crea en `<repositorio>/data/` (excluida de Git).

### Desde la terminal

```bash
mvn clean package
```

```bash
java -jar backend/target/presencia-backend.jar
```

```bash
java -jar frontend/target/presencia-frontend.jar
```

`mvn clean package` compila y ejecuta las 52 pruebas. Cada proceso se lanza en una terminal distinta y
el backend se puede ejecutar de forma independiente (por ejemplo, para usar la API con `curl`). Sin
empaquetar, también vale `mvn -pl backend compile exec:java` y `mvn -pl frontend compile exec:java`.

| Aplicación | URL |
|---|---|
| Inicio (enlaces a las tres apps) | <http://localhost:8081/> |
| Registro de asistencia | <http://localhost:8081/fichaje/> |
| Consulta de presencia e incidencias | <http://localhost:8081/consulta/> |
| Definición de horarios | <http://localhost:8081/horarios/> |
| API del backend | <http://localhost:8080/api/health> |

### Configuración: puertos, base de datos y datos iniciales

Los valores por defecto están en `backend/src/main/resources/application.properties` y
`frontend/src/main/resources/frontend.properties`. Cada clave se puede sobrescribir con `-Dclave=valor`
o con una variable de entorno (mayúsculas y `_`), sin tocar el código ni usar rutas absolutas:

| Clave | Variable de entorno | Por defecto | Descripción |
|---|---|---|---|
| `presencia.http.host` | `PRESENCIA_HTTP_HOST` | `localhost` | Interfaz del backend |
| `presencia.http.port` | `PRESENCIA_HTTP_PORT` | `8080` | Puerto del backend |
| `presencia.cors.allowed-origin` | `PRESENCIA_CORS_ALLOWED_ORIGIN` | `*` | Origen permitido por CORS |
| `presencia.db.url` | `PRESENCIA_DB_URL` | `jdbc:h2:file:./data/presencia;DB_CLOSE_DELAY=-1` | Base de datos (relativa al directorio de trabajo) |
| `presencia.db.user` / `presencia.db.password` | `PRESENCIA_DB_USER` / `PRESENCIA_DB_PASSWORD` | `sa` / vacío | Credenciales de la BD local de desarrollo |
| `presencia.zone` | `PRESENCIA_ZONE` | `Atlantic/Canary` | Zona horaria de los horarios |
| `presencia.seed` | `PRESENCIA_SEED` | `true` | Cargar datos de ejemplo si la BD está vacía |
| `presencia.frontend.port` | `PRESENCIA_FRONTEND_PORT` | `8081` | Puerto de los frontends |
| `presencia.api.url` | `PRESENCIA_API_URL` | `http://localhost:8080/api` | URL de la API que usan los frontends |

Ejemplo: `java -Dpresencia.http.port=9090 -jar backend/target/presencia-backend.jar` y
`java -Dpresencia.api.url=http://localhost:9090/api -jar frontend/target/presencia-frontend.jar`.

Para **empezar de cero** basta con borrar la carpeta `data/` con el backend parado.

**Datos iniciales** (todos ficticios, cargados a través de los casos de uso y con fechas relativas al
día de arranque para que la demostración funcione cualquier día):

| Persona | Equipo | Horario | Situación de ejemplo |
|---|---|---|---|
| EMP-001 Persona Oficina A | administracion | Oficina (asignado **al equipo**) | Fichajes puntuales |
| EMP-002 Persona Oficina B | administracion | Oficina (asignado al equipo) | Llega 27 min tarde el último día laborable |
| EMP-003 Persona Restaurante | sala | Restaurante (partido) | Sale 45 min antes en el último tramo |
| EMP-004 Persona Enfermería | planta-3 | Turnos rotatorios | **Olvidó fichar la salida** en su último turno |
| EMP-005 Persona Medicina A | urgencias | Guardia de presencia física 24 h | Abandona la guardia 2 h antes |
| EMP-006 Persona Medicina B | urgencias | Guardia localizada 17 h | Sin fichajes (no se exigen) |
| EMP-007 Persona Sin Horario | — | — | Para probar `SCHEDULE_NOT_ASSIGNED` |
| EMP-008 Persona Contrato Finalizado | — | Oficina hasta hace 31 días | Para probar `DATE_OUT_OF_PERIOD` |

También se cargan cuatro festivos del calendario y la historia de los últimos seis días.

## 4. Arquitectura de sistemas

```mermaid
flowchart LR
    subgraph Navegador
        F1["Registro de asistencia<br/>/fichaje/"]
        F2["Consulta de presencia<br/>/consulta/"]
        F3["Definición de horarios<br/>/horarios/"]
        AC["shared/api-client.js<br/>(adaptador de comunicación)"]
        F1 & F2 & F3 --> AC
    end
    FS["presencia-frontend<br/>servidor estático :8081<br/>(HTML, JS, /config.js)"]
    BE["presencia-backend<br/>API HTTP/JSON :8080"]
    DB[("H2 en fichero<br/>./data/presencia.mv.db")]
    FS -. sirve ficheros .-> Navegador
    AC -- "HTTP + JSON (contrato §10)<br/>cabecera X-Actor" --> BE
    BE -- JDBC --> DB
```

- **Tres aplicaciones frontend diferenciadas**, cada una con su HTML y su módulo JavaScript. No
  comparten estado; solo comparten el adaptador de comunicación y utilidades de presentación.
- **Los frontends nunca acceden a la base de datos**: solo llaman a la API mediante
  `HttpPresenceApi` (`frontend/src/main/resources/web/shared/api-client.js`). Las vistas no hacen
  `fetch` ni conocen rutas HTTP; para cambiar de transporte o usar un mock basta con otro objeto con
  los mismos métodos. La URL de la API se inyecta en tiempo de ejecución con `/config.js`.
- **El backend no depende de los frontends**: es ejecutable de forma independiente y solo expone JSON.
  Como frontends y backend están en puertos distintos, el backend responde a CORS.
- **Base de datos**: H2 embebida en un fichero local; el esquema (`backend/src/main/resources/db/schema.sql`)
  se crea al arrancar.

## 5. Arquitectura del backend (hexagonal)

```mermaid
flowchart LR
    subgraph IN["Adaptadores de entrada"]
        HTTP["infrastructure.http<br/>HttpApiServer, Router, ApiRoutes, dto"]
        SEED["infrastructure.seed<br/>DemoData"]
    end
    subgraph APP["Aplicación"]
        PIN["port.in<br/>RegisterClockEventUseCase<br/>AttendanceQueryUseCase<br/>ScheduleAdministrationUseCase<br/>PersonAdministrationUseCase<br/>JustifyIncidentUseCase"]
        SVC["service<br/>*Service (casos de uso)"]
        POUT["port.out<br/>PersonRepository, ScheduleRepository,<br/>AssignmentRepository, HolidayRepository,<br/>ClockEventRepository, JustificationRepository,<br/>AuditTrail, Transactions"]
        PIN --> SVC --> POUT
    end
    subgraph DOM["Dominio (sin dependencias externas)"]
        D["schedule · assignment · clocking<br/>attendance (AttendanceEvaluator) · calendar · person · error"]
    end
    subgraph OUT["Adaptadores de salida"]
        JDBC["infrastructure.persistence<br/>Jdbc*Repository, JdbcAuditTrail, Database"]
    end
    HTTP --> PIN
    SEED --> PIN
    SVC --> D
    JDBC -. implementa .-> POUT
```

Reglas de dependencia (comprobadas por `ArchitectureTest`):

- `domain` solo usa el JDK: no importa nada de `application`, `infrastructure`, Jackson, H2, JDBC ni HTTP.
- `application` solo depende de `domain` y de sus propios puertos.
- Los adaptadores HTTP y de persistencia no se conocen entre sí.
- `Backend` es la **raíz de composición**: el único sitio que instancia implementaciones concretas.

Los casos de uso se han probado también con **adaptadores en memoria**
(`backend/src/test/.../support/InMemoryAdapters.java`), lo que demuestra que los puertos de salida son
sustituibles.

## 6. Estructura de la entrega y clases principales

```
control-presencia/
├── pom.xml                         POM padre (módulos backend y frontend, JDK 21)
├── .run/                           Configuraciones de ejecución de IntelliJ
├── backend/
│   └── src/main/java/software/ulpgc/presencia/
│       ├── BackendApplication.java Punto de entrada
│       ├── Backend.java            Raíz de composición
│       ├── domain/                 Modelo y reglas (sin dependencias)
│       ├── application/
│       │   ├── port/in/            Casos de uso (interfaces y comandos)
│       │   ├── port/out/           Puertos de salida
│       │   └── service/            Implementación de los casos de uso
│       └── infrastructure/
│           ├── http/               Adaptador HTTP (servidor, rutas, DTO, errores)
│           ├── persistence/        Adaptador JDBC/H2
│           ├── json/               Representación JSON de horarios
│           ├── config/             AppConfig
│           └── seed/               Datos de ejemplo
├── frontend/
│   ├── src/main/java/.../frontend/FrontendServer.java
│   └── src/main/resources/web/
│       ├── shared/ api-client.js · ui.js · styles.css
│       ├── fichaje/                Registro de asistencia
│       ├── consulta/               Consulta de presencia e incidencias
│       └── horarios/               Definición de horarios
└── docs/capturas/                  Capturas y script que las genera
```

| Clase | Responsabilidad |
|---|---|
| `Schedule` (interfaz sellada) y `ContinuousSchedule`, `SplitSchedule`, `RotatingShiftSchedule`, `OnCallSchedule` | Cada tipo de horario y sus reglas: qué periodos de trabajo empiezan cada día |
| `ScheduleAssignment`, `ScheduleAssignments` | Asignación a persona o equipo en un periodo; la individual prevalece |
| `ClockEvent`, `ClockingPolicy`, `WorkSession` | Fichaje inmutable, coherencia de la secuencia y emparejado entrada-salida |
| `AttendanceEvaluator` | Servicio de dominio: incidencias, horas, desviación, estado diario y presencia actual |
| `RegisterClockEventService` | Caso de uso fichar (cerrojo por persona, validación, auditoría) |
| `AttendanceQueryService` | Presencia actual, informes, fichajes, incidencias y auditoría |
| `ScheduleAdministrationService` | Alta de horarios, asignaciones (sin solapes) y festivos |
| `IncidentJustificationService` | Justificación de incidencias (idea propia) |
| `HttpApiServer`, `Router`, `ApiRoutes` | Adaptador HTTP y traducción de errores de dominio a códigos HTTP |
| `Database`, `Jdbc*Repository`, `JdbcAuditTrail` | Persistencia JDBC con transacciones |
| `HttpPresenceApi` (JS) | Adaptador de comunicación sustituible de los frontends |

## 7. Modelo de datos

**Dominio**

```mermaid
classDiagram
    class Person { id; displayName; team }
    class Schedule { <<sealed>> id; name; kind; tolerance; periodsStartingOn(date); exemptOnHolidays() }
    class ContinuousSchedule { workDays; slot: TimeSlot }
    class SplitSchedule { workDays; slots: TimeSlot[2..] }
    class RotatingShiftSchedule { cycleStart; cycle: CycleDay[] }
    class OnCallSchedule { start; duration; mode; dates }
    class TimeSlot { start; end }
    class Shift { label; start; duration }
    class ScheduleAssignment { scheduleId; targetType PERSON|TEAM; targetId; validFrom; validTo }
    class ClockEvent { personId; type ENTRY|EXIT; occurredAt; recordedAt; source; note }
    class Incident { key; type; date; minutes; justification }
    class Holiday { date; description }
    Schedule <|-- ContinuousSchedule
    Schedule <|-- SplitSchedule
    Schedule <|-- RotatingShiftSchedule
    Schedule <|-- OnCallSchedule
    ContinuousSchedule --> TimeSlot
    SplitSchedule --> TimeSlot
    RotatingShiftSchedule --> Shift : CycleDay.Work
    ScheduleAssignment --> Schedule
    ScheduleAssignment --> Person : persona o equipo
    ClockEvent --> Person
    Incident --> Person
```

Las **incidencias se calculan** (no se guardan) a partir de fichajes, horario, festivos y la hora
actual; así nunca quedan desfasadas si se corrige un fichaje. Su clave estable
(`persona|fecha|tipo|hora`, p. ej. `EMP-002|2026-11-16|LATE_ARRIVAL|08:00`) permite guardar su justificación.

**Tablas** (`schema.sql`)

| Tabla | Contenido |
|---|---|
| `person` | id, display_name, team |
| `schedule` | id, name, kind, tolerance_minutes y `definition` (JSON con la estructura propia de cada tipo) |
| `schedule_assignment` | schedule_id, target_type (PERSON/TEAM), target_id, valid_from, valid_to |
| `holiday` | holiday_date, description |
| `clock_event` | person_id, event_type, occurred_at, recorded_at, source, note — **solo se inserta** |
| `incident_justification` | incident_key, person_id, reason, justified_by, justified_at |
| `audit_log` | logged_at, actor, action, entity_type, entity_id, detail — cada modificación |

Los instantes se guardan en UTC (`TIMESTAMP WITH TIME ZONE`) y se devuelven en la zona configurada.
Cada operación de escritura y su entrada de auditoría se guardan en la **misma transacción**.

## 8. Tipos de horario soportados

Cada tipo es una clase con reglas explícitas. El JSON mostrado es el cuerpo de `POST /api/schedules`.

### Corrido — oficina con jornada continua

Un único tramo los días laborables. En festivo no se trabaja.

```json
{ "name": "Oficina - jornada continua", "kind": "CONTINUOUS", "toleranceMinutes": 10,
  "workDays": ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"],
  "slots": [ { "start": "08:00", "end": "15:00" } ] }
```

### Partido — restaurante con mañana y tarde

Dos o más tramos el mismo día **separados por una pausa** (se rechazan tramos contiguos o solapados).
Cada tramo se evalúa por separado: se puede llegar tarde al segundo o no volver tras la pausa.

```json
{ "name": "Restaurante - horario partido", "kind": "SPLIT", "toleranceMinutes": 5,
  "workDays": ["TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"],
  "slots": [ { "start": "12:00", "end": "16:00" }, { "start": "19:30", "end": "23:30" } ] }
```

### Turnos rotatorios — hospital

Un ciclo de N días que se repite desde `cycleStart` (día del ciclo = (fecha − inicio) mod N). Un turno
puede **cruzar la medianoche** y pertenece al día en que empieza. Se rechaza un ciclo en el que un turno
se solape con el del día siguiente. Los festivos no anulan los turnos (cobertura continua).

```json
{ "name": "Hospital - turnos rotatorios", "kind": "ROTATING_SHIFT", "toleranceMinutes": 10,
  "cycleStart": "2026-11-16",
  "cycle": [ { "label": "Mañana", "start": "07:00", "durationMinutes": 480 },
             { "label": "Mañana", "start": "07:00", "durationMinutes": 480 },
             { "label": "Tarde",  "start": "15:00", "durationMinutes": 420 },
             { "label": "Tarde",  "start": "15:00", "durationMinutes": 420 },
             { "label": "Noche",  "start": "22:00", "durationMinutes": 600 },
             { "rest": true }, { "rest": true } ] }
```

### Guardias — hospital

Guardias en fechas concretas, hasta 24 h. Dos modalidades con reglas distintas:

- `PHYSICAL` (presencia física): se exige fichar; se evalúan ausencia, retraso y salida anticipada.
- `LOCALIZED` (localizada): no se exige presencia, no genera ausencias; si se acude, el tiempo fichado
  computa como trabajado (activación).

```json
{ "name": "Hospital - guardia de presencia física 24 h", "kind": "ON_CALL", "toleranceMinutes": 15,
  "start": "08:00", "durationMinutes": 1440, "mode": "PHYSICAL",
  "dates": ["2026-11-11", "2026-11-15", "2026-11-19"] }
```

## 9. Reglas de cómputo implementadas

Encapsuladas en `ClockingPolicy` (al fichar) y `AttendanceEvaluator` (al consultar):

**Al fichar**
1. La persona debe existir (`PERSON_NOT_FOUND`).
2. Entrada y salida se alternan: una segunda entrada sin salida es `DUPLICATE_CLOCK_EVENT`; una salida
   sin entrada abierta es `INCONSISTENT_CLOCK_EVENT`.
3. Los fichajes se añaden en orden cronológico (no se puede insertar uno anterior al último) y no
   pueden estar en el futuro.
4. Una **entrada** exige un horario vigente ese día: sin ninguna asignación → `SCHEDULE_NOT_ASSIGNED`;
   con asignaciones, pero ninguna cubre la fecha → `DATE_OUT_OF_PERIOD`. La salida no lo exige (cierra
   la entrada abierta, aunque sea de un turno de noche).
5. Se puede indicar la hora del fichaje (corrección de un olvido, origen `ADMIN`); queda guardada
   también la hora real de registro (`recordedAt`) y se audita.

**Al evaluar**
1. Los fichajes se emparejan en sesiones y cada sesión se asocia al periodo esperado con el que más se
   solapa (también del día anterior o siguiente, para turnos de noche y guardias). Una entrada hasta 2 h
   antes del inicio se asocia a ese periodo.
2. Sobre cada periodo ya iniciado, con la tolerancia del horario:
   - **Ausencia**: ninguna entrada pasada la tolerancia.
   - **Retraso**: primera entrada posterior a inicio + tolerancia.
   - **Salida anticipada**: última salida anterior a fin − tolerancia (solo cuando el periodo terminó).
   - **Salida pendiente**: entrada abierta pasado fin + tolerancia.
   - **Fuera de horario**: sesión que no corresponde a ningún periodo (informativo).
3. Las horas trabajadas solo cuentan sesiones **cerradas**; la desviación (trabajado − esperado) solo se
   calcula para días terminados.
4. Estado diario (el más grave): Ausente > Salida pendiente > Retraso > Salida anticipada > Justificado
   > En curso / Pendiente / Cumplido / Descanso / Festivo / Sin horario.
5. Asignaciones: la individual prevalece sobre la del equipo; las de un mismo destino no se solapan.

## 10. Contrato de comunicación

HTTP + JSON, prefijo `/api`, UTF-8. Las fechas son `AAAA-MM-DD`, las horas `HH:mm` y los instantes
ISO-8601 con desplazamiento (`2026-11-17T08:05:00Z`). La cabecera opcional **`X-Actor`** identifica
quién hace la operación para la auditoría (los frontends envían `app-fichaje`, `app-consulta` y
`app-horarios`). Los `POST` exigen `Content-Type: application/json` y se rechazan campos desconocidos.

| Método | Ruta | Cuerpo / parámetros | Respuesta |
|---|---|---|---|
| GET | `/api/health` | — | Estado, zona, hora del servidor y valores de los enumerados |
| GET | `/api/people` | — | `[Person]` |
| POST | `/api/people` | `{"id":"EMP-009","displayName":"…","team":"…"}` | 201 `Person` |
| GET | `/api/schedules` | — | `[Schedule]` (§8, con `id` y `summary`) |
| GET | `/api/schedules/{id}` | — | `Schedule` |
| POST | `/api/schedules` | Uno de los cuerpos del §8 | 201 `Schedule` |
| GET | `/api/assignments` | `?personId=` opcional | `[Assignment]` |
| POST | `/api/assignments` | `{"scheduleId","personId" o "team","validFrom","validTo"?}` | 201 `Assignment` |
| GET | `/api/holidays` | — | `[{"date","description"}]` |
| POST | `/api/holidays` | `{"date":"2026-12-25","description":"Navidad"}` | 201 |
| POST | `/api/clock-events` | `{"personId","type":"ENTRY"\|"EXIT","source":"KIOSK"\|"WEB"\|"ADMIN"\|"IMPORT","timestamp"?,"note"?}` | 201 `{"event": ClockEvent, "presence": Presence}` |
| GET | `/api/clock-events` | `?personId=&from=&to=` (por defecto hoy) | `[ClockEvent]` |
| GET | `/api/presence` | — | `[Presence]` de todas las personas |
| GET | `/api/presence/{personId}` | — | `Presence` |
| GET | `/api/reports/daily` | `?personId=&from=&to=` (máx. 366 días) | `Report` con totales y días |
| GET | `/api/incidents` | `?personId=&from=&to=&type=` (por defecto últimos 7 días) | `[Incident]` |
| POST | `/api/incidents/justifications` | `{"incidentKey","reason"}` | 201 `Justification` |
| GET | `/api/audit` | `?limit=` (1–500, por defecto 50) | `[AuditEntry]` más recientes primero |

Ejemplo de fichaje y su respuesta:

```http
POST /api/clock-events
Content-Type: application/json
X-Actor: app-fichaje

{"personId":"EMP-001","type":"ENTRY","source":"KIOSK"}
```

```json
{ "event": { "id": "59ce…", "personId": "EMP-001", "type": "ENTRY", "occurredAt": "2026-11-17T08:25:00Z",
             "recordedAt": "2026-11-17T08:25:00Z", "source": "KIOSK", "note": null },
  "presence": { "personId": "EMP-001", "displayName": "Persona Oficina A", "status": "PRESENT", "inside": true,
                "since": "2026-11-17T08:25:00Z", "currentPeriod": "08:00-15:00",
                "currentPeriodStart": "2026-11-17T08:00:00Z", "currentPeriodEnd": "2026-11-17T15:00:00Z",
                "todayStatus": "LATE" } }
```

Un día del informe (`GET /api/reports/daily`) contiene `date`, `scheduleName`, `holiday`, `status`,
`expectedMinutes`, `workedMinutes`, `deviationMinutes` (null si el día no ha terminado), `openSession`,
`periods` (esperado y fichado de cada periodo) e `incidents`.

Estados de presencia (`status`): `PRESENT`, `PRESENT_OUT_OF_SCHEDULE`, `CHECKOUT_PENDING`, `ABSENT`,
`EXPECTED`, `ON_CALL`, `OFF_DUTY`, `NO_SCHEDULE`. Estados diarios: `NO_SCHEDULE`, `HOLIDAY`, `REST`,
`PENDING`, `IN_PROGRESS`, `COMPLETED`, `EXTRA_WORK`, `JUSTIFIED`, `EARLY_DEPARTURE`, `LATE`,
`CHECKOUT_PENDING`, `ABSENT`. Tipos de incidencia: `ABSENCE`, `LATE_ARRIVAL`, `EARLY_DEPARTURE`,
`MISSING_CHECKOUT`, `UNSCHEDULED_WORK`.

### Errores

Todos los errores tienen el mismo cuerpo:

```json
{ "error": "DUPLICATE_CLOCK_EVENT", "message": "Ya existe una entrada abierta desde 17/11/2026 08:25; registra antes la salida", "status": 409 }
```

| HTTP | `error` | Cuándo |
|---|---|---|
| 400 | `INVALID_DATA` | Datos incompletos o mal formados, JSON roto, campo desconocido, rango de fechas inválido, tramos incoherentes… |
| 404 | `PERSON_NOT_FOUND`, `SCHEDULE_NOT_FOUND`, `INCIDENT_NOT_FOUND`, `NOT_FOUND` | Persona, horario, incidencia o ruta inexistente |
| 405 | `METHOD_NOT_ALLOWED` | Método no admitido en la ruta |
| 409 | `DUPLICATE_CLOCK_EVENT` | Entrada con otra entrada abierta |
| 409 | `INCONSISTENT_CLOCK_EVENT` | Salida sin entrada previa, o fichaje anterior al último |
| 409 | `PERSON_ALREADY_EXISTS`, `HOLIDAY_ALREADY_EXISTS`, `ASSIGNMENT_OVERLAP`, `INCIDENT_ALREADY_JUSTIFIED` | Conflictos de alta |
| 413 | `PAYLOAD_TOO_LARGE` | Cuerpo de más de 64 KB |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | `POST` sin `application/json` |
| 422 | `SCHEDULE_NOT_ASSIGNED` | La persona no tiene ningún horario asignado |
| 422 | `DATE_OUT_OF_PERIOD` | La fecha está fuera de los periodos de sus asignaciones |
| 500 | `INTERNAL_ERROR` | Error no controlado (sin detalles internos; se registra en el log) |

## 11. Idea propia: incidencias justificadas y calendario de festivos

**Incidencias justificadas.** Desde *Consulta → Incidencias* se puede justificar una ausencia, un
retraso o una salida (cita médica, formación, atasco…). La justificación guarda motivo, autor y fecha,
queda en la auditoría, no se puede repetir, y el día pasa a estado *Justificado* sin perder la
incidencia original. Encaja con el dominio porque un control de presencia real no se limita a detectar
desviaciones: necesita registrar por qué ocurrieron, y hacerlo sin modificar los fichajes (que siguen
siendo de solo añadir) mantiene la trazabilidad.

**Calendario de festivos con reglas por tipo de horario.** Un festivo anula los periodos de los horarios
corridos y partidos (la oficina y la tienda cierran), pero **no** los turnos ni las guardias, porque un
hospital mantiene la cobertura. Es una regla explícita de cada tipo (`Schedule.exemptOnHolidays()`).

Además, se registra la **auditoría** de todas las modificaciones (consultable en *Consulta → Auditoría*)
y se permite la **corrección de olvidos** con un fichaje de origen `ADMIN` con hora indicada.

## 12. Capturas

Generadas con el sistema real en ejecución (backend + frontends) y los datos de ejemplo, mediante
`docs/capturas/generar-capturas.cjs`.

### Registro de asistencia

Entrada registrada: el estado pasa a *Presente* y el día se marca como *Retraso*.

![Registro de asistencia](docs/capturas/fichaje.png)

Segundo intento de entrada: rechazado con `DUPLICATE_CLOCK_EVENT`.

![Fichaje duplicado rechazado](docs/capturas/fichaje-error.png)

### Consulta de presencia e incidencias

Presencia actual de todas las personas (EMP-004 aparece con *Salida pendiente* porque no fichó la salida ayer).

![Presencia actual](docs/capturas/consulta-presencia.png)

Informe por persona y periodo: esperado, trabajado, desviación e incidencias de cada día.

![Informe por persona y periodo](docs/capturas/consulta-informe.png)

Incidencias de todas las personas, con una ya justificada.

![Incidencias](docs/capturas/consulta-incidencias.png)

Auditoría de modificaciones.

![Auditoría](docs/capturas/consulta-auditoria.png)

### Definición de horarios

Horarios definidos de los cuatro tipos, asignaciones (por persona y por equipo), festivos y personas.

![Definición de horarios](docs/capturas/horarios.png)

Formulario de turnos rotatorios.

![Formulario de turnos rotatorios](docs/capturas/horarios-turnos.png)

## 13. Verificación realizada

### Pruebas automatizadas (`mvn test`: 52 pruebas, todas correctas)

| Clase de prueba | Qué verifica |
|---|---|
| `ScheduleTypesTest` (7) | Periodos de cada tipo de horario, ciclos antes de su inicio, validaciones (tramos sin pausa, solapes de turnos, tolerancia) |
| `ClockingPolicyTest` (4) | Entrada duplicada, salida sin entrada, orden cronológico y fichajes futuros |
| `AttendanceEvaluatorTest` (20) | Puntualidad, retraso, salida anticipada, ausencia, salida pendiente, festivos, trabajo extra, horario partido por tramos, turno de noche que cruza la medianoche, guardias física y localizada, justificaciones, estados de presencia |
| `UseCasesTest` (9) | Casos de uso con **adaptadores en memoria**: flujo completo, todos los errores de fichaje, prioridad persona/equipo, solapes de asignaciones, festivos, justificación única y auditoría |
| `HttpApiIntegrationTest` (6) | Backend real (HTTP + H2 en fichero temporal): flujo de fichaje y consulta, **todos los códigos de error del contrato**, ida y vuelta de los 4 tipos de horario, **persistencia tras reiniciar**, carga única de datos iniciales y CORS |
| `ArchitectureTest` (3) | Reglas de dependencia de la arquitectura hexagonal |
| `FrontendServerTest` (3) | Las tres aplicaciones y el adaptador se sirven, `/config.js`, redirecciones y rutas no válidas |

### Comprobaciones manuales (sistema completo desde una copia limpia)

Realizadas con `mvn clean package`, los dos `java -jar` y un navegador (además del script de capturas):

| Comprobación | Resultado |
|---|---|
| Backend y frontends arrancan desde una copia limpia y se comunican | ✔ |
| Se cargan los tres tipos de horario de ejemplo (más la guardia localizada) | ✔ 5 horarios |
| Horarios asignados a personas ficticias (individual y por equipo) | ✔ |
| Entrada y salida correctas | ✔ |
| Retraso (EMP-001 ficha tarde) y cómo aparece en la consulta | ✔ *Retraso* en presencia, informe e incidencias |
| Salida anticipada (EMP-003, EMP-005) y salida pendiente (EMP-004) | ✔ |
| Consulta por persona y por periodo | ✔ |
| Persona inexistente | ✔ 404 `PERSON_NOT_FOUND` |
| Fichaje duplicado | ✔ 409 `DUPLICATE_CLOCK_EVENT` (captura) |
| Salida sin entrada previa | ✔ 409 `INCONSISTENT_CLOCK_EVENT` |
| Horario no asignado (EMP-007) | ✔ 422 `SCHEDULE_NOT_ASSIGNED` |
| Fecha fuera de periodo (EMP-008) | ✔ 422 `DATE_OUT_OF_PERIOD` |
| Datos incompletos / JSON incorrecto | ✔ 400 `INVALID_DATA` |
| Guardia sin fechas desde el formulario | ✔ rechazada con mensaje |
| Corrección de olvido: salida `ADMIN` con hora de ayer para EMP-004 | ✔ aceptada; `recordedAt` conserva la hora real |
| Reinicio del backend: fichajes, horarios y auditoría persisten | ✔ mismos 49 fichajes y 5 horarios tras reiniciar |

Para repetirlas a mano con `curl`:

```bash
curl -s -X POST localhost:8080/api/clock-events -H 'Content-Type: application/json' -d '{"personId":"EMP-007","type":"ENTRY","source":"KIOSK"}'
```

## 14. Límites conocidos

- **Seguridad y permisos**: no hay autenticación ni roles; cualquiera con acceso a la API puede fichar
  por otra persona o definir horarios. `X-Actor` es declarativo (no verificado). Por eso el backend
  escucha por defecto solo en `localhost`. Un despliegue real necesitaría autenticación (p. ej. OIDC),
  roles empleado/responsable y HTTPS; CORS está abierto (`*`) para facilitar el desarrollo local.
- **Concurrencia**: los fichajes de una misma persona se serializan con un cerrojo en memoria y las
  escrituras van en transacciones. Es correcto con **una sola instancia** del backend; varias instancias
  requerirían un bloqueo en base de datos.
- **Reglas de cómputo**: una entrada abierta no suma horas hasta que se ficha la salida; no hay pausas
  dentro de un tramo, horas extra con compensación, convenios, bolsa de horas ni cómputo semanal/anual.
  Los tramos de horarios corridos y partidos no cruzan la medianoche (para eso están turnos y guardias).
  Las guardias tienen como máximo 24 h.
- **Incidencias**: se recalculan en cada consulta; si se cambian asignaciones pasadas, las incidencias
  de esos días cambian (los fichajes y las justificaciones no). Una justificación no se puede deshacer.
- **Modificación de datos**: no hay edición ni borrado de horarios, asignaciones o personas (solo altas),
  ni corrección/anulación de fichajes más allá de añadir uno nuevo con hora indicada; un fichaje no
  puede ser anterior al último registrado.
- **Zona horaria**: se usa una sola zona configurable para todos los centros. Los frontends calculan
  "hoy" con la fecha del navegador.
- **Escalabilidad**: las consultas cargan los datos de la persona y evalúan en memoria (adecuado para
  cientos de personas, no para miles con rangos largos); el rango de consulta está limitado a 366 días.
- **Datos**: todos los datos de ejemplo son ficticios. La base de datos se genera localmente en `data/`
  y está excluida del repositorio.
