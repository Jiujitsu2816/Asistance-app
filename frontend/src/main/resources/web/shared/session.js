// Sesion del navegador: token y usuario. Se guarda en sessionStorage (por pestaña). Si el
// almacenamiento no esta disponible, la sesion dura mientras la pagina este abierta.
const KEY = "presencia.session";
let memory = null;

export function saveSession(session) {
  memory = session;
  try { sessionStorage.setItem(KEY, JSON.stringify(session)); } catch { /* sin almacenamiento */ }
}

export function loadSession() {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (raw) return JSON.parse(raw);
  } catch { /* sin almacenamiento */ }
  return memory;
}

export function clearSession() {
  memory = null;
  try { sessionStorage.removeItem(KEY); } catch { /* sin almacenamiento */ }
}
