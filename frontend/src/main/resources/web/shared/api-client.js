// Adaptador de comunicacion con el backend (el "broker" que responde a todos los modulos).
//
// Los modulos solo usan los metodos de HttpPresenceApi; no hacen fetch ni conocen las rutas.
// Para cambiar de transporte (otra URL, un doble de prueba...) basta con otro objeto con los
// mismos metodos. El token de sesion se envia en la cabecera Authorization.

/** Error de la API con el codigo estable del contrato (p. ej. DUPLICATE_CLOCK_EVENT). */
export class ApiError extends Error {
  constructor(code, message, status) {
    super(message);
    this.code = code;
    this.status = status;
  }
}

export class HttpPresenceApi {
  /**
   * @param baseUrl        URL base de la API (de /config.js).
   * @param token          token de sesion o null.
   * @param onUnauthorized se llama si el backend responde 401 con una sesion caducada.
   */
  constructor(baseUrl, { token = null, onUnauthorized = () => {}, fetchImpl = globalThis.fetch.bind(globalThis) } = {}) {
    this.baseUrl = baseUrl.replace(/\/+$/, "");
    this.token = token;
    this.onUnauthorized = onUnauthorized;
    this.fetch = fetchImpl;
  }

  // ---- Sesion
  authStatus() { return this.#get("/auth/status"); }
  setup(username, password) { return this.#post("/auth/setup", { username, password }); }
  login(username, password) { return this.#post("/auth/login", { username, password }); }
  logout() { return this.#post("/auth/logout", {}); }
  me() { return this.#get("/auth/me"); }

  // ---- Cuentas, personas y centros (responsable)
  listUsers() { return this.#get("/users"); }
  createUser(account) { return this.#post("/users", account); }
  resetPassword(username, password) { return this.#post(`/users/${encodeURIComponent(username)}/password`, { password }); }
  listPeople() { return this.#get("/people"); }
  createPerson(person) { return this.#post("/people", person); }
  listWorkplaces() { return this.#get("/workplaces"); }
  createWorkplace(workplace) { return this.#post("/workplaces", workplace); }

  // ---- Horarios, asignaciones y festivos
  listSchedules() { return this.#get("/schedules"); }
  createSchedule(schedule) { return this.#post("/schedules", schedule); }
  listAssignments(personId) { return this.#get("/assignments", { personId }); }
  assignSchedule(assignment) { return this.#post("/assignments", assignment); }
  listHolidays() { return this.#get("/holidays"); }
  createHoliday(holiday) { return this.#post("/holidays", holiday); }

  // ---- Fichajes y consultas
  clock(event) { return this.#post("/clock-events", event); }
  listClockEvents({ personId, from, to } = {}) { return this.#get("/clock-events", { personId, from, to }); }
  currentPresence() { return this.#get("/presence"); }
  presenceOf(personId) { return this.#get(`/presence/${encodeURIComponent(personId)}`); }
  dailyReport({ personId, from, to }) { return this.#get("/reports/daily", { personId, from, to }); }
  listIncidents({ personId, from, to, type } = {}) { return this.#get("/incidents", { personId, from, to, type }); }
  justifyIncident(incidentKey, reason) { return this.#post("/incidents/justifications", { incidentKey, reason }); }
  audit(limit = 50) { return this.#get("/audit", { limit }); }

  async #get(path, params = {}) {
    const query = new URLSearchParams(
      Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ""));
    const suffix = query.toString() ? `?${query}` : "";
    return this.#request(path + suffix, { method: "GET" });
  }

  async #post(path, body) {
    return this.#request(path, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  }

  async #request(path, init) {
    const headers = { ...(init.headers || {}) };
    if (this.token) headers.Authorization = `Bearer ${this.token}`;
    let response;
    try {
      response = await this.fetch(this.baseUrl + path, { ...init, headers });
    } catch (e) {
      throw new ApiError("NETWORK_ERROR", `No se pudo conectar con el backend (${this.baseUrl}). ¿Está arrancado?`, 0);
    }
    const text = await response.text();
    let data = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      throw new ApiError("INVALID_RESPONSE", `Respuesta no válida del backend (HTTP ${response.status})`, response.status);
    }
    if (!response.ok) {
      const error = new ApiError(data?.error ?? "HTTP_" + response.status, data?.message ?? response.statusText, response.status);
      if (error.code === "UNAUTHENTICATED" && this.token) this.onUnauthorized(error);
      throw error;
    }
    return data;
  }
}

/** URL de la API publicada por el servidor de frontends en /config.js. */
export function apiBaseUrl() {
  return globalThis.PRESENCIA_CONFIG?.apiBaseUrl ?? "http://localhost:8080/api";
}
