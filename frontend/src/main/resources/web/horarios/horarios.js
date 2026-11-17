import { createPresenceApi } from "/shared/api-client.js";
import { KIND_LABELS, el, errorText, fillPeople, showMessage, table, today } from "/shared/ui.js";

const api = createPresenceApi("app-horarios");
const $ = (id) => document.getElementById(id);
const DAYS = [["MONDAY", "Lun"], ["TUESDAY", "Mar"], ["WEDNESDAY", "Mié"], ["THURSDAY", "Jue"],
  ["FRIDAY", "Vie"], ["SATURDAY", "Sáb"], ["SUNDAY", "Dom"]];
let schedules = [];
let onCallDates = [];

// ---- Formulario de horario: secciones segun el tipo ----------------------------------
function kind() { return $("s-kind").value; }

function updateKind() {
  document.querySelectorAll("#schedule-form [data-kind]").forEach((section) => {
    section.hidden = !section.dataset.kind.split(" ").includes(kind());
  });
  const slots = $("s-slots");
  if (kind() === "CONTINUOUS") {
    while (slots.children.length > 1) slots.lastChild.remove();
    $("slots-hint").textContent = "(un único tramo)";
  } else if (kind() === "SPLIT") {
    while (slots.children.length < 2) slots.append(slotRow("17:00", "21:00"));
    $("slots-hint").textContent = "(dos o más tramos separados por una pausa)";
  }
  // Un horario corrido tiene exactamente un tramo: no se puede quitar.
  slots.querySelectorAll("button").forEach((b) => { b.hidden = kind() === "CONTINUOUS"; });
  if (kind() === "ROTATING_SHIFT" && !$("s-cycle").children.length) {
    $("s-cycle").append(cycleRow("Mañana", "07:00", 8), cycleRow("Tarde", "15:00", 8),
      cycleRow("Noche", "23:00", 8), cycleRow(null), cycleRow(null));
  }
  preview();
}

function slotRow(start, end) {
  const row = el("div", { class: "grid slot" },
    el("label", {}, "Inicio", el("input", { type: "time", value: start, class: "slot-start", required: true })),
    el("label", {}, "Fin", el("input", { type: "time", value: end, class: "slot-end", required: true })),
    el("button", { type: "button", class: "secondary small", onclick: () => { row.remove(); preview(); } }, "Quitar"));
  return row;
}

function cycleRow(label, start = "07:00", hours = 8) {
  const rest = label === null;
  const type = el("select", { class: "c-type" },
    el("option", { value: "work", selected: !rest }, "Trabajo"),
    el("option", { value: "rest", selected: rest }, "Descanso"));
  const fields = [
    el("label", {}, "Turno", el("input", { class: "c-label", value: label ?? "", maxlength: 40 })),
    el("label", {}, "Inicio", el("input", { type: "time", class: "c-start", value: start })),
    el("label", {}, "Horas", el("input", { type: "number", class: "c-hours", min: 1, max: 24, value: hours })),
  ];
  const sync = () => fields.forEach((f) => { f.hidden = type.value === "rest"; });
  type.addEventListener("change", () => { sync(); preview(); });
  sync();
  const row = el("div", { class: "grid cycle-day" }, el("label", {}, "Día", type), ...fields,
    el("button", { type: "button", class: "secondary small", onclick: () => { row.remove(); preview(); } }, "Quitar"));
  return row;
}

function renderDates() {
  $("s-dates").replaceChildren(onCallDates.length ? onCallDates.join(", ") : "Ninguna fecha añadida");
  preview();
}

/** Construye el cuerpo JSON del contrato POST /api/schedules a partir del formulario. */
function scheduleBody() {
  const base = { name: $("s-name").value.trim(), kind: kind(), toleranceMinutes: Number($("s-tolerance").value) };
  switch (kind()) {
    case "CONTINUOUS":
    case "SPLIT":
      return {
        ...base,
        workDays: [...document.querySelectorAll("#s-days input:checked")].map((c) => c.value),
        slots: [...document.querySelectorAll("#s-slots .slot")].map((row) => ({
          start: row.querySelector(".slot-start").value, end: row.querySelector(".slot-end").value })),
      };
    case "ROTATING_SHIFT":
      return {
        ...base,
        cycleStart: $("s-cycle-start").value,
        cycle: [...document.querySelectorAll("#s-cycle .cycle-day")].map((row) => row.querySelector(".c-type").value === "rest"
          ? { rest: true }
          : { label: row.querySelector(".c-label").value, start: row.querySelector(".c-start").value,
              durationMinutes: Number(row.querySelector(".c-hours").value) * 60 }),
      };
    case "ON_CALL":
      return {
        ...base, start: $("s-oncall-start").value, durationMinutes: Number($("s-oncall-hours").value) * 60,
        mode: $("s-oncall-mode").value, dates: onCallDates,
      };
  }
}

function preview() {
  $("s-preview").textContent = JSON.stringify(scheduleBody(), null, 2);
}

