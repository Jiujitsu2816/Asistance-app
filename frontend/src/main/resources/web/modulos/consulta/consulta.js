// Modulo de CONSULTA DE PRESENCIA E INCIDENCIAS.
// - Responsable: presencia actual de todos, informes por persona y periodo, incidencias (y su
//   registro de fichajes y auditoria.
// - Empleado: sus propios informes, incidencias y fichajes.
import {
  INCIDENT_LABELS, addDays, badge, card, dateTime, el, errorText, field, fillPeople, hint, minutes, period,
  showMessage, table, time, today, wrap,
} from "/shared/ui.js";

export default {
  id: "consulta",
  roles: ["ADMIN", "EMPLOYEE"],
  title: (ctx) => (ctx.isAdmin ? "Consulta de presencia" : "Mis registros"),
  mount,
};

async function mount(root, ctx) {
  const { api, isAdmin } = ctx;
  const people = isAdmin ? await api.listPeople() : [];
  const nameOf = (id) => people.find((p) => p.id === id)?.displayName ?? id;
  const message = el("div", { id: "message" });

  const tabs = [
    isAdmin ? { id: "presence", label: "Presencia actual", render: presenceTab } : null,
    { id: "report", label: isAdmin ? "Informe por persona" : "Mi informe", render: reportTab },
    { id: "incidents", label: isAdmin ? "Incidencias" : "Mis incidencias", render: incidentsTab },
    { id: "events", label: isAdmin ? "Fichajes" : "Mis fichajes", render: eventsTab },
    isAdmin ? { id: "audit", label: "Auditoría", render: auditTab } : null,
  ].filter(Boolean);

  const nav = el("div", { class: "subnav", role: "tablist" });
  const panel = el("div", {});
  root.append(nav, panel, message);

  async function select(tab) {
    nav.replaceChildren(...tabs.map((t) => el("button", {
      role: "tab", type: "button", "data-tab": t.id, "aria-selected": String(t === tab), onclick: () => select(t),
    }, t.label)));
    message.replaceChildren();
    panel.replaceChildren();
    try {
      await tab.render(panel);
    } catch (e) {
      showMessage(message, "error", "Error al consultar.", errorText(e));
    }
  }

  /** Selector de persona solo para el responsable (el empleado siempre consulta sus datos). */
  function personSelect(id, { all = false } = {}) {
    if (!isAdmin) return null;
    const select = el("select", { id });
    fillPeople(select, people, all ? { placeholder: "Todas" } : {});
    return field("Persona", select);
  }
  const value = (id) => document.getElementById(id)?.value || undefined;

  // ---- Presencia actual (responsable) ---------------------------------------------------
  async function presenceTab(container) {
    const tableBox = el("div", { id: "presence-table" });
    const refresh = el("button", { class: "secondary small", type: "button" }, "Actualizar");
    container.append(card("Presencia actual",
      hint("Estado de cada persona en este momento según su horario y su centro de trabajo."),
      el("div", { class: "actions" }, refresh), tableBox));
    const load = async () => {
      const rows = (await api.currentPresence()).map((p) => el("tr", {},
        el("td", {}, el("strong", {}, p.personId), el("div", { class: "sub" }, p.displayName)),
        el("td", {}, badge(p.status)),
        el("td", {}, p.inside ? `Dentro desde ${dateTime(p.since)}` : (p.since ? `Fuera desde ${dateTime(p.since)}` : "—")),
        el("td", {}, p.currentPeriod ? period(p.currentPeriod, p.currentPeriodStart, p.currentPeriodEnd) : "—"),
        el("td", {}, p.workplace?.name ?? "—"),
        el("td", {}, badge(p.todayStatus))));
      tableBox.replaceChildren(wrap(table(["Persona", "Ahora", "Último fichaje", "Periodo en curso", "Centro", "Estado del día"], rows)));
    };
    refresh.addEventListener("click", () => load().catch((e) => showMessage(message, "error", "Error.", errorText(e))));
    await load();
  }

  // ---- Informe ---------------------------------------------------------------------------
  async function reportTab(container) {
    const from = el("input", { type: "date", id: "report-from", value: addDays(today(), -6), required: true });
    const to = el("input", { type: "date", id: "report-to", value: today(), required: true });
    const totals = el("div", { id: "report-totals" });
    const result = el("div", { id: "report-table" });
    const form = el("form", { class: "grid", id: "report-form" },
      personSelect("report-person"), field("Desde", from), field("Hasta", to), el("button", { type: "submit" }, "Consultar"));
    container.append(card(isAdmin ? "Informe por persona y periodo" : "Mi informe por periodo", form, totals, result));
    const load = async () => {
      const report = await api.dailyReport({ personId: value("report-person"), from: from.value, to: to.value });
      totals.replaceChildren(el("div", { class: "totals" },
        total("Esperado", minutes(report.expectedMinutes)), total("Trabajado", minutes(report.workedMinutes)),
        total("Desviación (días cerrados)", minutes(report.deviationMinutes), report.deviationMinutes < 0 ? "negative" : "positive"),
        total("Incidencias", report.incidents)));
      result.replaceChildren(wrap(table(
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
          el("td", {}, d.incidents.map((i) => el("div", {}, badge(i.type, INCIDENT_LABELS), " ",
            el("span", { class: "sub" }, i.description)))))))));
    };
    form.addEventListener("submit", (e) => {
      e.preventDefault();
      load().catch((err) => showMessage(message, "error", "No se pudo obtener el informe.", errorText(err)));
    });
    await load();
  }

  function total(label, valueText, cls) {
    return el("div", {}, el("span", {}, label), el("span", { class: cls }, valueText));
  }

  // ---- Incidencias ----------------------------------------------------------------------
  async function incidentsTab(container) {
    const from = el("input", { type: "date", id: "incidents-from", value: addDays(today(), -6), required: true });
    const to = el("input", { type: "date", id: "incidents-to", value: today(), required: true });
    const type = el("select", { id: "incidents-type" }, el("option", { value: "" }, "Todos"),
      ...Object.entries(INCIDENT_LABELS).map(([code, label]) => el("option", { value: code }, label)));
    const result = el("div", { id: "incidents-table" });
    const notice = el("div", { id: "incidents-message" });
    const form = el("form", { class: "grid", id: "incidents-form" }, personSelect("incidents-person", { all: true }),
      field("Desde", from), field("Hasta", to), field("Tipo", type), el("button", { type: "submit" }, "Buscar"));
    container.append(card(isAdmin ? "Incidencias" : "Mis incidencias",
      hint("Ausencias, retrasos y salidas calculados a partir de los fichajes y del horario asignado."),
      form, notice, result));
    const load = async () => {
      const list = await api.listIncidents({ personId: value("incidents-person"), from: from.value, to: to.value, type: type.value });
      const headers = ["Fecha", ...(isAdmin ? ["Persona"] : []), "Tipo", "Periodo", "Detalle"];
      result.replaceChildren(wrap(table(headers, list.map((i) => el("tr", {},
        el("td", { class: "date" }, i.date),
        isAdmin ? el("td", {}, el("strong", {}, i.personId), el("div", { class: "sub" }, nameOf(i.personId))) : null,
        el("td", {}, badge(i.type, INCIDENT_LABELS)), el("td", {}, i.periodLabel), el("td", {}, i.description))),
        "Sin incidencias en el periodo")));
    };
    form.addEventListener("submit", (e) => {
      e.preventDefault();
      load().catch((err) => showMessage(notice, "error", "No se pudieron consultar.", errorText(err)));
    });
    await load();
  }

  // ---- Fichajes -------------------------------------------------------------------------
  async function eventsTab(container) {
    const from = el("input", { type: "date", id: "events-from", value: addDays(today(), -6), required: true });
    const to = el("input", { type: "date", id: "events-to", value: today(), required: true });
    const result = el("div", { id: "events-table" });
    const form = el("form", { class: "grid", id: "events-form" }, personSelect("events-person", { all: true }),
      field("Desde", from), field("Hasta", to), el("button", { type: "submit" }, "Buscar"));
    container.append(card(isAdmin ? "Registro de fichajes" : "Mis fichajes", form, result));
    const load = async () => {
      const list = await api.listClockEvents({ personId: value("events-person"), from: from.value, to: to.value });
      result.replaceChildren(wrap(table(["Fecha y hora", ...(isAdmin ? ["Persona"] : []), "Tipo", "Origen", "Centro", "Nota", "Registrado el"],
        list.map((e) => el("tr", {},
          el("td", {}, dateTime(e.occurredAt)),
          isAdmin ? el("td", {}, `${e.personId} · ${nameOf(e.personId)}`) : null,
          el("td", {}, e.type === "ENTRY" ? "Entrada" : "Salida"), el("td", {}, e.source),
          el("td", {}, e.workplaceId ?? "—"), el("td", {}, e.note ?? ""), el("td", { class: "sub" }, dateTime(e.recordedAt)))),
        "Sin fichajes en el periodo")));
    };
    form.addEventListener("submit", (e) => {
      e.preventDefault();
      load().catch((err) => showMessage(message, "error", "No se pudieron consultar.", errorText(err)));
    });
    await load();
  }

  // ---- Auditoria (responsable) ---------------------------------------------------------
  async function auditTab(container) {
    const entries = await api.audit(50);
    const rows = entries.map((a) => el("tr", {},
      el("td", { class: "num" }, a.sequence), el("td", {}, dateTime(a.at)), el("td", {}, a.actor),
      el("td", {}, a.action), el("td", {}, `${a.entityType} ${a.entityId}`), el("td", { class: "sub" }, a.detail ?? "")));
    const auditTable = el("div", { id: "audit-table" },
      wrap(table(["#", "Fecha", "Usuario", "Acción", "Entidad", "Detalle"], rows)));
    container.append(card("Auditoría de modificaciones",
      hint("Últimos 50 cambios: cuentas, personas, centros, horarios, asignaciones, festivos y fichajes."),
      auditTable));
  }

  await select(tabs[0]);
}
