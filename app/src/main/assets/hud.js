(function () {
  'use strict';
  if (window.__rokidHud) { window.__rokidHud.refresh(); return; }

  const host = document.createElement('div');
  host.id = 'rokid-pocket-web-hud';
  host.style.setProperty('position', 'fixed', 'important');
  host.style.setProperty('inset', '0', 'important');
  host.style.setProperty('z-index', '2147483647', 'important');
  host.style.setProperty('display', 'block', 'important');
  const shadow = host.attachShadow({ mode: 'open' });
  const style = document.createElement('style');
  style.textContent = `
    :host { all: initial; }
    * { box-sizing: border-box; }
    #screen { position: fixed; inset: 0; z-index: 2147483647; overflow: hidden;
      background: #000; color: #a4ffba; font: 21px/1.23 system-ui, sans-serif;
      padding: 46px 16px 18px; display: flex; flex-direction: column; }
    #heading { color: #d7ffe0; font: 700 18px/1.15 system-ui, sans-serif;
      white-space: nowrap; text-overflow: ellipsis; overflow: hidden; }
    #origin { color: #70aa80; font-size: 14px; white-space: nowrap;
      overflow: hidden; text-overflow: ellipsis; padding: 3px 0 10px; }
    #list { flex: 1; overflow-y: auto; scrollbar-width: none; }
    #list::-webkit-scrollbar { display: none; }
    .row { width: 100%; min-height: 53px; color: #b3ffc4; padding: 11px 13px;
      border: 1px solid #1b5c2b; border-radius: 7px; margin-bottom: 8px;
      background: #031006; text-align: left; font: inherit; overflow-wrap: anywhere; }
    .row.selected { color: #edfff1; border: 2px solid #a4ffba;
      background: #164328; padding: 10px 12px; }
    .row .kind { display: block; color: #74ba83; font-size: 13px; margin-bottom: 4px; }
    .row.selected .kind { color: #b7edc0; }
    #footer { flex: none; color: #8ac799; border-top: 1px solid #366443;
      font-size: 14px; padding-top: 9px; text-align: center; }
    #draft { border: 1px solid #79da91; border-radius: 7px; padding: 12px;
      margin: 4px 0 10px; min-height: 58px; overflow-wrap: anywhere;
      max-height: 100px; overflow-y: auto; }
  `;
  shadow.appendChild(style);
  const screen = document.createElement('div');
  screen.id = 'screen';
  shadow.appendChild(screen);

  const state = { raw: false, home: false, headEnabled: false, index: 0, rawIndex: -1, rows: [],
    keyboard: null, maxRows: 100, observer: null, refreshTimer: null,
    originalFilter: null };
  const groups = ['АБВГ', 'ДЕЁЖЗ', 'ИЙКЛ', 'МНОП', 'РСТУ', 'ФХЦЧ',
    'ШЩЪЫ', 'ЬЭЮЯ', 'abcde', 'fghij', 'klmno', 'pqrst', 'uvwxy',
    'z0123', '456789', '.,@:/-_', '?=&%+#', 'ПРОБЕЛ', '⌫', 'ГОТОВО', 'ОТПРАВИТЬ'];
  const bookmarks = [
    ['Google', 'https://www.google.com'],
    ['Wikipedia', 'https://www.wikipedia.org'],
    ['GitHub', 'https://github.com'],
    ['YouTube', 'https://m.youtube.com'],
    ['Telegram Web', 'https://web.telegram.org'],
    ['ChatGPT', 'https://chatgpt.com'],
    ['DeepSeek', 'https://chat.deepseek.com'],
    ['Qwen', 'https://chat.qwen.ai']
  ];

  function small(text, limit) {
    const value = String(text || '').replace(/\s+/g, ' ').trim();
    return value.length > limit ? value.slice(0, limit - 1) + '…' : value;
  }

  function isVisible(element) {
    if (!element || !element.isConnected || element.closest('#rokid-pocket-web-hud')) return false;
    if (element.closest('[hidden],[aria-hidden="true"],template')) return false;
    let cursor = element;
    for (let depth = 0; cursor && depth < 8; depth++, cursor = cursor.parentElement) {
      const css = getComputedStyle(cursor);
      if (css.display === 'none' || css.visibility === 'hidden') return false;
    }
    const box = element.getBoundingClientRect();
    return box.width > 0 && box.height > 0;
  }

  function labelFor(element) {
    const kind = element.tagName.toLowerCase();
    if (kind === 'input' || kind === 'textarea' || kind === 'select') {
      return small(element.getAttribute('aria-label') ||
        (element.labels && element.labels[0] && element.labels[0].innerText) ||
        element.placeholder || element.value || element.name || element.type || 'Ввод текста', 110);
    }
    return small(element.innerText || element.getAttribute('aria-label') ||
      element.getAttribute('title') || element.textContent, 240);
  }

  function collect() {
    const selector = 'h1,h2,h3,p,li,blockquote,pre,a,button,input,textarea,select,' +
      '[role="button"],[role="link"],[contenteditable="true"]';
    const primary = document.querySelector('main,article,[role="main"]');
    const all = [...document.querySelectorAll(selector)];
    // Place a site's main content before large navigation menus, while keeping
    // search, sign-in and other header controls reachable near the top.
    const outsideControls = primary ? all.filter(element =>
      !primary.contains(element) && element.matches('input,textarea,select,button')).slice(0, 20) : [];
    const candidates = primary ? outsideControls.concat(
      [...primary.querySelectorAll(selector)], all.filter(element => !primary.contains(element))) : all;
    const output = [];
    const seen = new Set();
    const visited = new Set();
    for (const element of candidates) {
      if (visited.has(element)) continue;
      visited.add(element);
      if (output.length >= state.maxRows || !isVisible(element)) continue;
      const tag = element.tagName.toLowerCase();
      if (tag === 'input' && ['hidden', 'reset', 'image', 'file'].includes(element.type)) continue;
      const interactive = element.matches('a,button,input,textarea,select,' +
        '[role="button"],[role="link"],[contenteditable="true"]');
      if (!interactive && element.closest('a,button,li,[role="button"],[role="link"]')) continue;
      const label = labelFor(element);
      if (!label) continue;
      const kind = element.matches('input[type="checkbox"],input[type="radio"],input[type="submit"]') ? 'Действие' :
        element.matches('input,textarea,select,[contenteditable="true"]') ? 'Поле' :
        element.matches('button,[role="button"]') ? 'Действие' :
        element.matches('a,[role="link"]') ? 'Ссылка' :
        element.matches('h1,h2,h3') ? 'Заголовок' : 'Текст';
      // Nested widgets often repeat a heading and link with the same label.
      const key = kind + ':' + label;
      if (seen.has(key) && !interactive) continue;
      seen.add(key);
      output.push({ kind, label, element });
    }
    return output;
  }

  function row(kind, label, selected, callback) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'row' + (selected ? ' selected' : '');
    const caption = document.createElement('span');
    caption.className = 'kind';
    caption.textContent = kind;
    const content = document.createElement('span');
    content.textContent = label;
    button.append(caption, content);
    button.addEventListener('click', callback);
    return button;
  }

  function sendNative(action) {
    // A per-page token is supplied by Android inside this closure. Site scripts
    // cannot request native actions by guessing a public custom URL.
    window.prompt('__ROKID_POCKET_WEB__:' + __rokidBridgeToken + ':' + action, '');
  }

  function shell(title, origin) {
    screen.replaceChildren();
    const heading = document.createElement('div');
    heading.id = 'heading';
    heading.textContent = small(title, 100);
    const site = document.createElement('div');
    site.id = 'origin';
    site.textContent = small(origin, 90);
    const list = document.createElement('div');
    list.id = 'list';
    const footer = document.createElement('div');
    footer.id = 'footer';
    screen.append(heading, site, list, footer);
    return { list, footer };
  }

  function scrollSelection() {
    const selected = screen.querySelector('.selected');
    if (selected) selected.scrollIntoView({ block: 'nearest' });
  }

  function render() {
    if (state.raw) return;
    if (state.keyboard) { renderKeyboard(); return; }
    const { list, footer } = shell(state.home ? 'POCKET WEB' : (document.title || 'Страница'),
      state.home ? 'Избранные сервисы' : (location.hostname || 'Главная'));
    const actions = [
      { kind: 'Меню', label: '⌕  Адрес или поиск', action: () => openKeyboard(null) },
      { kind: 'Меню', label: '↶  Назад', action: () => 'back' },
      { kind: 'Меню', label: '▣  Оригинальный сайт', action: () => toggle() },
      { kind: 'Меню', label: '⌂  На главную', action: showBookmarks },
      { kind: 'Меню', label: '⟳  Обновить страницу', action: () => location.reload() },
      { kind: 'Меню', label: '◉  Голова: ' + (state.headEnabled ? 'выключить' : 'включить'),
        action: () => 'head' },
      { kind: 'Меню', label: '⌖  Центр головы', action: () => 'center' }
    ];
    const all = actions.concat(state.rows.map(item => ({
      kind: item.kind, label: item.label, action: () => interact(item)
    })));
    if (state.rows.length >= state.maxRows && state.maxRows < 1000) {
      all.push({ kind: 'Меню', label: 'Показать ещё', action: () => {
        state.maxRows += 100; refresh();
      } });
    }
    state.index = Math.max(0, Math.min(state.index, all.length - 1));
    for (let i = 0; i < all.length; i++) {
      const item = all[i];
      list.appendChild(row(item.kind, item.label, i === state.index, () => {
        state.index = i;
        const result = item.action();
        if (result === 'head' || result === 'center' || result === 'back') sendNative(result);
        if (!state.keyboard && !state.raw) render();
      }));
    }
    footer.textContent = `${state.index + 1}/${all.length}  ·  свайп: выбор  ·  тап: открыть`;
    scrollSelection();
  }

  function setValue(input, value) {
    if (input.isContentEditable) {
      input.textContent = value;
    } else if (input.tagName.toLowerCase() === 'select') {
      input.value = value;
    } else {
      const type = input.tagName.toLowerCase() === 'textarea' ? HTMLTextAreaElement : HTMLInputElement;
      const setter = Object.getOwnPropertyDescriptor(type.prototype, 'value').set;
      setter.call(input, value);
    }
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function openKeyboard(target) {
    if (state.raw) exitRaw();
    if (target && target.tagName.toLowerCase() === 'select') {
      const options = [...target.options].map(option => ({
        kind: 'Выбор', label: small(option.text, 120), action: () => {
          setValue(target, option.value); state.keyboard = null; refresh();
        }
      }));
      state.keyboard = { target, choices: options, index: 0, stage: 'options' };
    } else {
      state.keyboard = { target, value: target ? (target.value || target.textContent || '') : '',
        index: 0, stage: 'groups', group: 0 };
    }
    state.raw = false;
    host.style.setProperty('display', 'block', 'important');
    renderKeyboard();
  }

  function renderKeyboard() {
    const k = state.keyboard;
    const { list, footer } = shell(k.target ? labelFor(k.target) : 'Адрес или поиск',
      k.stage === 'options' ? 'Выбери значение' : 'Свайп → выбор · тап → подтвердить');
    if (k.stage !== 'options') {
      const draft = document.createElement('div');
      draft.id = 'draft';
      draft.textContent = k.target && k.target.type === 'password' ? '●'.repeat(k.value.length) :
        (k.value || 'Здесь появится текст');
      screen.insertBefore(draft, list);
    }
    const choices = k.stage === 'options' ? k.choices :
      k.stage === 'letters' ? [...groups[k.group]].map(letter => ({
        kind: 'Буква', label: letter, action: () => { edit(letter); k.stage = 'groups'; k.index = k.group; }
      })) : groups.map((group, groupIndex) => ({
        kind: 'Клавиатура', label: group, action: () => chooseGroup(groupIndex)
      }));
    k.index = Math.max(0, Math.min(k.index, choices.length - 1));
    choices.forEach((item, i) => list.appendChild(row(item.kind, item.label, i === k.index, () => {
      k.index = i; item.action(); if (state.keyboard) renderKeyboard();
    })));
    footer.textContent = `${k.index + 1}/${choices.length}  ·  назад: к странице`;
    scrollSelection();
  }

  function edit(letter) {
    const k = state.keyboard;
    k.value += letter;
    if (k.target && k.target.isConnected) setValue(k.target, k.value);
  }

  function chooseGroup(i) {
    const k = state.keyboard;
    const group = groups[i];
    if (group === 'ПРОБЕЛ') edit(' ');
    else if (group === '⌫') {
      k.value = [...k.value].slice(0, -1).join('');
      if (k.target && k.target.isConnected) setValue(k.target, k.value);
    } else if (group === 'ГОТОВО' || group === 'ОТПРАВИТЬ') {
      if (!k.target) {
        let input = k.value.trim();
        if (!input) return;
        if (!/^https?:\/\//i.test(input)) {
          input = /^(localhost|[\w-]+\.[\w.-]+)(:\d+)?(\/|$)/i.test(input) ?
            'https://' + input : 'https://www.google.com/search?q=' + encodeURIComponent(input);
        }
        try {
          const url = new URL(input);
          if (!['http:', 'https:'].includes(url.protocol)) return;
          state.keyboard = null;
          location.assign(url.href);
        } catch (_) { /* Keep the editor open for correction. */ }
      } else {
        const target = k.target;
        setValue(target, k.value);
        state.keyboard = null;
        if (group === 'ОТПРАВИТЬ' && target.form) {
          if (target.form.requestSubmit) target.form.requestSubmit();
          else target.form.submit();
        } else refresh();
      }
    } else {
      k.group = i;
      k.stage = 'letters';
      k.index = 0;
    }
  }

  function interact(item) {
    if (item.url) { location.assign(item.url); return; }
    const element = item.element;
    if (!element.isConnected) { refresh(); return; }
    if (item.kind === 'Поле') { openKeyboard(element); return; }
    if (item.kind === 'Ссылка' || item.kind === 'Действие') {
      element.scrollIntoView({ block: 'center' });
      element.click();
      setTimeout(refresh, 450);
    } else {
      element.scrollIntoView({ block: 'center' });
    }
  }

  function rawFocusables() {
    return [...document.querySelectorAll('a,button,input,textarea,select,' +
      '[tabindex],[role="button"],[role="link"]')].filter(isVisible);
  }

  function exitRaw() {
    state.raw = false;
    host.style.setProperty('display', 'block', 'important');
    document.documentElement.style.filter = state.originalFilter || '';
    document.getElementById('rokid-pocket-web-focus')?.remove();
  }

  function toggle() {
    if (state.raw) {
      exitRaw();
      refresh();
    } else {
      state.raw = true;
      host.style.setProperty('display', 'none', 'important');
      state.originalFilter = document.documentElement.style.filter;
      document.documentElement.style.filter = 'grayscale(1) brightness(.85) contrast(1.25)';
      const hint = document.createElement('style');
      hint.id = 'rokid-pocket-web-focus';
      hint.textContent = ':focus { outline: 4px solid #60ff91 !important; outline-offset: 2px !important; }';
      document.head.appendChild(hint);
    }
    return 'handled';
  }

  function move(delta) {
    if (state.keyboard) {
      const k = state.keyboard;
      const size = k.stage === 'options' ? k.choices.length :
        k.stage === 'letters' ? groups[k.group].length : groups.length;
      k.index = (k.index + delta + size) % size;
      renderKeyboard();
      return 'handled';
    }
    if (state.raw) {
      const items = rawFocusables();
      if (!items.length) { window.scrollBy(0, delta * Math.round(innerHeight * .6)); return 'handled'; }
      if (state.rawIndex < 0 && delta < 0) state.rawIndex = 0;
      state.rawIndex = (state.rawIndex + delta + items.length) % items.length;
      items[state.rawIndex].focus();
      items[state.rawIndex].scrollIntoView({ block: 'center' });
      return 'handled';
    }
    const count = state.rows.length + 7 +
      (state.rows.length >= state.maxRows && state.maxRows < 1000 ? 1 : 0);
    state.index = (state.index + delta + count) % count;
    render();
    return 'handled';
  }

  function activate() {
    if (state.keyboard) {
      const k = state.keyboard;
      if (k.stage === 'options') k.choices[k.index].action();
      else if (k.stage === 'letters') {
        edit([...groups[k.group]][k.index]);
        k.stage = 'groups'; k.index = k.group;
      } else chooseGroup(k.index);
      if (state.keyboard) renderKeyboard();
      return 'handled';
    }
    if (state.raw) {
      const items = rawFocusables();
      const element = items[state.rawIndex];
      if (element) {
        if (element.matches('input,textarea,select')) openKeyboard(element);
        else element.click();
      }
      return 'handled';
    }
    if (state.index === 0) openKeyboard(null);
    else if (state.index === 1) return 'back';
    else if (state.index === 2) toggle();
    else if (state.index === 3) showBookmarks();
    else if (state.index === 4) location.reload();
    else if (state.index === 5) return 'head';
    else if (state.index === 6) return 'center';
    else if (state.index === state.rows.length + 7) {
      state.maxRows += 100; refresh();
    } else interact(state.rows[state.index - 7]);
    return 'handled';
  }

  function showBookmarks() {
    state.home = true;
    state.index = 7;
    state.rows = bookmarks.map(([label, url]) => ({ kind: 'Ссылка', label, url }));
    render();
  }

  function back() {
    if (state.keyboard) {
      if (state.keyboard.stage === 'letters') {
        state.keyboard.stage = 'groups'; state.keyboard.index = state.keyboard.group;
      } else state.keyboard = null;
      render();
      return 'handled';
    }
    if (state.raw) return toggle();
    return 'back';
  }

  function refresh() {
    if (!host.isConnected) document.documentElement.appendChild(host);
    if (!state.home) state.rows = collect();
    if (!state.keyboard && !state.raw) render();
  }

  window.__rokidHud = { next: () => move(1), previous: () => move(-1),
    activate, back, toggle, refresh,
    setHeadEnabled: enabled => { state.headEnabled = !!enabled; if (!state.raw && !state.keyboard) render(); } };

  const observer = new MutationObserver(records => {
    if (state.keyboard || state.raw) return;
    if (!records.some(record => !host.contains(record.target) && record.target !== host)) return;
    clearTimeout(state.refreshTimer);
    state.refreshTimer = setTimeout(refresh, 350);
  });
  observer.observe(document.documentElement, { childList: true, subtree: true, characterData: true });
  state.observer = observer;
  refresh();
})();
