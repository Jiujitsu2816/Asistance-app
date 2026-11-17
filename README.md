# Registro de asistencia

Aplicacion Java de escritorio con dos vistas autenticadas: empleado y responsable.
Requiere JDK 26. No necesita librerias externas ni un servidor para ejecutarse.

## Inicio

En IntelliJ, selecciona JDK 26 y ejecuta `Software.ulpgc.Main`.
En el primer arranque se solicita crear la cuenta del responsable.
Desde su vista se pueden crear empleados, asignandoles un ID, nombre,
apellidos, usuario y clave de al menos ocho caracteres.

El boton **Otra ventana** permite abrir otra sesion en la misma aplicacion.
Por ejemplo, puedes mantener abierta la vista del responsable e iniciar sesion
como empleado en otra ventana. Cada ventana mantiene su propia sesion.

## Flujo

1. El empleado indica el lugar de trabajo y pulsa **Solicitar entrada**.
2. La peticion aparece en **Mis solicitudes**, con su hora original y estado pendiente.
3. El responsable pulsa **Actualizar**, selecciona la solicitud y la acepta o deniega.
   Para denegar debe indicar un motivo. La decision guarda el usuario del responsable
   y la fecha y hora en que se tomo.
4. Una entrada aprobada aparece en **Mi registro de horas** del empleado.
   La hora de entrada es la de la solicitud, aunque se apruebe despues.
5. El empleado solicita la salida y el responsable la revisa de la misma manera.
   Las sesiones cerradas aprobadas se suman en el total de horas.

Solo se permite una solicitud pendiente por empleado. La entrada debe estar
aprobada antes de solicitar la salida. Una salida denegada deja la sesion abierta
y permite volver a solicitarla. Las decisiones ya tomadas no se pueden modificar.
Usa **Actualizar** en las tablas para ver los cambios de otras ventanas.

## Horarios y ausencias

En la vista del responsable, abre **Horarios y ausencias**. Selecciona un empleado,
indica lugar, fecha, **Desde** y **Hasta** (por ejemplo, 09:00 a 17:00) y pulsa
**Guardar horario**. Cada fila corresponde a una fecha; puedes guardar varias.
La salida debe ser posterior a la entrada en el mismo dia. Los horarios antiguos
se conservan y muestran **Sin definir** en la salida.

Una ausencia (`Missing`) se calcula desde la hora de entrada prevista cuando no
hay una entrada aprobada a tiempo para ese empleado y lugar. No se crea ninguna
ausencia si no se ha guardado un horario. Una solicitud pendiente o denegada no
cuenta como asistencia. Si se aprueba despues una solicitud enviada a tiempo,
la ausencia desaparece; una entrada enviada tarde conserva la ausencia.
La hora final delimita el horario guardado; actualmente no se calculan ausencias
parciales por salida anticipada ni horas pendientes de trabajar.

Consulta **Ausencias** como responsable o **Mis ausencias** como empleado y pulsa
**Actualizar ausencias**. El lugar debe coincidir con el de la entrada.
Los horarios se guardan en el registro y las ausencias se reconstruyen al abrirlo,
incluso si la aplicacion estaba cerrada a la hora prevista.

## Acceso y registro

Los responsables disponen de **Usuarios y claves** para listar todas las cuentas
y restablecer sus claves. Las claves existentes no se pueden consultar.
El cambio cierra las otras sesiones de la cuenta afectada.

Si se pierde el acceso, cierra la aplicacion y ejecuta `Main` con el argumento
`--recover-users` (opcionalmente seguido de la ruta del registro).
Esta operacion local crea una copia de seguridad, conserva usuarios e historial,
crea el administrador `root` y genera claves nuevas para todas las cuentas.
Las nuevas claves se muestran una vez en la consola; guardalas antes de cerrarla.

La cuenta `root` solo aparece en su propio listado de usuarios. Los demas
responsables no pueden modificar su clave. Su nombre esta reservado.
Solo `root` dispone de los botones **Crear backup** y **Reset**.
Cada backup conserva la copia nueva y la anterior del mismo registro.
El reset pide confirmacion y elimina los demas usuarios, todas las solicitudes
y el historial, y todos los backups del registro. Conserva `root` y su clave,
y cierra todas las sesiones. Si solo queda `root`, el inicio vuelve a mostrar
el formulario para crear un responsable; tambien permite entrar con `root`
escribiendo su usuario y su clave en ese mismo formulario.

- El empleado solo puede consultar sus solicitudes, su asistencia y su total de horas.
  Su identidad se obtiene de la sesion, sin aceptar un ID ajeno desde la vista.
- El responsable puede dar de alta empleados y revisar todas las solicitudes
  pendientes y el registro de decisiones. El broker comprueba estos permisos.
- Las claves se guardan con PBKDF2 y una sal aleatoria por cuenta.
- Cuentas, peticiones y decisiones se guardan en `data/attendance.properties`.
  El historial aprobado se reconstruye a partir de las decisiones al abrir la aplicacion.
  La carpeta `data/` esta excluida de Git.
