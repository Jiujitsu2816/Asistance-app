# Guía para la defensa (20 minutos)

Decisiones de arquitectura del proyecto, por qué se tomaron y dónde están en el código, relacionadas
con los conceptos de la asignatura (fundamentos, principios SOLID, patrones y mecanismos de Java).

## Guion por tiempos

| Min. | Bloque | Qué enseñar |
|---|---|---|
| 0–2 | El problema | Tres tipos de horario, fichar, consultar; el reto está en el dominio |
| 2–5 | Decisión 1: arquitectura de sistemas | Navegador → backend (broker) → base de datos; contrato HTTP/JSON |
| 5–10 | Decisión 2: arquitectura hexagonal | Capas, regla de dependencia y el recorrido de un fichaje |
| 10–14 | Decisión 3: horarios polimórficos | `Schedule` sellada y sus cuatro implementaciones |
| 14–16 | Decisión 4: reglas en el dominio | Diseño por contrato, `ClockingPolicy`, `AttendanceEvaluator` |
| 16–18 | Decisión 5: perfiles y permisos | Login, responsable/empleado, permisos en el backend |
| 18–20 | Persistencia, pruebas y demo | Transacción + auditoría, pruebas con adaptadores en memoria, demo rápida |

**Demo de 1 minuto** (con todo ya arrancado y el responsable creado): entrar como empleado → fichar
entrada → fichar entrada otra vez (error `DUPLICATE_CLOCK_EVENT`) → cerrar sesión → entrar como
responsable → *Consulta de presencia*.

---

## 1. El problema (2 min)

- Registrar entradas y salidas, compararlas con el horario y consultar el resultado (presencia,
  retrasos, ausencias, horas trabajadas).
- **La dificultad es el dominio:** no todos trabajan igual. Corrido (oficina), partido (restaurante),
  turnos rotatorios y guardias (hospital, que además cruzan la medianoche).
- Requisitos de arquitectura del enunciado: backend independiente, tres módulos frontend, contrato
  HTTP documentado y arquitectura hexagonal.

## 2. Decisión 1 — Arquitectura de sistemas (3 min)

```
Navegador (aplicación web)  ──HTTP+JSON──►  Backend (API, "broker")  ──JDBC──►  H2 (fichero)
   módulos: fichaje, consulta,                 casos de uso + dominio
   horarios, personal
```

- **Una aplicación web con módulos** en vez de varias aplicaciones de escritorio: un solo sitio,
  accesible desde el móvil o un terminal, y el menú cambia según el perfil. El enunciado admite
  "aplicaciones **o módulos** frontend diferenciados".
- **El backend es el único que toca la base de datos.** La web no sabe que existe H2: solo conoce el
  contrato HTTP (README §10).
- **Dos procesos independientes:** el backend se ejecuta solo (requisito 1) y la web es solo HTML+JS
  servido por `FrontendServer`.
- **Sin frameworks (YAGNI):** servidor HTTP del JDK, Jackson para JSON y H2 embebido. Todo se arranca
  con `java -jar` y se entiende sin "magia" de anotaciones.

Dónde: `frontend/.../web/shared/api-client.js` (el único que hace `fetch`), `HttpApiServer`, `ApiRoutes`.

## 3. Decisión 2 — Arquitectura hexagonal (5 min)

```
infrastructure (adaptadores) ──► application (casos de uso + puertos) ──► domain (reglas)
   http, persistence, security          port.in / port.out / service           sin dependencias
```

**Regla de dependencia:** las flechas solo apuntan hacia dentro. El dominio no importa nada de JDBC,
HTTP ni Jackson. Lo comprueba una prueba automática: `ArchitectureTest.domainDependsOnlyOnTheJdk`.

**Recorrido de un fichaje** (enséñalo abriendo los ficheros en este orden):

1. `ApiRoutes.clockIn` (adaptador de entrada): traduce el JSON a un **comando** y comprueba permisos.
2. `RegisterClockEventUseCase` (puerto de entrada): la interfaz que ve el adaptador.
3. `RegisterClockEventService.register` (caso de uso): busca la persona, aplica `ClockingPolicy`,
   comprueba el horario y guarda el fichaje **y** la auditoría en una transacción.
4. `ClockEventRepository` (puerto de salida): interfaz definida por la aplicación.
5. `JdbcClockEventRepository.append` (adaptador de salida): el SQL.
6. `Backend` (raíz de composición): el único sitio que hace `new JdbcClockEventRepository(...)` y lo
   inyecta por constructor.

| Concepto de la guía | Cómo aparece |
|---|---|
| **DIP** (inversión de dependencias) | Los servicios dependen de `ClockEventRepository` (abstracción), no de JDBC |
| **Inyección de dependencias** | Todo llega por constructor; `Backend` monta el sistema |
| **Patrón Adapter** | `Jdbc*Repository` adapta JDBC a los puertos; `HttpApiServer`/`ApiRoutes` adaptan HTTP a los casos de uso; `HttpPresenceApi` (JS) adapta `fetch` para la web |
| **ISP** (segregación de interfaces) | Un puerto por entidad (`PersonRepository`, `ScheduleRepository`...) y un caso de uso por necesidad, en vez de un "servicio gigante" |
| **SRP** (responsabilidad única) | Cada servicio tiene un motivo de cambio: fichar, consultar, administrar horarios, cuentas... |
| **Modularidad** | Las pruebas sustituyen la base de datos por `InMemoryAdapters` sin tocar los servicios |

