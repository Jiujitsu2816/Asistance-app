import { HttpPresenceApi, apiBaseUrl } from "/shared/api-client.js";
import { loadSession, saveSession } from "/shared/session.js";
import { errorText, showMessage } from "/shared/ui.js";

const api = new HttpPresenceApi(apiBaseUrl());
const $ = (id) => document.getElementById(id);

function enter(result) {
  saveSession({ token: result.token, user: result.user, expiresAt: result.expiresAt });
  location.assign("/app/");
}

async function init() {
  if (loadSession()?.token) {
    location.assign("/app/");
    return;
  }
  try {
    const status = await api.authStatus();
    $(status.setupRequired ? "setup" : "login").hidden = false;
    $(status.setupRequired ? "setup-user" : "login-user").focus();
  } catch (e) {
    showMessage($("message"), "error", "No se puede conectar con el backend.", errorText(e));
  }
}

$("login-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    enter(await api.login($("login-user").value, $("login-password").value));
  } catch (e) {
    $("login-password").value = "";
    showMessage($("message"), "error", "No se pudo iniciar sesión.", errorText(e));
  }
});

$("setup-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  if ($("setup-password").value !== $("setup-password2").value) {
    showMessage($("message"), "error", "Las claves no coinciden.");
    return;
  }
  try {
    enter(await api.setup($("setup-user").value, $("setup-password").value));
  } catch (e) {
    showMessage($("message"), "error", "No se pudo crear el responsable.", errorText(e));
  }
});

init();
