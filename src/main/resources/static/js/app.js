// Small enhancements. Every form works without this script; the server always validates.

// Theme toggle: switches light / dark and remembers the choice on this computer.
(function () {
  var button = document.getElementById('theme-toggle');
  if (!button) return;
  button.addEventListener('click', function () {
    var root = document.documentElement;
    var current = root.dataset.theme ||
      (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
    var next = current === 'dark' ? 'light' : 'dark';
    root.dataset.theme = next;
    try { localStorage.setItem('theme', next); } catch (e) {}
  });
})();

// Ask before deleting.
document.addEventListener('submit', function (e) {
  var message = e.target.getAttribute('data-confirm');
  if (message && !window.confirm(message)) e.preventDefault();
});

// ---- Form inputs ------------------------------------------------------------

(function () {
  var forms = document.querySelectorAll('form[data-guard]');
  if (!forms.length) return;

  var AMOUNT = /^\s*(?:₱|php|p)?\s*(\d{1,3}(?:,\d{3})*|\d+)(?:\.(\d{1,2}))?\s*$/i;
  var WHOLE = /^\s*\d+\s*$/;

  function fieldOf(input) { return input.closest('.field'); }
  function errorOf(input) { return document.getElementById(input.id + '-error'); }

  // Returns a message, or '' when the value is acceptable. Mirrors the server's rules.
  function problem(input) {
    var value = input.value.trim();
    var label = (fieldOf(input).querySelector('.label span') || {}).textContent || 'this field';
    if (!value) {
      if (!input.required) return '';
      if (input.dataset.kind === 'date') return 'Enter the date issued';
      if (input.dataset.kind === 'amount') return 'Enter the amount paid';
      return 'Enter the ' + label.toLowerCase().replace(/\.$/, '');
    }
    if (input.dataset.kind === 'int' && !WHOLE.test(value)) return 'Enter a whole number';
    if (input.dataset.kind === 'amount' && !AMOUNT.test(value)) return 'Enter an amount, for example 250.50';
    return '';
  }

  function show(input, message) {
    var field = fieldOf(input), error = errorOf(input);
    if (!field || !error) return;
    field.classList.toggle('has-error', !!message);
    if (message) input.setAttribute('aria-invalid', 'true'); else input.removeAttribute('aria-invalid');
    error.textContent = message;
  }

  // "1250.5" -> "1,250.50" when leaving an amount field.
  function formatAmount(input) {
    var m = AMOUNT.exec(input.value);
    if (!m) return;
    var whole = m[1].replace(/,/g, '').replace(/^0+(?=\d)/, '');
    var cents = ((m[2] || '') + '00').slice(0, 2);
    input.value = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + '.' + cents;
  }

  forms.forEach(function (form) {
    var inputs = form.querySelectorAll('.field input[type=text], .field input[type=date]');
    var initial = new FormData(form);
    var dirty = false, submitting = false;
    var note = form.querySelector('.dirty-note');

    inputs.forEach(function (input) {
      if (input.dataset.kind === 'amount' && input.value) formatAmount(input);
      input.addEventListener('blur', function () {
        if (input.dataset.kind === 'amount') formatAmount(input);
        // Only complain once the person has typed something or left a required field empty on purpose.
        if (input.value || input.dataset.touched) show(input, problem(input));
      });
      input.addEventListener('input', function () {
        input.dataset.touched = '1';
        // Clear an error as soon as it's fixed, but don't nag while typing.
        if (fieldOf(input).classList.contains('has-error') && !problem(input)) show(input, '');
      });
    });

    // "Next available: 2026031" fills the control number.
    form.addEventListener('click', function (e) {
      var fill = e.target.closest('[data-fill]');
      if (!fill) return;
      var input = document.getElementById(fill.dataset.fill);
      input.value = fill.dataset.value;
      input.dispatchEvent(new Event('input', { bubbles: true }));
      show(input, '');
      input.focus();
    });

    form.addEventListener('submit', function (e) {
      var first = null;
      inputs.forEach(function (input) {
        var message = problem(input);
        show(input, message);
        if (message && !first) first = input;
      });
      if (first) {
        e.preventDefault();
        first.focus();
        return;
      }
      inputs.forEach(function (input) { if (input.dataset.kind === 'amount') input.value = input.value.replace(/,/g, ''); });
      submitting = true;
    });

    function changed() {
      var now = new FormData(form);
      var keys = new Set([].concat(Array.from(initial.keys()), Array.from(now.keys())));
      for (var k of keys) {
        if (String(initial.getAll(k)) !== String(now.getAll(k))) return true;
      }
      return false;
    }
    form.addEventListener('input', function () {
      dirty = changed();
      if (note) note.textContent = dirty ? 'Unsaved changes' : '';
    });
    form.addEventListener('change', function () { form.dispatchEvent(new Event('input')); });

    // Warn before leaving with unsaved changes.
    window.addEventListener('beforeunload', function (e) {
      if (dirty && !submitting) { e.preventDefault(); e.returnValue = ''; }
    });

    // Ctrl+S / Cmd+S saves.
    document.addEventListener('keydown', function (e) {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
        e.preventDefault();
        form.requestSubmit(form.querySelector('[type=submit]'));
      }
    });
  });

  // After a failed save, move focus to the summary; its links jump to each field.
  var summary = document.getElementById('errors');
  if (summary) {
    summary.focus();
    summary.addEventListener('click', function (e) {
      var link = e.target.closest('a[href^="#"]');
      if (!link) return;
      var input = document.getElementById(link.getAttribute('href').slice(1));
      if (input) { e.preventDefault(); input.focus(); input.scrollIntoView({ block: 'center' }); }
    });
  }
})();