**Frase clave:** *"Si mañana cambiamos H2 por PostgreSQL o HTTP por otra cosa, solo cambian los
adaptadores; el dominio y los casos de uso no se tocan, y las pruebas lo demuestran usando adaptadores
en memoria."*

## 4. Decisión 3 — Horarios polimórficos (4 min) — *la más importante del dominio*

```java
public sealed interface Schedule
        permits ContinuousSchedule, SplitSchedule, RotatingShiftSchedule, OnCallSchedule {
    List<WorkPeriod> periodsStartingOn(LocalDate date);   // qué hay que trabajar ese día
    boolean exemptOnHolidays();                           // ¿un festivo lo anula?
    Duration tolerance();
}
```

- Cada tipo implementa **sus propias reglas** en su clase. El enunciado pide reglas "explícitas, no
  condicionales dispersos": el evaluador nunca pregunta "si es partido, haz esto...", solo llama a
  `periodsStartingOn(fecha)`.
  - Corrido: un tramo los días laborables. Partido: varios tramos separados por una pausa.
  - Turnos: ciclo de N días (`(fecha − inicio) mod N`); un turno de noche cruza la medianoche.
  - Guardias: fechas concretas, física (exige fichar) o localizada (no).
- **Polimorfismo + OCP:** añadir un tipo nuevo es añadir una clase, sin modificar el evaluador.
- **LSP:** cualquier `Schedule` se puede usar donde se espera un `Schedule`; todas cumplen el mismo contrato.
- **`sealed`** (Java 17+): limita las implementaciones a las cuatro conocidas, y el `switch` con
  *pattern matching* de `ScheduleDocument.from` obliga al compilador a cubrirlas todas.
- **Festivos (las "excepciones" de la rúbrica):** `exemptOnHolidays()` devuelve `true` en oficina y
  tienda y `false` en turnos y guardias (el hospital no cierra). Es polimorfismo, no un `if` por tipo.

Dónde: `domain/schedule/` (una clase por tipo), `infrastructure/json/ScheduleDocument` (conversión a JSON).

## 5. Decisión 4 — Reglas en el dominio y diseño por contrato (2 min)

- **Objetos inválidos no pueden existir:** los `record` validan en su constructor (`Require.text`,
  `Require.that`). Un `TimeSlot` que termina antes de empezar lanza `INVALID_DATA`. Son las
  **precondiciones e invariantes** del diseño por contrato.
- **Inmutabilidad:** casi todo el dominio son `record` (valores inmutables), así que se comparten sin miedo.
- **`ClockingPolicy`:** entrada y salida se alternan; una segunda entrada es `DUPLICATE_CLOCK_EVENT`
  y una salida sin entrada es `INCONSISTENT_CLOCK_EVENT`.
- **`AttendanceEvaluator`** (servicio de dominio con nombre claro, como pide el enunciado): empareja
  fichajes en sesiones, las asocia al periodo con el que más se solapan y detecta ausencia, retraso,
  salida anticipada y salida pendiente. Usa **Streams** y una **clase interna** `Evaluation` que agrupa
  el cálculo de un rango de fechas.
- **Las incidencias se calculan, no se guardan:** si se corrige un fichaje, nunca quedan desfasadas
  (DRY: una única fuente de verdad, los fichajes).
- **Errores con código estable:** una sola `DomainException` con un `ErrorCode`. El adaptador HTTP traduce
  el código a 400/401/403/404/409/422 (`HttpApiServer.statusOf`); el dominio no sabe nada de HTTP.

## 6. Decisión 5 — Perfiles y permisos (2 min)

- Una misma aplicación: **responsable** (personal, centros, horarios, consultas) o **empleado** (su
  horario y su fichaje). La idea propia del proyecto.
- **Los permisos se deciden en el backend, no en el menú:** `AccessControl.personScope` hace que un
  empleado solo pueda operar sobre su propia persona; si lo intenta con otra, recibe `403 FORBIDDEN`
  aunque llame a la API a mano. Ese test existe: `HttpApiIntegrationTest.anEmployeeOnlySeesAndClocksTheirOwnData`.
- **Seguridad básica bien hecha:**
  - Claves con PBKDF2 y sal aleatoria.
  - Comparación en tiempo constante (`MessageDigest.isEqual`).
  - El mismo mensaje exista o no el usuario.
  - Claves en `char[]` que se borran con `Arrays.fill` tras usarlas (enlace con *gestión de memoria*: no
    dejar secretos vivos en el heap).
- **Sin credenciales en el repositorio:** el primer responsable se crea desde la web; hay recuperación
  local con `--recuperar-acceso`.

## 7. Persistencia y trazabilidad (1 min)