$("schedule-form").addEventListener("input", preview);
$("s-kind").addEventListener("change", updateKind);
$("add-slot").addEventListener("click", () => { $("s-slots").append(slotRow("17:00", "21:00")); preview(); });
$("add-cycle-day").addEventListener("click", () => { $("s-cycle").append(cycleRow("Mañana")); preview(); });
$("add-date").addEventListener("click", () => {
  const value = $("s-oncall-date").value;
  if (value && !onCallDates.includes(value)) onCallDates = [...onCallDates, value].sort();
  renderDates();
});
$("schedule-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const created = await api.createSchedule(scheduleBody());
    showMessage($("schedule-message"), "ok", `Horario guardado: ${created.name}.`, created.summary);
    await loadSchedules();
  } catch (e) {
    showMessage($("schedule-message"), "error", "No se pudo guardar el horario.", errorText(e));
  }
});

// ---- Listados -------------------------------------------------------------------------
async function loadSchedules() {
  schedules = await api.listSchedules();
  $("schedules").replaceChildren(table(["Nombre", "Tipo", "Definición", "Tolerancia"],
    schedules.map((s) => el("tr", {}, el("td", {}, el("strong", {}, s.name), el("div", { class: "sub" }, s.id)),
      el("td", {}, KIND_LABELS[s.kind]), el("td", {}, s.summary), el("td", { class: "num" }, `${s.toleranceMinutes} min`)))));
  $("a-schedule").replaceChildren(...schedules.map((s) => el("option", { value: s.id }, `${s.name} (${KIND_LABELS[s.kind]})`)));
}

async function loadAssignments() {
  const assignments = await api.listAssignments();
  const nameOf = (id) => schedules.find((s) => s.id === id)?.name ?? id;
  $("assignments").replaceChildren(table(["Horario", "Destino", "Desde", "Hasta"],
    assignments.map((a) => el("tr", {}, el("td", {}, nameOf(a.scheduleId)),
      el("td", {}, `${a.targetType === "TEAM" ? "Equipo" : "Persona"} ${a.targetId}`),
      el("td", {}, a.validFrom), el("td", {}, a.validTo ?? "sin fin")))));
}

async function loadHolidays() {
  $("holidays").replaceChildren(table(["Fecha", "Descripción"],
    (await api.listHolidays()).map((h) => el("tr", {}, el("td", {}, h.date), el("td", {}, h.description)))));
}

async function loadPeople() {
  const people = await api.listPeople();
  fillPeople($("a-person"), people);
  $("people").replaceChildren(table(["Código", "Nombre", "Equipo"],
    people.map((p) => el("tr", {}, el("td", {}, p.id), el("td", {}, p.displayName), el("td", {}, p.team ?? "—")))));
}

// ---- Asignaciones, festivos y personas -------------------------------------------------
$("a-target-type").addEventListener("change", () => {
  document.querySelectorAll("[data-target]").forEach((l) => { l.hidden = l.dataset.target !== $("a-target-type").value; });
});
$("assignment-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const toTeam = $("a-target-type").value === "team";
  try {
    await api.assignSchedule({
      scheduleId: $("a-schedule").value,
      personId: toTeam ? undefined : $("a-person").value,
      team: toTeam ? $("a-team").value : undefined,
      validFrom: $("a-from").value,
      validTo: $("a-to").value || undefined,
    });
    showMessage($("assignment-message"), "ok", "Horario asignado.");
    await loadAssignments();
  } catch (e) {
    showMessage($("assignment-message"), "error", "No se pudo asignar.", errorText(e));
  }
});

$("holiday-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    await api.createHoliday({ date: $("h-date").value, description: $("h-description").value });
    showMessage($("holiday-message"), "ok", "Festivo añadido.");
    await loadHolidays();
  } catch (e) {
    showMessage($("holiday-message"), "error", "No se pudo añadir el festivo.", errorText(e));
  }
});

$("person-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    await api.createPerson({ id: $("p-id").value, displayName: $("p-name").value, team: $("p-team").value || undefined });
    showMessage($("person-message"), "ok", "Persona dada de alta.");
    event.target.reset();
    await loadPeople();
  } catch (e) {
    showMessage($("person-message"), "error", "No se pudo dar de alta.", errorText(e));
  }
});

// ---- Inicio ---------------------------------------------------------------------------
async function init() {
  $("s-days").replaceChildren(...DAYS.map(([value, label], i) =>
    el("label", {}, el("input", { type: "checkbox", value, checked: i < 5 }), label)));
  $("s-slots").append(slotRow("08:00", "15:00"));
  $("s-cycle-start").value = today();
  $("a-from").value = today();
  renderDates();
  updateKind();
  try {
    await loadSchedules();
    await Promise.all([loadAssignments(), loadHolidays(), loadPeople()]);
  } catch (e) {
    showMessage($("schedule-message"), "error", "No se pudo cargar la aplicación.", errorText(e));
  }
}
init();
