// Modulo de PERSONAL, ACCESOS Y CENTROS (responsable): alta de personas, cuentas de acceso
// (empleado o responsable) con cambio de clave, y centros de trabajo.
import { ROLE_LABELS, card, dateTime, el, errorText, field, fillPeople, hint, showMessage, table, wrap } from "/shared/ui.js";

export default {
  id: "personal",
  roles: ["ADMIN"],
  title: () => "Personal y centros",
  mount,
};

async function mount(root, { api }) {
  const $ = (id) => root.querySelector(`#${id}`);
  let people = [];

  const personForm = el("form", { class: "grid", id: "person-form" },
    field("Código", el("input", { id: "p-id", required: true, maxlength: 32, placeholder: "EMP-009" })),
    field("Nombre visible", el("input", { id: "p-name", required: true, maxlength: 80 })),
    field("Equipo (opcional)", el("input", { id: "p-team", maxlength: 40 })),
    el("button", { type: "submit" }, "Dar de alta"));
  const accountForm = el("form", { class: "grid", id: "account-form" },
    field("Perfil", el("select", { id: "u-role" },
      el("option", { value: "EMPLOYEE" }, "Empleado"), el("option", { value: "ADMIN" }, "Responsable"))),
    el("label", { id: "u-person-field" }, "Persona", el("select", { id: "u-person" })),
    field("Usuario", el("input", { id: "u-name", required: true, minlength: 3, maxlength: 32, autocomplete: "off",
      placeholder: "p. ej. emp001" })),
    field("Clave inicial", el("input", { id: "u-password", type: "password", required: true, minlength: 8,
      autocomplete: "new-password" })),
    el("button", { type: "submit" }, "Crear acceso"));
  const workplaceForm = el("form", { class: "grid", id: "workplace-form" },
    field("Código", el("input", { id: "w-id", required: true, maxlength: 32, placeholder: "ALMACEN" })),
    field("Nombre", el("input", { id: "w-name", required: true, maxlength: 80 })),
    field("Dirección (opcional)", el("input", { id: "w-address", maxlength: 120 })),
    el("button", { type: "submit" }, "Añadir centro"));

  root.append(
    card("Personas",
      hint("Usa códigos internos y nombres ficticios: no introduzcas datos personales reales."),
      personForm, el("div", { id: "person-message" }), el("div", { id: "people" })),
    card("Accesos a la aplicación",
      hint("Cada empleado entra con su usuario y solo ve su horario y sus fichajes. Los responsables gestionan todo. "
        + "La clave se guarda protegida y nunca se puede consultar; si se olvida, se asigna una nueva (cierra sus sesiones)."),
      accountForm, el("div", { id: "account-message" }), el("div", { id: "accounts" })),
    card("Centros de trabajo",
      hint("Edificios o sedes donde se cumplen los horarios. Se eligen al asignar un horario y quedan en cada fichaje."),
      workplaceForm, el("div", { id: "workplace-message" }), el("div", { id: "workplaces" })));

  const submit = (form, messageId, action, okText) => form.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      await action();
      showMessage($(messageId), "ok", okText);
    } catch (e) {
      showMessage($(messageId), "error", "No se pudo guardar.", errorText(e));
    }
  });

  async function loadPeople() {
    people = await api.listPeople();
    $("people").replaceChildren(wrap(table(["Código", "Nombre", "Equipo"],
      people.map((p) => el("tr", {}, el("td", {}, p.id), el("td", {}, p.displayName), el("td", {}, p.team ?? "—"))))));
    fillPeople($("u-person"), people);
  }

  async function loadAccounts() {
    const accounts = await api.listUsers();
    const nameOf = (id) => people.find((p) => p.id === id)?.displayName ?? id;
    $("accounts").replaceChildren(wrap(table(["Usuario", "Perfil", "Persona", "Creada", "Cambiar clave"],
      accounts.map((a) => el("tr", {}, el("td", {}, el("strong", {}, a.username)), el("td", {}, ROLE_LABELS[a.role]),
        el("td", {}, a.personId ? `${a.personId} · ${nameOf(a.personId)}` : "—"), el("td", {}, dateTime(a.createdAt)),
        el("td", {}, resetForm(a.username)))))));
  }

  function resetForm(username) {
    const input = el("input", { type: "password", minlength: 8, placeholder: "Nueva clave", "aria-label": `Nueva clave de ${username}`,
      autocomplete: "new-password" });
    return el("form", {
      class: "inline", onsubmit: async (event) => {
        event.preventDefault();
        try {
          await api.resetPassword(username, input.value);
          input.value = "";
          showMessage($("account-message"), "ok", `Clave de ${username} cambiada. Sus sesiones abiertas se han cerrado.`);
        } catch (e) {
          showMessage($("account-message"), "error", "No se pudo cambiar la clave.", errorText(e));
        }
      },
    }, input, el("button", { class: "small", type: "submit" }, "Cambiar"));
  }

  async function loadWorkplaces() {
    $("workplaces").replaceChildren(wrap(table(["Código", "Nombre", "Dirección"],
      (await api.listWorkplaces()).map((w) => el("tr", {}, el("td", {}, w.id), el("td", {}, w.name), el("td", {}, w.address ?? "—"))))));
  }

  $("u-role").addEventListener("change", () => { $("u-person-field").hidden = $("u-role").value !== "EMPLOYEE"; });

  submit(personForm, "person-message", async () => {
    await api.createPerson({ id: $("p-id").value, displayName: $("p-name").value, team: $("p-team").value || undefined });
    personForm.reset();
    await loadPeople();
  }, "Persona dada de alta. Ahora puedes crearle un acceso y asignarle un horario.");

  submit(accountForm, "account-message", async () => {
    const role = $("u-role").value;
    await api.createUser({ username: $("u-name").value, password: $("u-password").value, role,
      personId: role === "EMPLOYEE" ? $("u-person").value : undefined });
    $("u-name").value = "";
    $("u-password").value = "";
    await loadAccounts();
  }, "Acceso creado. Comunica el usuario y la clave a la persona.");

  submit(workplaceForm, "workplace-message", async () => {
    await api.createWorkplace({ id: $("w-id").value, name: $("w-name").value, address: $("w-address").value || undefined });
    workplaceForm.reset();
    await loadWorkplaces();
  }, "Centro añadido.");

  await loadPeople();
  await Promise.all([loadAccounts(), loadWorkplaces()]);
}