// ---- Pre-printed form layout: drag values on the page, kept in step with the table ----

(function () {
  var sheet = document.querySelector('[data-sheet]');
  if (!sheet) return;
  var pageW = parseFloat(getComputedStyle(sheet).getPropertyValue('--page-w'));
  var pageH = parseFloat(getComputedStyle(sheet).getPropertyValue('--page-h'));

  function input(field, part) { return document.getElementById(field + '-' + part); }
  function box(field) { return document.getElementById('box-' + field); }
  function row(field) { return document.querySelector('tr[data-row="' + field + '"]'); }
  function round(v) { return Math.round(v * 10) / 10; }
  function num(el, fallback) { var v = parseFloat(String(el.value).replace(',', '.')); return isNaN(v) ? fallback : v; }

  // Table -> page.
  function sync(field) {
    var b = box(field);
    b.style.setProperty('--x', num(input(field, 'x'), 0));
    b.style.setProperty('--y', num(input(field, 'y'), 0));
    b.style.setProperty('--w', Math.max(1, num(input(field, 'width'), 1)));
    b.style.setProperty('--size', Math.min(36, Math.max(5, num(input(field, 'size'), 10))));
    var on = input(field, 'on').checked;
    b.classList.toggle('off', !on);
    row(field).classList.toggle('is-off', !on);
  }

  function select(field) {
    document.querySelectorAll('.sheet-field.active, tr[data-row].active').forEach(function (el) { el.classList.remove('active'); });
    if (!field) return;
    box(field).classList.add('active');
    row(field).classList.add('active');
  }

  // Page -> table.
  function place(field, x, y) {
    input(field, 'x').value = round(Math.max(0, Math.min(pageW - 1, x)));
    input(field, 'y').value = round(Math.max(0, Math.min(pageH - 1, y)));
    sync(field);
    input(field, 'x').dispatchEvent(new Event('input', { bubbles: true }));
  }

  document.querySelectorAll('tr[data-row]').forEach(function (tr) {
    var field = tr.dataset.row;
    sync(field);
    tr.addEventListener('input', function () { sync(field); });
    tr.addEventListener('change', function () { sync(field); });
    tr.addEventListener('focusin', function () { select(field); });
  });

  sheet.addEventListener('pointerdown', function (e) {
    var b = e.target.closest('.sheet-field');
    if (!b) return;
    e.preventDefault();
    var field = b.dataset.field;
    select(field);
    b.focus({ preventScroll: true });
    var rect = sheet.getBoundingClientRect();
    var mmPerPx = pageW / rect.width;
    var startX = e.clientX, startY = e.clientY;
    var fromX = num(input(field, 'x'), 0), fromY = num(input(field, 'y'), 0);
    b.setPointerCapture(e.pointerId);
    b.classList.add('dragging');
    function move(ev) { place(field, fromX + (ev.clientX - startX) * mmPerPx, fromY + (ev.clientY - startY) * mmPerPx); }
    function up() {
      b.classList.remove('dragging');
      b.removeEventListener('pointermove', move);
      b.removeEventListener('pointerup', up);
      b.removeEventListener('pointercancel', up);
    }
    b.addEventListener('pointermove', move);
    b.addEventListener('pointerup', up);
    b.addEventListener('pointercancel', up);
  });

  // Arrow keys nudge the selected value by 0.5 mm (Shift: 5 mm).
  sheet.addEventListener('keydown', function (e) {
    var b = e.target.closest('.sheet-field');
    if (!b) return;
    var step = e.shiftKey ? 5 : 0.5;
    var d = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] }[e.key];
    if (!d) return;
    e.preventDefault();
    var field = b.dataset.field;
    place(field, num(input(field, 'x'), 0) + d[0], num(input(field, 'y'), 0) + d[1]);
  });
  sheet.addEventListener('focusin', function (e) {
    var b = e.target.closest('.sheet-field');
    if (b) select(b.dataset.field);
  });
})();
