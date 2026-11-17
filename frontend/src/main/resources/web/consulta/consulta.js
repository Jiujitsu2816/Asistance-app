import { createPresenceApi } from "/shared/api-client.js";
import {
  INCIDENT_LABELS, addDays, badge, dateTime, el, errorText, fillPeople, minutes, period, showMessage, table, time, today,
} from "/shared/ui.js";

const api = createPresenceApi("app-consulta");
const $ = (id) => document.getElementById(id);
let people = [];
const nameOf = (id) => people.find((p) => p.id === id)?.displayName ?? id;

// ---- Pestanas ------------------------------------------------------------------------
const loaders = { presence: loadPresence, audit: loadAudit };
function selectTab(name) {
  document.querySelectorAll("[role=tab]").forEach((b) => b.setAttribute("aria-selected", b.dataset.tab === name));
  document.querySelectorAll("[data-panel]").forEach((p) => { p.hidden = p.dataset.panel !== name; });
  location.hash = name;
  loaders[name]?.().catch(fail);
}
document.querySelectorAll("[role=tab]").forEach((b) => b.addEventListener("click", () => selectTab(b.dataset.tab)));

function fail(e) {
  showMessage($("message"), "error", "Error al consultar.", errorText(e));
}

// ---- Presencia actual -----------------------------------------------------------------
async function loadPresence() {
  const rows = (await api.currentPresence()).map((p) => el("tr", {},
    el("td", {}, el("strong", {}, p.personId), el("div", { class: "sub" }, p.displayName)),
    el("td", {}, badge(p.status)),
    el("td", {}, p.inside ? `Dentro desde ${dateTime(p.since)}` : (p.since ? `Fuera desde ${dateTime(p.since)}` : "—")),
    el("td", {}, p.currentPeriod ? period(p.currentPeriod, p.currentPeriodStart, p.currentPeriodEnd) : "—"),
    el("td", {}, badge(p.todayStatus))));
  $("presence-table").replaceChildren(table(["Persona", "Ahora", "Último fichaje", "Periodo en curso", "Estado del día"], rows));
}
$("presence-refresh").addEventListener("click", () => loadPresence().catch(fail));
setInterval(() => { if (!document.querySelector('[data-panel="presence"]').hidden) loadPresence().catch(() => {}); }, 30000);

// ---- Informe ---------------------------------------------------------------------------
$("report-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const report = await api.dailyReport({ personId: $("report-person").value, from: $("report-from").value, to: $("report-to").value });
    const deviation = report.deviationMinutes;
    $("report-totals").replaceChildren(el("div", { class: "totals" },
      total("Esperado", minutes(report.expectedMinutes)),
      total("Trabajado", minutes(report.workedMinutes)),
      total("Desviación (días cerrados)", minutes(deviation), deviation < 0 ? "negative" : "positive"),
      total("Incidencias", report.incidents)));
    $("report-table").replaceChildren(table(
      ["Fecha", "Horario", "Periodos (esperado → fichado)", "Esperado", "Trabajado", "Desviación", "Estado", "Incidencias"],
      report.days.map((d) => el("tr", {},
        el("td", { class: "date" }, d.date, d.holiday ? el("div", { class: "sub" }, `Festivo: ${d.holiday}`) : null),
        el("td", {}, d.scheduleName ?? "—"),
        el("td", {}, d.periods.length ? d.periods.map((p) => el("div", {},
          `${period(p.label, p.expectedStart, p.expectedEnd)} → `,
          p.firstEntry ? `${time(p.firstEntry)}–${p.open ? "…" : time(p.lastExit)}` : "sin fichaje",
          p.presenceRequired ? null : el("span", { class: "sub" }, " (localizada)"))) : "—"),
        el("td", { class: "num" }, minutes(d.expectedMinutes)),
        el("td", { class: "num" }, minutes(d.workedMinutes), d.openSession ? el("div", { class: "sub" }, "entrada abierta") : null),
        el("td", { class: `num ${d.deviationMinutes < 0 ? "negative" : ""}` }, minutes(d.deviationMinutes)),
        el("td", {}, badge(d.status)),
        el("td", {}, d.incidents.map((i) => el("div", {}, badge(i.type, INCIDENT_LABELS), " ", el("span", { class: "sub" },
          i.description, i.justified ? ` · justificada: ${i.justification.reason}` : ""))))))));
  } catch (e) {
    fail(e);
  }
});

