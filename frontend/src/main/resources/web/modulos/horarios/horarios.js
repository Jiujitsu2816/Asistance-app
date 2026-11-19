// Modulo de DEFINICION DE HORARIOS (responsable): horarios corridos, partidos, turnos
// rotatorios y guardias; su asignacion a personas o equipos en un centro de trabajo; y el
// calendario de festivos.
import { KIND_LABELS, card, el, errorText, field, fillPeople, hint, showMessage, table, today, wrap } from "/shared/ui.js";

export default {
  id: "horarios",
  roles: ["ADMIN"],
  title: () => "Definición de horarios",
  mount,
};

const DAYS = [["MONDAY", "Lun"], ["TUESDAY", "Mar"], ["WEDNESDAY", "Mié"], ["THURSDAY", "Jue"],
  ["FRIDAY", "Vie"], ["SATURDAY", "Sáb"], ["SUNDAY", "Dom"]];

// Formulario estatico (sin datos de usuario): cada tipo de horario tiene su seccion.
const FORM = `
  <div class="grid">
    <label>Nombre <input id="s-name" required maxlength="80" placeholder="p. ej. Oficina central"></label>
    <label>Tipo
      <select id="s-kind">
        <option value="CONTINUOUS">Corrido (oficina)</option>
        <option value="SPLIT">Partido (tienda / restaurante)</option>
        <option value="ROTATING_SHIFT">Turnos rotatorios (hospital)</option>
        <option value="ON_CALL">Guardias (hospital)</option>
      </select>
    </label>
    <label>Tolerancia (min) <input id="s-tolerance" type="number" min="0" max="120" value="10"></label>
  </div>
  <div data-kind="CONTINUOUS SPLIT">
    <fieldset><legend>Días laborables</legend><div class="days" id="s-days"></div></fieldset>
    <fieldset><legend>Tramos horarios <span class="sub" id="slots-hint"></span></legend>
      <div id="s-slots"></div>
      <button type="button" class="secondary small" id="add-slot">Añadir tramo</button>
    </fieldset>
  </div>
  <div data-kind="ROTATING_SHIFT" hidden>
    <div class="grid"><label>Inicio del ciclo <input type="date" id="s-cycle-start"></label></div>
    <fieldset><legend>Días del ciclo (se repite indefinidamente)</legend>
      <div id="s-cycle"></div>
      <button type="button" class="secondary small" id="add-cycle-day">Añadir día</button>
    </fieldset>
  </div>
  <div data-kind="ON_CALL" hidden>
    <div class="grid">
      <label>Hora de inicio <input type="time" id="s-oncall-start" value="08:00"></label>
      <label>Duración (horas) <input type="number" id="s-oncall-hours" min="1" max="24" value="24"></label>
      <label>Modalidad
        <select id="s-oncall-mode">
          <option value="PHYSICAL">Presencia física (exige fichar)</option>
          <option value="LOCALIZED">Localizada (no exige presencia)</option>
        </select>
      </label>
    </div>
    <fieldset><legend>Fechas de guardia</legend>
      <div class="grid"><label>Fecha <input type="date" id="s-oncall-date"></label>
        <button type="button" class="secondary" id="add-date">Añadir fecha</button></div>
      <p class="sub" id="s-dates"></p>
    </fieldset>
  </div>
  <details><summary>JSON que se enviará al backend</summary><pre id="s-preview" class="sub"></pre></details>
  <div class="actions"><button type="submit" id="save-schedule">Guardar horario</button></div>
  <div id="schedule-message"></div>`;

