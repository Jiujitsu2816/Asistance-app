package software.ulpgc.presencia.domain.account;

/** Perfil con el que se inicia sesion en la aplicacion. */
public enum Role {
    /** Responsable: personas, cuentas, centros, horarios, asignaciones, consultas y correcciones. */
    ADMIN,
    /** Empleado: consulta su horario, ficha su entrada y salida y ve sus registros. */
    EMPLOYEE
}
