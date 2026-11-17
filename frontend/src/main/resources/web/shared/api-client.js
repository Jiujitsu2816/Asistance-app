// Adaptador de comunicacion con el backend.
//
// Las tres aplicaciones solo usan la interfaz "PresenceApi" (los metodos de abajo) y la
// obtienen con createPresenceApi(). Para cambiar de transporte (otra URL, un mock para
// pruebas, otro protocolo...) basta con proporcionar otro objeto con los mismos metodos;
// las vistas no hacen fetch ni conocen las rutas HTTP.

/** Error de la API con el codigo estable del contrato (p. ej. DUPLICATE_CLOCK_EVENT). */
export class ApiError extends Error {
  constructor(code, message, status) {
    super(message);
    this.code = code;
    this.status = status;
  }
}

/** Implementacion HTTP + JSON del contrato documentado en el README. */
export class HttpPresenceApi {
  constructor(baseUrl, { actor = "frontend", fetchImpl = globalThis.fetch.bind(globalThis) } = {}) {
    this.baseUrl = baseUrl.replace(/\/+$/, "");
    this.actor = actor;
    this.fetch = fetchImpl;
  }

  // ---- Personas
  listPeople() { return this.#get("/people"); }
  createPerson(person) { return this.#post("/people", person); }

  // ---- Horarios, asignaciones y festivos
  listSchedules() { return this.#get("/schedules"); }
  createSchedule(schedule) { return this.#post("/schedules", schedule); }
  listAssignments(personId) { return this.#get("/assignments", { personId }); }
  assignSchedule(assignment) { return this.#post("/assignments", assignment); }
  listHolidays() { return this.#get("/holidays"); }
  createHoliday(holiday) { return this.#post("/holidays", holiday); }

  // ---- Fichajes
  clock(event) { return this.#post("/clock-events", event); }
  listClockEvents({ personId, from, to } = {}) { return this.#get("/clock-events", { personId, from, to }); }

  // ---- Consultas
  health() { return this.#get("/health"); }
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
    let response;
    try {
      response = await this.fetch(this.baseUrl + path, {
        ...init,
        headers: { ...(init.headers || {}), "X-Actor": this.actor },
      });
    } catch (e) {
      throw new ApiError("NETWORK_ERROR", `No se pudo conectar con el backend (${this.baseUrl}). ¿Está arrancado?`, 0);
    }
    const text = await response.text();
    let data = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      throw new ApiError("INVALID_RESPONSE", `Respuesta no valida del backend (HTTP ${response.status})`, response.status);
    }
    if (!response.ok) {
      throw new ApiError(data?.error ?? "HTTP_" + response.status, data?.message ?? response.statusText, response.status);
    }
    return data;
  }
}

/** Punto unico de creacion del adaptador; la URL llega de /config.js. */
export function createPresenceApi(actor) {
  const config = globalThis.PRESENCIA_CONFIG ?? { apiBaseUrl: "http://localhost:8080/api" };
  return new HttpPresenceApi(config.apiBaseUrl, { actor });
}