function total(label, value, cls) {
  return el("div", {}, el("span", {}, label), el("span", { class: cls }, value));
}

// ---- Incidencias ----------------------------------------------------------------------
$("incidents-form").addEventListener("submit", (event) => {
  event.preventDefault();
  loadIncidents().catch(fail);
});

async function loadIncidents() {
  const incidents = await api.listIncidents({
    personId: $("incidents-person").value, from: $("incidents-from").value,
    to: $("incidents-to").value, type: $("incidents-type").value,
  });
  $("incidents-table").replaceChildren(table(["Fecha", "Persona", "Tipo", "Periodo", "Detalle", "Justificación"],
    incidents.map((i) => el("tr", {},
      el("td", { class: "date" }, i.date),
      el("td", {}, el("strong", {}, i.personId), el("div", { class: "sub" }, nameOf(i.personId))),
      el("td", {}, badge(i.type, INCIDENT_LABELS)),
      el("td", {}, i.periodLabel),
      el("td", {}, i.description),
      el("td", {}, i.justified
        ? el("div", {}, badge("JUSTIFIED"), el("div", { class: "sub" }, `${i.justification.reason} · ${i.justification.justifiedBy}`))
        : justifyForm(i))))));
}

function justifyForm(incident) {
  const input = el("input", { type: "text", maxlength: 200, placeholder: "Motivo", "aria-label": "Motivo" });
  const button = el("button", { class: "small", type: "submit" }, "Justificar");
  return el("form", {
    class: "inline", onsubmit: async (event) => {
      event.preventDefault();
      try {
        await api.justifyIncident(incident.key, input.value);
        showMessage($("incidents-message"), "ok", "Incidencia justificada.");
        await loadIncidents();
      } catch (e) {
        showMessage($("incidents-message"), "error", "No se pudo justificar.", errorText(e));
      }
    },
  }, input, " ", button);
}

// ---- Fichajes -------------------------------------------------------------------------
$("events-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const events = await api.listClockEvents({
      personId: $("events-person").value, from: $("events-from").value, to: $("events-to").value,
    });
    $("events-table").replaceChildren(table(["Fecha y hora", "Persona", "Tipo", "Origen", "Nota", "Registrado el"],
      events.map((e) => el("tr", {},
        el("td", {}, dateTime(e.occurredAt)),
        el("td", {}, `${e.personId} · ${nameOf(e.personId)}`),
        el("td", {}, e.type === "ENTRY" ? "Entrada" : "Salida"),
        el("td", {}, e.source),
        el("td", {}, e.note ?? ""),
        el("td", { class: "sub" }, dateTime(e.recordedAt))))));
  } catch (e) {
    fail(e);
  }
});

// ---- Auditoria ------------------------------------------------------------------------
async function loadAudit() {
  const entries = await api.audit(50);
  $("audit-table").replaceChildren(table(["#", "Fecha", "Quién", "Acción", "Entidad", "Detalle"],
    entries.map((a) => el("tr", {},
      el("td", { class: "num" }, a.sequence), el("td", {}, dateTime(a.at)), el("td", {}, a.actor),
      el("td", {}, a.action), el("td", {}, `${a.entityType} ${a.entityId}`), el("td", { class: "sub" }, a.detail ?? "")))));
}
$("audit-refresh").addEventListener("click", () => loadAudit().catch(fail));

// ---- Inicio ---------------------------------------------------------------------------
async function init() {
  try {
    people = await api.listPeople();
    fillPeople($("report-person"), people);
    fillPeople($("incidents-person"), people, { placeholder: "Todas" });
    fillPeople($("events-person"), people, { placeholder: "Todas" });
    const t = today();
    for (const prefix of ["report", "incidents", "events"]) {
      $(`${prefix}-from`).value = addDays(t, prefix === "events" ? 0 : -6);
      $(`${prefix}-to`).value = t;
    }
    const initial = location.hash.substring(1);
    selectTab(document.querySelector(`[data-tab="${initial}"]`) ? initial : "presence");
  } catch (e) {
    fail(e);
  }
}
init();