async function mount(root, { api }) {
  const $ = (id) => root.querySelector(`#${id}`);
  let schedules = [];
  let workplaces = [];
  let onCallDates = [];

  const scheduleForm = el("form", { id: "schedule-form" });
  scheduleForm.innerHTML = FORM;
  const assignmentForm = el("form", { class: "grid", id: "assignment-form" },
    field("Horario", el("select", { id: "a-schedule", required: true })),
    field("Asignar a", el("select", { id: "a-target-type" },
      el("option", { value: "person" }, "Persona"), el("option", { value: "team" }, "Equipo"))),
    el("label", { "data-target": "person" }, "Persona", el("select", { id: "a-person" })),
    el("label", { "data-target": "team", hidden: true }, "Equipo", el("input", { id: "a-team", placeholder: "p. ej. administracion" })),
    field("Centro de trabajo", el("select", { id: "a-workplace" })),
    field("Desde", el("input", { type: "date", id: "a-from", required: true, value: today() })),
    field("Hasta (opcional)", el("input", { type: "date", id: "a-to" })),
    el("button", { type: "submit" }, "Asignar"));
  const holidayForm = el("form", { class: "grid", id: "holiday-form" },
    field("Fecha", el("input", { type: "date", id: "h-date", required: true })),
    field("Descripción", el("input", { id: "h-description", required: true, maxlength: 80 })),
    el("button", { type: "submit" }, "Añadir festivo"));

  root.append(
    card("Horarios definidos", el("div", { id: "schedules" })),
    card("Nuevo horario",
      hint("Corrido: un tramo. Partido: dos o más tramos separados por una pausa. Turnos: ciclo de N días que se repite "
        + "(un turno puede cruzar la medianoche). Guardias: fechas concretas, de presencia física o localizadas."),
      scheduleForm),
    card("Asignación de horarios",
      hint("Un horario se asigna a una persona o a un equipo durante un periodo y en un centro de trabajo. La asignación "
        + "individual tiene prioridad sobre la del equipo. Los periodos de un mismo destino no pueden solaparse."),
      assignmentForm, el("div", { id: "assignment-message" }), el("div", { id: "assignments" })),
    card("Calendario de festivos",
      hint("En festivo no se esperan los horarios corridos ni partidos. Los turnos y guardias mantienen la cobertura."),
      holidayForm, el("div", { id: "holiday-message" }), el("div", { id: "holidays" })));

  // ---- Formulario de horario ----------------------------------------------------------
  const kind = () => $("s-kind").value;

  function updateKind() {
    root.querySelectorAll("#schedule-form [data-kind]").forEach((section) => {
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
    $("add-slot").hidden = kind() !== "SPLIT";
    slots.querySelectorAll("button").forEach((b) => { b.hidden = kind() === "CONTINUOUS"; });
    if (kind() === "ROTATING_SHIFT" && !$("s-cycle").children.length) {
      $("s-cycle").append(cycleRow("Mañana", "07:00", 8), cycleRow("Tarde", "15:00", 8),
        cycleRow("Noche", "23:00", 8), cycleRow(null), cycleRow(null));
    }
    preview();
  }

  function slotRow(start, end) {
    const row = el("div", { class: "grid slot" },
      field("Inicio", el("input", { type: "time", value: start, class: "slot-start", required: true })),
      field("Fin", el("input", { type: "time", value: end, class: "slot-end", required: true })),
      el("button", { type: "button", class: "secondary small", onclick: () => { row.remove(); preview(); } }, "Quitar"));
    return row;
  }

  function cycleRow(label, start = "07:00", hours = 8) {
    const rest = label === null;
    const type = el("select", { class: "c-type" },
      el("option", { value: "work", selected: !rest }, "Trabajo"), el("option", { value: "rest", selected: rest }, "Descanso"));
    const fields = [
      field("Turno", el("input", { class: "c-label", value: label ?? "", maxlength: 40 })),
      field("Inicio", el("input", { type: "time", class: "c-start", value: start })),
      field("Horas", el("input", { type: "number", class: "c-hours", min: 1, max: 24, value: hours })),
    ];
    const sync = () => fields.forEach((f) => { f.hidden = type.value === "rest"; });
    type.addEventListener("change", () => { sync(); preview(); });
    sync();
    const row = el("div", { class: "grid cycle-day" }, field("Día", type), ...fields,
      el("button", { type: "button", class: "secondary small", onclick: () => { row.remove(); preview(); } }, "Quitar"));
    return row;
  }

  function renderDates() {
    $("s-dates").replaceChildren(onCallDates.length ? onCallDates.join(", ") : "Ninguna fecha añadida");
    preview();
  }

  /** Cuerpo JSON del contrato POST /api/schedules a partir del formulario. */
  function scheduleBody() {
    const base = { name: $("s-name").value.trim(), kind: kind(), toleranceMinutes: Number($("s-tolerance").value) };
    switch (kind()) {
      case "CONTINUOUS":
      case "SPLIT":
        return {
          ...base,
          workDays: [...root.querySelectorAll("#s-days input:checked")].map((c) => c.value),
          slots: [...root.querySelectorAll("#s-slots .slot")].map((row) => ({
            start: row.querySelector(".slot-start").value, end: row.querySelector(".slot-end").value })),
        };
      case "ROTATING_SHIFT":
        return {
          ...base,
          cycleStart: $("s-cycle-start").value,
          cycle: [...root.querySelectorAll("#s-cycle .cycle-day")].map((row) => (row.querySelector(".c-type").value === "rest"
            ? { rest: true }
            : { label: row.querySelector(".c-label").value, start: row.querySelector(".c-start").value,
                durationMinutes: Number(row.querySelector(".c-hours").value) * 60 })),
        };
      default:
        return {
          ...base, start: $("s-oncall-start").value, durationMinutes: Number($("s-oncall-hours").value) * 60,
          mode: $("s-oncall-mode").value, dates: onCallDates,
        };
    }
  }

  function preview() {
    $("s-preview").textContent = JSON.stringify(scheduleBody(), null, 2);
  }

  $("s-days").replaceChildren(...DAYS.map(([value, label], i) =>
    el("label", {}, el("input", { type: "checkbox", value, checked: i < 5 }), label)));
  $("s-slots").append(slotRow("08:00", "15:00"));
  $("s-cycle-start").value = today();
  scheduleForm.addEventListener("input", preview);
  $("s-kind").addEventListener("change", updateKind);
  $("add-slot").addEventListener("click", () => { $("s-slots").append(slotRow("17:00", "21:00")); preview(); });
  $("add-cycle-day").addEventListener("click", () => { $("s-cycle").append(cycleRow("Mañana")); preview(); });
  $("add-date").addEventListener("click", () => {
    const value = $("s-oncall-date").value;
    if (value && !onCallDates.includes(value)) onCallDates = [...onCallDates, value].sort();
    renderDates();
  });
  scheduleForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const created = await api.createSchedule(scheduleBody());
      showMessage($("schedule-message"), "ok", `Horario guardado: ${created.name}.`, created.summary);
      await loadSchedules();
    } catch (e) {
      showMessage($("schedule-message"), "error", "No se pudo guardar el horario.", errorText(e));
    }
  });
  renderDates();
  updateKind();

  // ---- Listados -------------------------------------------------------------------------
  async function loadSchedules() {
    schedules = await api.listSchedules();
    $("schedules").replaceChildren(wrap(table(["Nombre", "Tipo", "Definición", "Tolerancia"],
      schedules.map((s) => el("tr", {}, el("td", {}, el("strong", {}, s.name), el("div", { class: "sub" }, s.id)),
        el("td", {}, KIND_LABELS[s.kind]), el("td", {}, s.summary), el("td", { class: "num" }, `${s.toleranceMinutes} min`))))));
    $("a-schedule").replaceChildren(...schedules.map((s) => el("option", { value: s.id }, `${s.name} (${KIND_LABELS[s.kind]})`)));
  }

  async function loadAssignments() {
    const assignments = await api.listAssignments();
    const scheduleName = (id) => schedules.find((s) => s.id === id)?.name ?? id;
    const workplaceName = (id) => workplaces.find((w) => w.id === id)?.name ?? (id ?? "—");
    $("assignments").replaceChildren(wrap(table(["Horario", "Destino", "Centro", "Desde", "Hasta"],
      assignments.map((a) => el("tr", {}, el("td", {}, scheduleName(a.scheduleId)),
        el("td", {}, `${a.targetType === "TEAM" ? "Equipo" : "Persona"} ${a.targetId}`),
        el("td", {}, workplaceName(a.workplaceId)),
        el("td", { class: "date" }, a.validFrom), el("td", { class: "date" }, a.validTo ?? "sin fin"))))));
  }

  async function loadHolidays() {
    $("holidays").replaceChildren(wrap(table(["Fecha", "Descripción"],
      (await api.listHolidays()).map((h) => el("tr", {}, el("td", { class: "date" }, h.date), el("td", {}, h.description))))));
  }

  // ---- Asignaciones y festivos ---------------------------------------------------------
  $("a-target-type").addEventListener("change", () => {
    root.querySelectorAll("[data-target]").forEach((l) => { l.hidden = l.dataset.target !== $("a-target-type").value; });
  });
  assignmentForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const toTeam = $("a-target-type").value === "team";
    try {
      await api.assignSchedule({
        scheduleId: $("a-schedule").value,
        personId: toTeam ? undefined : $("a-person").value,
        team: toTeam ? $("a-team").value : undefined,
        workplaceId: $("a-workplace").value || undefined,
        validFrom: $("a-from").value,
        validTo: $("a-to").value || undefined,
      });
      showMessage($("assignment-message"), "ok", "Horario asignado.");
      await loadAssignments();
    } catch (e) {
      showMessage($("assignment-message"), "error", "No se pudo asignar.", errorText(e));
    }
  });
  holidayForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      await api.createHoliday({ date: $("h-date").value, description: $("h-description").value });
      showMessage($("holiday-message"), "ok", "Festivo añadido.");
      await loadHolidays();
    } catch (e) {
      showMessage($("holiday-message"), "error", "No se pudo añadir el festivo.", errorText(e));
    }
  });

  const [people, places] = await Promise.all([api.listPeople(), api.listWorkplaces()]);
  workplaces = places;
  fillPeople($("a-person"), people);
  $("a-workplace").replaceChildren(el("option", { value: "" }, "Sin centro"),
    ...workplaces.map((w) => el("option", { value: w.id }, w.name)));
  await loadSchedules();
  await Promise.all([loadAssignments(), loadHolidays()]);
}
