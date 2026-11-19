// Aplicacion unica: segun el perfil de quien inicia sesion se muestran unos modulos u otros.
// Cada modulo es independiente (su propia carpeta en /modulos) y solo recibe el adaptador de
// la API y el usuario; no comparten estado entre ellos.
import { HttpPresenceApi, apiBaseUrl } from "/shared/api-client.js";
import { clearSession, loadSession } from "/shared/session.js";
import { ROLE_LABELS, el, errorText, showMessage } from "/shared/ui.js";
import fichaje from "/modulos/fichaje/fichaje.js";
import consulta from "/modulos/consulta/consulta.js";
import horarios from "/modulos/horarios/horarios.js";
import personal from "/modulos/personal/personal.js";

const MODULES = [fichaje, consulta, horarios, personal];
const $ = (id) => document.getElementById(id);

function toLogin() {
  clearSession();
  location.replace("/");
}

const session = loadSession();
if (!session?.token) toLogin();

const api = new HttpPresenceApi(apiBaseUrl(), { token: session?.token, onUnauthorized: toLogin });

async function start() {
  let user;
  try {
    user = await api.me();            // comprueba que la sesion sigue siendo valida
  } catch (e) {
    if (e.code === "UNAUTHENTICATED") return toLogin();
    showMessage($("module"), "error", "No se pudo cargar la aplicación.", errorText(e));
    return;
  }
  const available = MODULES.filter((m) => m.roles.includes(user.role));
  const ctx = { api, user, isAdmin: user.role === "ADMIN" };

  $("user").replaceChildren(
    el("span", {}, el("strong", {}, user.displayName), " · ", ROLE_LABELS[user.role]),
    el("button", {
      class: "secondary small", type: "button", id: "logout",
      onclick: async () => { try { await api.logout(); } catch { /* ya cerrada */ } toLogin(); },
    }, "Cerrar sesión"));

  async function show() {
    const wanted = location.hash.substring(1);
    const module = available.find((m) => m.id === wanted) ?? available[0];
    $("nav").replaceChildren(...available.map((m) =>
      el("a", { href: `#${m.id}`, "aria-current": m === module ? "page" : null }, m.title(ctx))));
    const container = el("div", { class: "module", "data-module": module.id });
    $("module").replaceChildren(container);
    document.title = `${module.title(ctx)} · Control de presencia`;
    try {
      await module.mount(container, ctx);
    } catch (e) {
      showMessage(container, "error", "Error en el módulo.", errorText(e));
    }
  }
  window.addEventListener("hashchange", show);
  show();
}

start();
