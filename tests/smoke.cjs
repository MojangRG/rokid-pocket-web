const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  const page = await browser.newPage({ viewport: { width: 480, height: 640 }, deviceScaleFactor: 1 });
  await page.setContent(`<!doctype html><html lang="ru"><head><title>Новости и сервисы</title></head>
    <body><nav><a href="#unrelated">Длинное меню</a><input placeholder="Поиск по сайту"></nav>
    <main><h1>Новости сегодня</h1><p id="story">Главная история на небольшом экране.</p>
    <a href="#details">Читать материал</a><button onclick="document.querySelector('#story').textContent='Обновлённая история'">Обновить</button>
    <input id="message" aria-label="Сообщение"></main><p hidden>Секретная строка</p></body></html>`);
  const hudSource = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/hud.js'), 'utf8');
  await page.addScriptTag({ content: '(function(){const __rokidBridgeToken="test-token";'
    + hudSource + '})()' });
  const hud = page.locator('#rokid-pocket-web-hud');
  const screen = hud.locator('#screen');
  assert.match(await screen.innerText(), /Главная история/);
  assert.match(await screen.innerText(), /Поиск по сайту/);
  assert.doesNotMatch(await screen.innerText(), /Секретная строка/);

  fs.mkdirSync(path.join(__dirname, '../docs'), { recursive: true });
  await page.screenshot({ path: path.join(__dirname, '../docs/preview.png') });

  await screen.locator('.row').filter({ hasText: 'Обновить' }).last().click();
  await page.waitForTimeout(450);
  assert.match(await screen.innerText(), /Обновлённая история/);

  await screen.getByRole('button', { name: /Сообщение/ }).click();
  await screen.getByRole('button', { name: /abcde/ }).click();
  await screen.getByRole('button', { name: /Буква.*a/ }).click();
  assert.equal(await page.locator('#message').inputValue(), 'a');
  await screen.getByRole('button', { name: /ГОТОВО/ }).click();
  assert.match(await screen.innerText(), /Новости сегодня/);

  await page.evaluate(() => window.__rokidHud.toggle());
  assert.equal(await hud.evaluate(node => getComputedStyle(node).display), 'none');
  assert.equal(await page.evaluate(() => window.__rokidHud.back()), 'handled');
  assert.notEqual(await hud.evaluate(node => getComputedStyle(node).display), 'none');
  await page.evaluate(() => window.__rokidHud.setHeadEnabled(true));
  assert.match(await screen.innerText(), /Голова: выключить/);

  const nativeMessages = [];
  page.on('dialog', async dialog => {
    nativeMessages.push(dialog.message());
    await dialog.accept('');
  });
  await screen.getByRole('button', { name: /Голова: выключить/ }).click();
  await screen.getByRole('button', { name: /Центр головы/ }).click();
  await screen.getByRole('button', { name: /Назад/ }).first().click();
  assert.deepEqual(nativeMessages, ['head', 'center', 'back'].map(action =>
    `__ROKID_POCKET_WEB__:test-token:${action}`));

  await browser.close();
  console.log('HUD smoke test passed: extraction, actions, input, original mode, native menu.');
})().catch(error => { console.error(error); process.exitCode = 1; });
