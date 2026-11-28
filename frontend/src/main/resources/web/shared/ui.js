// Utilidades de presentacion compartidas (sin acceso a la API).

export const STATUS_LABELS = {
  // Estado diario
  NO_SCHEDULE: "Sin horario", HOLIDAY: "Festivo", REST: "Descanso", PENDING: "Pendiente",
  IN_PROGRESS: "En curso", COMPLETED: "Cumplido", EXTRA_WORK: "Trabajo extra",
  EARLY_DEPARTURE: "Salida anticipada", LATE: "Retraso", CHECKOUT_PENDING: "Salida pendiente", ABSENT: "Ausente",
  // Presencia actual
  PRESENT: "Presente", PRESENT_OUT_OF_SCHEDULE: "Presente (fuera de horario)", EXPECTED: "Esperado",
  ON_CALL: "De guardia localizada", OFF_DUTY: "Fuera de turno",
};

export const INCIDENT_LABELS = {
  ABSENCE: "Ausencia", LATE_ARRIVAL: "Retraso", EARLY_DEPARTURE: "Salida anticipada",
  MISSING_CHECKOUT: "Salida pendiente", UNSCHEDULED_WORK: "Fuera de horario",
};

export const ROLE_LABELS = { ADMIN: "Responsable", EMPLOYEE: "Empleado" };

export const KIND_LABELS = {
  CONTINUOUS: "Corrido", SPLIT: "Partido", ROTATING_SHIFT: "Turnos rotatorios", ON_CALL: "Guardia",
};

const TONES = {
  PRESENT: "ok", COMPLETED: "ok", IN_PROGRESS: "info", PRESENT_OUT_OF_SCHEDULE: "info",
  EXPECTED: "info", ON_CALL: "info", PENDING: "muted", REST: "muted", HOLIDAY: "muted", OFF_DUTY: "muted",
  NO_SCHEDULE: "muted", EXTRA_WORK: "info", LATE: "warn", EARLY_DEPARTURE: "warn", CHECKOUT_PENDING: "warn",
  ABSENT: "bad", ABSENCE: "bad", LATE_ARRIVAL: "warn", MISSING_CHECKOUT: "warn", UNSCHEDULED_WORK: "info",
};

/** Crea un elemento: el("td", { class: "x" }, "texto", otroNodo). */
export function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (value === undefined || value === null || value === false) continue;
    if (key.startsWith("on")) node.addEventListener(key.slice(2), value);
    else node.setAttribute(key, value === true ? "" : value);
  }
  for (const child of children.flat()) {
    if (child === null || child === undefined) continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

export function badge(code, labels = STATUS_LABELS) {
  return el("span", { class: `badge ${TONES[code] ?? "muted"}`, title: code }, labels[code] ?? code);
}

/** "2026-11-17T08:05:00Z" -> "08:05" (hora tal como la devuelve el backend en su zona). */
export function time(iso) {
  return iso ? iso.substring(11, 16) : "—";
}

/** "2026-11-17T08:05:00Z" -> "17/11/2026 08:05". */
export function dateTime(iso) {
  if (!iso) return "—";
  return `${iso.substring(8, 10)}/${iso.substring(5, 7)}/${iso.substring(0, 4)} ${iso.substring(11, 16)}`;
}

/** "Manana 07:00–15:00"; si la etiqueta ya son las horas ("08:00-15:00") no se repite. */
export function period(label, startIso, endIso) {
  const hours = `${time(startIso)}–${time(endIso)}`;
  return /^\d{2}:\d{2}-\d{2}:\d{2}$/.test(label ?? "") ? hours : `${label} ${hours}`;
}

export function minutes(value) {
  if (value === null || value === undefined) return "—";
  const sign = value < 0 ? "−" : "";
  const abs = Math.abs(value);
  return `${sign}${Math.floor(abs / 60)} h ${String(abs % 60).padStart(2, "0")} min`;
}

export function today() {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().substring(0, 10);
}

export function addDays(isoDate, days) {
  const d = new Date(`${isoDate}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().substring(0, 10);
}

/** Muestra un mensaje en un contenedor .message (tipo: ok | error | info). */
export function showMessage(container, type, text, detail) {
  container.replaceChildren(el("div", { class: `message ${type}`, role: type === "error" ? "alert" : "status" },
    el("strong", {}, text), detail ? el("span", {}, " ", detail) : null));
}

/** Envuelve una tabla para que haga scroll horizontal en pantallas estrechas. */
export function wrap(tableNode) {
  return el("div", { class: "table-wrap" }, tableNode);
}

/** Campo de formulario con etiqueta. */
export function field(label, control) {
  return el("label", {}, label, control);
}

export function card(title, ...children) {
  return el("section", { class: "card" }, el("h2", {}, title), ...children);
}

export function hint(text) {
  return el("p", { class: "hint" }, text);
}

export function errorText(error) {
  return error.code ? `${error.message} (${error.code})` : String(error.message ?? error);
}

/** Rellena un <select> con personas. */
export function fillPeople(select, people, { placeholder } = {}) {
  select.replaceChildren(
    placeholder ? el("option", { value: "" }, placeholder) : null,
    ...people.map((p) => el("option", { value: p.id }, `${p.id} · ${p.displayName}`)));
}

export function table(headers, rows, emptyText = "Sin resultados") {
  return el("table", {},
    el("thead", {}, el("tr", {}, headers.map((h) => el("th", {}, h)))),
    el("tbody", {}, rows.length ? rows : el("tr", {}, el("td", { colspan: headers.length, class: "empty" }, emptyText))));
}