- Cada cambio se guarda antes de actualizar el estado en memoria. Se escribe un
  archivo temporal y se sustituye el registro completo. Si falla el guardado, la
  operacion no queda aplicada en memoria.
- Las horas se almacenan como instantes UTC y se muestran en la zona horaria del equipo.

Es un broker local para una sola aplicacion, con varias ventanas. Un bloqueo
impide que otro proceso abra el mismo archivo y sobrescriba los cambios.
Para usar un archivo distinto, pasa su ruta como argumento de `Main`.
Este proyecto no incluye un servidor de red para conectar equipos diferentes.

## Pruebas

Ejecuta el `main` de `Software.ulpgc.AttendanceBrokerTest` desde IntelliJ.
Tambien puedes ejecutar `scripts/test.ps1 -JdkHome RUTA_AL_JDK_26` en un entorno
que permita scripts PowerShell. Las pruebas no necesitan dependencias externas.

Para ejecutar toda la suite con JUnit, usa `mvn test` o ejecuta los tests de
`src/test/java` desde IntelliJ. Incluye tambien las 19 pruebas del broker,
las reglas de asistencia y ausencias, y las regresiones de rotacion de backups
y recuentos invalidos en el registro guardado. Maven necesita las dependencias
JUnit declaradas en `pom.xml`; el script anterior solo ejecuta las pruebas del broker.

Las 19 pruebas de integracion comprueban reset, rotacion de backups, recuperacion de acceso, autenticacion y permisos, altas,
solicitudes pendientes, aprobaciones y denegaciones, fechas originales, suma de
horas, aislamiento entre empleados, solicitudes invalidas, decisiones definitivas,
persistencia, fallos al guardar, bloqueo entre procesos, registros corruptos e
inmutabilidad de los historiales. Sus datos de prueba se escriben en `target/`.

## Clases principales

- `Employe`: identidad del empleado; las credenciales y los permisos se gestionan por separado.
- `Attendance`: sesion de trabajo aprobada con entrada, salida y duracion.
- `AttendanceService`: reglas basicas de entrada, salida e historial.
- `AttendanceRequest`: peticion con hora original, estado, responsable y motivo.
- `AttendanceBroker`: coordina sesiones, permisos, cuentas y revision de solicitudes.
- `AttendanceRepository`: contrato de lectura y guardado de un estado completo.
- `AttendanceStore` / `PropertiesAttendanceStore`: almacenamiento exclusivo, guardado,
  bloqueo del archivo y backups.
- `AttendancePropertiesCodec`: conversion entre el estado y el formato Properties.
- `AttendanceSnapshot` / `UserAccount`: estado inmutable y cuentas con claves protegidas.
- `PasswordHasher` / `Pbkdf2PasswordHasher`: creacion y comprobacion de claves protegidas.
- `AbsencePolicy` / `OnTimeEntryPolicy`: regla de cobertura de una entrada prevista.
- `AttendanceWindow`: vistas del empleado y del responsable en Swing.
- `Main`: punto de entrada, ruta del registro y composicion de dependencias.

## Diseno y principios SOLID

El broker depende de `AttendanceStore`, `PasswordHasher` y `AbsencePolicy`, recibidos
por constructor. `Main` selecciona las implementaciones para archivos Properties,
PBKDF2 y entradas a tiempo. El constructor `AttendanceBroker(Path, Clock)` se conserva
para los clientes existentes. La vista llama al broker y no accede al archivo ni
calcula permisos o ausencias.

| Principio | Aplicacion en el proyecto |
| --- | --- |
| S: responsabilidad unica | El broker coordina los casos de uso; el codec conoce el formato; el store administra archivos y backups; el hasher protege claves; la politica decide si una entrada cubre el horario. |
| O: abierto a extension | Se pueden proporcionar otro store, hasher o politica de ausencias sin modificar los casos de uso del broker. |
| L: sustitucion | Un repositorio sustituto debe conservar el estado anterior si falla el guardado. Un store mantiene acceso exclusivo hasta cerrarse y rechaza operaciones posteriores; cerrar dos veces es valido. |
| I: segregacion de interfaces | `AttendanceRepository` solo ofrece cargar/guardar; `AttendanceStore` agrega las operaciones de administracion que necesita el broker. Las claves y las ausencias tienen contratos separados. |
| D: inversion de dependencias | La coordinacion usa contratos; las implementaciones concretas se eligen en `Main`. `Clock` se inyecta para reproducir fechas en las pruebas. |

El guardado recibe un `AttendanceSnapshot` inmutable. El broker publica los cambios
en memoria despues de guardar. El formato de archivo version 1, las claves PBKDF2,
los horarios antiguos sin salida y las reglas de aprobacion se conservan.
El broker es propietario del store inyectado y lo cierra tambien si falla la carga.

Las pruebas de arquitectura comprueban sustitucion del store, hasher y politica,
fallos de carga y guardado, inmutabilidad del estado, lectura de horarios antiguos
y cierre del almacenamiento. Se ejecutan junto con las pruebas funcionales en JUnit.
