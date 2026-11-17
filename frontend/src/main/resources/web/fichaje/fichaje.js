import { createPresenceApi } from "/shared/api-client.js";
import { badge, el, errorText, fillPeople, period, showMessage, table, time, today, dateTime } from "/shared/ui.js";

const api = createPresenceApi("app-fichaje");
const $ = (id) => document.getElementById(id);

async function init() {
  try {
    const people = await api.listPeople();
    fillPeople($("person"), people);
    const remembered = sessionStorage.getItem("fichaje.person");
    if (remembered && people.some((p) => p.id === remembered)) $("person").value = remembered;
    await refresh();
  } catch (e) {
    showMessage($("message"), "error", "No se pudo cargar la aplicación.", errorText(e));
  }
}

async function refresh() {
  const personId = $("person").value;
  if (!personId) return;
  sessionStorage.setItem("fichaje.person", personId);
  const [presence, events] = await Promise.all([
    api.presenceOf(personId),
    api.listClockEvents({ personId, from: today(), to: today() }),
  ]);
  renderPresence(presence);
  renderEvents(events);
}

function renderPresence(p) {
  $("status").replaceChildren(
    el("div", { class: "big-status" }, badge(p.status)),
    el("div", {},
      el("div", {}, p.inside ? `Dentro desde ${dateTime(p.since)}` : (p.since ? `Fuera desde ${dateTime(p.since)}` : "Sin fichajes")),
      el("div", { class: "sub" }, p.currentPeriod
        ? `Periodo actual: ${period(p.currentPeriod, p.currentPeriodStart, p.currentPeriodEnd)}`
        : "Sin periodo de trabajo en curso"),
    ),
    el("div", {}, el("span", { class: "sub" }, "Hoy: "), badge(p.todayStatus)),
  );
  $("entry").classList.toggle("secondary", p.inside);
  $("exit").classList.toggle("secondary", !p.inside);
}

function renderEvents(events) {
  $("events").replaceChildren(table(["Hora", "Tipo", "Origen", "Nota", "Registrado"],
    events.map((e) => el("tr", {},
      el("td", {}, time(e.occurredAt)),
      el("td", {}, e.type === "ENTRY" ? "Entrada" : "Salida"),
      el("td", {}, e.source),
      el("td", {}, e.note ?? ""),
      el("td", { class: "sub" }, dateTime(e.recordedAt)))),
    "Todavía no hay fichajes hoy"));
}

async function clock(type) {
  const personId = $("person").value;
  const local = $("timestamp").value;
  const body = {
    personId,
    type,
    source: $("source").value,
    timestamp: local ? new Date(local).toISOString() : undefined,
    note: $("note").value.trim() || undefined,
  };
  for (const button of [$("entry"), $("exit")]) button.disabled = true;
  try {
    const result = await api.clock(body);
    showMessage($("message"), "ok", `${type === "ENTRY" ? "Entrada" : "Salida"} registrada a las ${time(result.event.occurredAt)}.`);
    $("timestamp").value = "";
    $("note").value = "";
    renderPresence(result.presence);
    renderEvents(await api.listClockEvents({ personId, from: today(), to: today() }));
  } catch (e) {
    showMessage($("message"), "error", "Fichaje rechazado.", errorText(e));
  } finally {
    for (const button of [$("entry"), $("exit")]) button.disabled = false;
  }
}

$("person").addEventListener("change", () => {
  $("message").replaceChildren();
  refresh().catch((e) => showMessage($("message"), "error", "Error al consultar.", errorText(e)));
});
$("entry").addEventListener("click", () => clock("ENTRY"));
$("exit").addEventListener("click", () => clock("EXIT"));
init();
