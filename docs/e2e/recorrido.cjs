// Recorrido de extremo a extremo de la aplicacion web con un navegador (Playwright).
// Requiere el sistema arrancado con una base de datos NUEVA (datos de ejemplo, sin cuentas):
//   java -jar backend/target/presencia-backend-app.jar     (en una terminal)
//   java -jar frontend/target/presencia-frontend.jar       (en otra)
//   node docs/e2e/recorrido.cjs [carpeta-de-capturas]
// Crea el responsable, da acceso a EMP-001, ficha como empleado y revisa los modulos del
// responsable. Falla (codigo de salida 1) si algo no aparece o hay errores de JavaScript.
const { chromium } = require('playwright');
const assert = require('assert');

const base = process.env.APP_URL || 'http://localhost:8081';
const shots = process.argv[2];
// Claves creadas solo para este recorrido sobre datos ficticios locales.
const ADMIN = { user: 'responsable', pass: 'Recorrido-2026' };
const EMP = { user: 'emp001', pass: 'Empleado-2026' };

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 860 } });
  const jsErrors = [];
  page.on('pageerror', (e) => jsErrors.push(String(e)));
  const shot = async (name, opts = {}) => { if (shots) await page.screenshot({ path: `${shots}/${name}.png`, fullPage: true, ...opts }); };
  const text = (sel) => page.textContent(sel);
  const step = (msg) => console.log('✔ ' + msg);

  // 1. Primer arranque: crear el responsable
  await page.goto(base + '/');
  await page.waitForSelector('#setup:not([hidden])');
  await shot('login-primer-arranque');
  await page.fill('#setup-user', ADMIN.user);
  await page.fill('#setup-password', ADMIN.pass);
  await page.fill('#setup-password2', ADMIN.pass);
  await page.click('#setup-form button');
  await page.waitForURL('**/app/**');
  await page.waitForSelector('nav a');
  const adminNav = await page.$$eval('nav a', (as) => as.map((a) => a.textContent));
  assert.deepStrictEqual(adminNav, ['Registro de asistencia', 'Consulta de presencia', 'Definición de horarios', 'Personal y centros']);
  step('responsable creado; menú de responsable: ' + adminNav.join(', '));

  // 2. Personal: crear el acceso de EMP-001
  await page.click('nav a[href="#personal"]');
  await page.waitForSelector('#accounts table');
  await page.selectOption('#u-person', 'EMP-001');
  await page.fill('#u-name', EMP.user);
  await page.fill('#u-password', EMP.pass);
  await page.click('#account-form button');
  await page.waitForSelector('#account-message .message.ok');
  await page.waitForFunction(() => document.querySelector('#accounts').textContent.includes('emp001'));
  await shot('personal');
  step('acceso creado para EMP-001');
  await page.fill('#u-name', 'otro');
  await page.fill('#u-password', 'corta');
  await page.$eval('#u-password', (i) => i.removeAttribute('minlength'));
  await page.click('#account-form button');
  await page.waitForSelector('#account-message .message.error');
  assert.match(await text('#account-message'), /INVALID_DATA/);
  step('clave demasiado corta rechazada');

  // 3. Cerrar sesion y entrar como empleado
  await page.click('#logout');
  await page.waitForSelector('#login:not([hidden])');
  await page.fill('#login-user', EMP.user);
  await page.fill('#login-password', 'mala-clave');
  await page.click('#login-form button');
  await page.waitForSelector('#message .message.error');
  assert.match(await text('#message'), /INVALID_CREDENTIALS/);
  await page.fill('#login-password', EMP.pass);
  await page.click('#login-form button');
  await page.waitForURL('**/app/**');
  await page.waitForSelector('#presence .badge');
  const empNav = await page.$$eval('nav a', (as) => as.map((a) => a.textContent));
  assert.deepStrictEqual(empNav, ['Mi fichaje', 'Mis registros']);
  step('empleado: credencial errónea rechazada; menú de empleado: ' + empNav.join(', '));
  assert.strictEqual(await page.$('#person'), null, 'el empleado no elige persona');
  await page.click('#entry');
  await page.waitForSelector('#message .message.ok');
  // El estado exacto depende de la hora (Presente, Presente fuera de horario...): se comprueba que esta dentro.
  await page.waitForFunction(() => document.querySelector('#presence').dataset.inside === 'true');
  await page.waitForSelector('#events tbody td:not(.empty)');
  await page.waitForSelector('#my-schedule table');
  await shot('empleado-fichaje');
  step('empleado ficha la entrada: ' + (await text('#message')).trim());
  await page.click('#entry');
  await page.waitForFunction(() => document.querySelector('#message').textContent.includes('DUPLICATE_CLOCK_EVENT'));
  step('segunda entrada rechazada (DUPLICATE_CLOCK_EVENT)');
  await page.click('nav a[href="#consulta"]');
  await page.waitForSelector('#report-table table');
  await shot('empleado-registros');
  assert.strictEqual(await page.$('#report-person'), null);
  step('empleado consulta su informe');
  // Intentar entrar en un modulo de responsable redirige a sus propios modulos
  await page.goto(base + '/app/#horarios');
  await page.waitForSelector('[data-module="fichaje"]');
  step('el empleado no puede abrir "horarios" (muestra su fichaje)');
  await page.click('#logout');
  await page.waitForSelector('#login:not([hidden])');

  // 4. Responsable: consulta, horarios y fichaje en nombre de otra persona
  await page.fill('#login-user', ADMIN.user);
  await page.fill('#login-password', ADMIN.pass);
  await page.click('#login-form button');
  await page.waitForURL('**/app/**');
  await page.click('nav a[href="#consulta"]');
  await page.waitForSelector('#presence-table tbody tr');
  assert.match(await text('#presence-table'), /Oficina central/);
  await shot('consulta-presencia');
  step('presencia actual con centro de trabajo');
  await page.click('[data-tab="report"]');
  await page.selectOption('#report-person', 'EMP-002');
  await page.click('#report-form button');
  await page.waitForFunction(() => document.querySelector('#report-table').textContent.includes('Retraso'));
  await shot('consulta-informe');
  step('informe por persona y periodo');
  await page.click('[data-tab="incidents"]');
  await page.waitForSelector('#incidents-table form');
  await page.fill('#incidents-table form input', 'Cita médica justificada');
  await page.click('#incidents-table form button');
  await page.waitForSelector('#incidents-message .message.ok');
  await shot('consulta-incidencias');
  step('incidencia justificada');
  await page.click('[data-tab="audit"]');
  await page.waitForSelector('#audit-table tbody tr');
  assert.match(await text('#audit-table'), /INCIDENT_JUSTIFIED/);
  await shot('consulta-auditoria', { fullPage: false });
  step('auditoría con el usuario que hizo cada cambio');
  await page.click('nav a[href="#horarios"]');
  await page.waitForSelector('#schedules tbody tr');
  await shot('horarios');
  await page.fill('#s-name', 'Guardia sin fechas');
  await page.selectOption('#s-kind', 'ON_CALL');
  await page.click('#save-schedule');
  await page.waitForSelector('#schedule-message .message.error');
  await page.fill('#s-name', 'UCI de prueba');
  await page.selectOption('#s-kind', 'ROTATING_SHIFT');
  await page.click('#save-schedule');
  await page.waitForFunction(() => document.querySelector('#schedule-message').textContent.includes('Horario guardado'));
  await page.locator('#schedule-form').screenshot(shots ? { path: `${shots}/horarios-turnos.png` } : {});
  step('turnos rotatorios creados; guardia sin fechas rechazada');
  await page.click('nav a[href="#fichaje"]');
  await page.waitForSelector('#person option', { state: 'attached' });
  await page.selectOption('#person', 'EMP-007');
  await page.waitForFunction(() => document.querySelector('#presence').dataset.person === 'EMP-007');
  await page.click('#entry');
  await page.waitForFunction(() => document.querySelector('#message').textContent.includes('SCHEDULE_NOT_ASSIGNED'));
  await shot('responsable-fichaje-error');
  step('fichaje de una persona sin horario rechazado (SCHEDULE_NOT_ASSIGNED)');

  assert.deepStrictEqual(jsErrors, [], 'errores de JavaScript: ' + jsErrors.join('\n'));
  step('sin errores de JavaScript');
  await browser.close();
})().catch((e) => { console.error('✘ ' + e.message); process.exit(1); });