- Los fichajes son de **solo añadir** (el repositorio no tiene `update` ni `delete`): las correcciones
  se hacen añadiendo un fichaje nuevo con hora indicada, y queda la hora real en `recordedAt`.
- **Cada cambio y su entrada de auditoría van en la misma transacción** (puerto `Transactions`,
  implementado en `Database.inTransaction` con un `ThreadLocal<Connection>`): no puede quedar un fichaje
  sin auditar.
- Datos en H2 en fichero (`./data/`): sobreviven a un reinicio (probado en
  `HttpApiIntegrationTest.clockEventsAndAuditSurviveARestart`).

## 8. Pruebas (1 min)

- **61 pruebas**:
  - Dominio: los tipos de horario y el evaluador (turno de noche, festivos, guardias...).
  - Casos de uso con **adaptadores en memoria** (demuestran el DIP).
  - Integración HTTP con la base de datos real.
  - Arquitectura (regla de dependencia) y seguridad.
- Recorrido automático con navegador (`docs/e2e/recorrido.cjs`) que genera las capturas del README.

---

## Patrones y mecanismos de la guía que aparecen en el código

| De la guía | En el proyecto | Dónde |
|---|---|---|
| Interfaz como contrato (abstracción) | Puertos de entrada y salida | `application/port/in`, `port/out` |
| Polimorfismo | Cuatro horarios detrás de `Schedule` | `domain/schedule` |
| Adapter | Repositorios JDBC, API HTTP, cliente JS | `infrastructure/*`, `api-client.js` |
| Factory Method (estático) | `ApiResponse.ok/created`, `HolidayCalendar.empty`, `ScheduleDocument.from`, `WorkSession.pair` | varios |
| Command | Los casos de uso reciben comandos (`RegisterClockEventCommand`...) y el `Router` guarda un manejador (lambda) por ruta: el invocador no cambia al añadir rutas | `port/in`, `Router` |
| Iterator / Streams | Cálculos declarativos con `stream().filter().map()` | `AttendanceEvaluator`, servicios |
| Observer | Los módulos web reaccionan a eventos (`addEventListener`) | `web/modulos` |
| Singleton | **No se usa a propósito**: cada objeto se crea una vez en `Backend` y se inyecta (la guía avisa de que el Singleton aumenta el acoplamiento) | `Backend` |
| Composición sobre herencia | Los servicios *tienen* repositorios; solo heredan las dos excepciones (`DomainException`, `PersistenceException`) de `RuntimeException` | servicios |
| Clases internas / anónimas | `Evaluation` (interna de objeto), `record` anidados (`Planned`, comandos), clases anónimas en `InMemoryAdapters` | evaluador, pruebas |
| `static` | Utilidades sin estado: `Require`, `AccessControl` | dominio, aplicación |
| Mecanismos modernos | `record`, `sealed`, `switch` con *pattern matching*, `Optional` | todo el dominio |

## Lo que se ha simplificado para poder explicarlo

- **Una sola clase de error** (`DomainException` + `ErrorCode`) en lugar de 21 subclases que solo
  cambiaban de nombre (DRY/YAGNI).
- **Sin justificación de incidencias:** no lo pide el enunciado; la idea propia son los perfiles y los
  centros de trabajo.
- **Sin lanzador "todo en uno" ni JavaFX:** dos procesos (backend y web), como pide el enunciado.

## Preguntas probables y respuesta corta

- **¿Por qué hexagonal y no MVC en capas?** Porque las reglas de horario son lo valioso y deben poder
  probarse sin base de datos ni HTTP. Con puertos y adaptadores, el dominio no depende de nada.
- **¿Dónde está el "broker"?** Es el backend: todas las peticiones de la web pasan por la API, que
  autentica, aplica permisos y delega en los casos de uso.
- **¿Cómo añadirías un nuevo tipo de horario?** Una clase nueva que implemente `Schedule` (y añadirla al
  `permits`) más su conversión en `ScheduleDocument`. El evaluador no cambia: OCP.
- **¿Por qué se calculan las incidencias en vez de guardarlas?** Para no tener dos verdades: si se
  corrige un fichaje, el resultado se recalcula solo.
- **¿Qué pasa si dos peticiones fichan a la vez para la misma persona?** Se serializan con un cerrojo
  por persona y una transacción. Funciona con una instancia del backend; con varias haría falta un
  bloqueo en base de datos (README §14).
- **¿Por qué H2 y no MySQL?** Arranca sin instalar nada. Cambiarla solo afecta a `infrastructure/persistence`.
- **¿Cómo sabes que el dominio no depende de la infraestructura?** `ArchitectureTest` analiza los
  `import` y falla si el dominio importa JDBC, HTTP, Jackson o la capa de aplicación.
- **¿Qué pasa si un empleado manipula la web para fichar por otro?** Nada: el backend ignora el menú y
  responde 403. Lo cubre una prueba.
- **Límites:**
  - Sesiones en memoria.
  - Sin HTTPS en local.
  - Sin edición ni borrado de datos (solo altas).
  - Una sola zona horaria.
  - Una instancia del backend.
