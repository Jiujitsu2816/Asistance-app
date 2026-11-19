// Modulo de REGISTRO DE ASISTENCIA.
// - Empleado: su estado actual, fichar entrada/salida (hora del servidor) y su horario.
// - Responsable: elegir persona, fichar en su nombre y corregir olvidos indicando la hora.
import {
  addDays, badge, card, dateTime, el, errorText, field, fillPeople, hint, period, showMessage, table, time, today, wrap,
} from "/shared/ui.js";

export default {
  id: "fichaje",
  roles: ["EMPLOYEE", "ADMIN"],
  title: (ctx) => (ctx.isAdmin ? "Registro de asistencia" : "Mi fichaje"),
  mount,
};

async function mount(root, { api, user, isAdmin }) {
  const message = el("div", { id: "message" });
  const status = el("div", { class: "status-panel", id: "presence", "aria-live": "polite" });
  const entry = el("button", { class: "big entry", id: "entry", type: "button" }, "Fichar ENTRADA");
  const exit = el("button", { class: "big exit", id: "exit", type: "button" }, "Fichar SALIDA");
  const events = el("div", { id: "events" });
  const schedule = el("div", { id: "my-schedule" });

  // Controles solo del responsable
  const person = el("select", { id: "person" });
  const source = el("select", { id: "source" },
    el("option", { value: "KIOSK" }, "Terminal de fichaje (KIOSK)"),
    el("option", { value: "WEB" }, "Web o móvil (WEB)"),
    el("option", { value: "ADMIN" }, "Corrección de administración (ADMIN)"));
  const timestamp = el("input", { type: "datetime-local", id: "timestamp" });
  const note = el("input", { type: "text", id: "note", maxlength: 200, placeholder: "p. ej. Olvido de fichaje" });

  const selected = () => (isAdmin ? person.value : user.personId);

  root.append(
    card(isAdmin ? "Registro de asistencia" : `Hola, ${user.displayName}`,
      isAdmin
        ? el("div", { class: "grid" }, field("Persona", person), field("Origen del fichaje", source))
        : hint("Pulsa ENTRADA al empezar tu jornada y SALIDA al terminarla. Se registra la hora del servidor."),
      status,
      el("div", { class: "actions" }, entry, exit),
      isAdmin ? el("details", {},
        el("summary", {}, "Corrección manual (indicar hora y nota)"),
        hint("Para corregir un olvido: el fichaje se registra con la hora indicada (no puede ser futura ni anterior al último "
          + "fichaje) y queda auditado con la hora real en que se introdujo. Usa el origen ADMIN."),
        el("div", { class: "grid" }, field("Hora del fichaje", timestamp), field("Nota", note))) : null,
      message),
    card(isAdmin ? "Fichajes de hoy" : "Mis fichajes de hoy", events),
    card(isAdmin ? "Horario de la persona" : "Mi horario", schedule));

  async function refresh() {
    const personId = selected();
    if (!personId) return;
    const [presence, todayEvents] = await Promise.all([
      api.presenceOf(personId), api.listClockEvents({ personId, from: today(), to: today() })]);
    if (personId !== selected()) return; // se eligio otra persona mientras tanto
    renderPresence(presence);
    renderEvents(todayEvents);
    renderSchedule(personId).catch((e) => showMessage(schedule, "error", "No se pudo cargar el horario.", errorText(e)));
  }

  function renderPresence(p) {
    status.dataset.person = p.personId;
    status.dataset.status = p.status;
    status.dataset.inside = String(p.inside);
    status.replaceChildren(
      el("div", { class: "big-status" }, badge(p.status)),
      el("div", {},
        el("div", {}, p.inside ? `Dentro desde ${dateTime(p.since)}` : (p.since ? `Fuera desde ${dateTime(p.since)}` : "Sin fichajes")),
        el("div", { class: "sub" }, p.currentPeriod
          ? `Periodo actual: ${period(p.currentPeriod, p.currentPeriodStart, p.currentPeriodEnd)}`
          : "Sin periodo de trabajo en curso"),
        p.workplace ? el("div", { class: "sub" }, `Centro: ${p.workplace.name}`) : null),
      el("div", {}, el("span", { class: "sub" }, "Hoy: "), badge(p.todayStatus)));
    entry.classList.toggle("secondary", p.inside);
    exit.classList.toggle("secondary", !p.inside);
  }

  function renderEvents(list) {
    events.replaceChildren(wrap(table(["Hora", "Tipo", "Origen", "Centro", "Nota"],
      list.map((e) => el("tr", {},
        el("td", {}, time(e.occurredAt)), el("td", {}, e.type === "ENTRY" ? "Entrada" : "Salida"),
        el("td", {}, e.source), el("td", {}, e.workplaceId ?? "—"), el("td", {}, e.note ?? ""))),
      "Todavía no hay fichajes hoy")));
  }

  async function renderSchedule(personId) {
    const [assignments, schedules, workplaces, week] = await Promise.all([
      api.listAssignments(personId), api.listSchedules(), api.listWorkplaces(),
      api.dailyReport({ personId, from: today(), to: addDays(today(), 6) })]);
    const scheduleName = (id) => schedules.find((s) => s.id === id)?.name ?? id;
    const workplaceName = (id) => workplaces.find((w) => w.id === id)?.name ?? (id ?? "—");
    schedule.replaceChildren(
      el("h3", {}, "Asignaciones"),
      wrap(table(["Horario", "Centro", "Desde", "Hasta", "Origen"],
        assignments.map((a) => el("tr", {}, el("td", {}, scheduleName(a.scheduleId)),
          el("td", {}, workplaceName(a.workplaceId)), el("td", { class: "date" }, a.validFrom),
          el("td", { class: "date" }, a.validTo ?? "sin fin"),
          el("td", {}, a.targetType === "TEAM" ? `Equipo ${a.targetId}` : "Individual"))),
        "Sin horario asignado")),
      el("h3", {}, "Próximos 7 días"),
      wrap(table(["Fecha", "Horario", "Periodos", "Estado"],
        week.days.map((d) => el("tr", {},
          el("td", { class: "date" }, d.date, d.holiday ? el("div", { class: "sub" }, `Festivo: ${d.holiday}`) : null),
          el("td", {}, d.scheduleName ?? "—"),
          el("td", {}, d.periods.length ? d.periods.map((p) => el("div", {},
            period(p.label, p.expectedStart, p.expectedEnd), p.presenceRequired ? "" : " (localizada)")) : "Libre"),
          el("td", {}, badge(d.status)))))));
  }

  async function clock(type) {
    const body = { personId: selected(), type };
    if (isAdmin) {
      body.source = source.value;
      if (timestamp.value) body.timestamp = new Date(timestamp.value).toISOString();
      if (note.value.trim()) body.note = note.value.trim();
    }
    entry.disabled = exit.disabled = true;
    try {
      const result = await api.clock(body);
      showMessage(message, "ok", `${type === "ENTRY" ? "Entrada" : "Salida"} registrada a las ${time(result.event.occurredAt)}.`);
      timestamp.value = "";
      note.value = "";
      renderPresence(result.presence);
      renderEvents(await api.listClockEvents({ personId: body.personId, from: today(), to: today() }));
    } catch (e) {
      showMessage(message, "error", "Fichaje rechazado.", errorText(e));
    } finally {
      entry.disabled = exit.disabled = false;
    }
  }

  entry.addEventListener("click", () => clock("ENTRY"));
  exit.addEventListener("click", () => clock("EXIT"));
  person.addEventListener("change", () => {
    message.replaceChildren();
    refresh().catch((e) => showMessage(message, "error", "Error al consultar.", errorText(e)));
  });

  if (isAdmin) fillPeople(person, await api.listPeople());
  if (!selected()) {
    showMessage(message, "error", "Tu cuenta no está asociada a ninguna persona.");
    return;
  }
  await refresh();
}
