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

## Acceso y registro

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

Las 15 pruebas de integracion comprueban autenticacion y permisos, altas,
solicitudes pendientes, aprobaciones y denegaciones, fechas originales, suma de
horas, aislamiento entre empleados, solicitudes invalidas, decisiones definitivas,
persistencia, fallos al guardar, bloqueo entre procesos, registros corruptos e
inmutabilidad de los historiales. Sus datos de prueba se escriben en `target/`.

## Clases principales

- `Employe`: identidad del empleado; las credenciales y los permisos se gestionan por separado.
- `Attendance`: sesion de trabajo aprobada con entrada, salida y duracion.
- `AttendanceService`: reglas basicas de entrada, salida e historial.
- `AttendanceRequest`: peticion con hora original, estado, responsable y motivo.
- `AttendanceBroker`: autenticacion, permisos, revision de solicitudes y persistencia.
- `AttendanceWindow`: vistas del empleado y del responsable en Swing.
- `Main`: punto de entrada y configuracion de la ruta del registro.
