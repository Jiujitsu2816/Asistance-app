// Script opcional que recorre las tres aplicaciones con un navegador y genera las capturas
// del README. Requiere Node.js y Playwright, y backend + frontends arrancados con una BD
// recien creada (datos de ejemplo):  node docs/capturas/generar-capturas.cjs docs/capturas
const { chromium } = require('playwright');
const out = process.argv[2] || '.';
const base = process.env.FRONTEND_URL || 'http://localhost:8081';

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 860 }, deviceScaleFactor: 1 });
  const shot = (name, opts = {}) => page.screenshot({ path: `${out}/${name}.png`, fullPage: true, ...opts });

  // Registro de asistencia: entrada correcta y fichaje duplicado rechazado.
  await page.goto(`${base}/fichaje/`);
  await page.waitForSelector('#person option', { state: 'attached' });
  await page.selectOption('#person', 'EMP-001');
  await page.waitForSelector('#status .badge');
  await page.click('#entry');
  await page.waitForSelector('#events tbody tr td:not(.empty)');
  await shot('fichaje');
  await page.click('#entry');
  await page.waitForSelector('.message.error');
  await shot('fichaje-error');

  // Consulta: presencia actual, informe, incidencias (con una justificacion) y auditoria.
  await page.goto(`${base}/consulta/#presence`);
  await page.waitForSelector('#presence-table tbody tr td:not(.empty)');
  await shot('consulta-presencia');
  await page.click('[data-tab=report]');
  await page.selectOption('#report-person', 'EMP-002');
  await page.click('#report-form button');
  await page.waitForSelector('#report-table tbody tr');
  await shot('consulta-informe');
  await page.click('[data-tab=incidents]');
  await page.selectOption('#incidents-type', 'LATE_ARRIVAL');
  await page.click('#incidents-form button');
  await page.waitForSelector('#incidents-table form');
  await page.locator('#incidents-table form input').first().fill('Atasco por obras, justificado por RR. HH.');
  await page.locator('#incidents-table form button').first().click();
  await page.waitForSelector('#incidents-message .message.ok');
  await page.selectOption('#incidents-type', '');
  await page.click('#incidents-form button');
  await page.waitForTimeout(500);
  await shot('consulta-incidencias');
  await page.click('[data-tab=audit]');
  await page.waitForSelector('#audit-table tbody tr td:not(.empty)');
  await shot('consulta-auditoria', { fullPage: false });

  // Definicion de horarios: listado y formulario de turnos rotatorios.
  await page.goto(`${base}/horarios/`);
  await page.waitForSelector('#schedules tbody tr td:not(.empty)');
  await shot('horarios');
  await page.selectOption('#s-kind', 'ROTATING_SHIFT');
  await page.fill('#s-name', 'UCI - turnos de 12 h');
  await page.locator('#schedule-form').screenshot({ path: `${out}/horarios-turnos.png` });
  await browser.close();
})();
