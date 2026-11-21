/* Pen Holder color selector helper — reusable by the embedded editor.
   Each color choice shows a swatch and its palette label.
   Selection updates the original select so existing preview/3MF handlers
   continue to receive the exact selected hex value. */
(function () {
  const style = document.createElement("style");
  style.textContent = `
    .ph-color-trigger{display:flex;align-items:center;gap:8px;width:100%;padding:8px 10px;border:1px solid var(--bd);border-radius:10px;background:var(--bg);color:var(--tx);font:inherit;text-align:left;cursor:pointer}
    .ph-color-swatch{width:19px;height:19px;min-width:19px;border:1px solid #888;border-radius:5px;box-shadow:inset 0 0 0 1px #ffffff55}
    .ph-color-label{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
    .ph-color-caret{opacity:.7}
    .ph-color-menu{position:fixed;z-index:100001;max-height:min(280px,70vh);overflow:auto;padding:5px;background:var(--card);border:1px solid var(--bd);border-radius:12px;box-shadow:0 12px 30px #0003}
    .ph-color-option{display:flex;align-items:center;gap:9px;width:100%;padding:8px 10px;border:0;border-radius:8px;background:transparent;color:var(--tx);font:inherit;text-align:left;cursor:pointer}
    .ph-color-option[aria-selected="true"]{font-weight:800;background:var(--vw)}
  `;
  document.head.appendChild(style);

  function decorateColorSelect(select, palette) {
    if (!select || select.dataset.phColorDecorated === "1") return;
    select.dataset.phColorDecorated = "1";
    select.hidden = true;
    const trigger = document.createElement("button");
    trigger.type = "button";
    trigger.className = "ph-color-trigger";
    trigger.setAttribute("aria-haspopup", "listbox");
    const swatch = document.createElement("span");
    swatch.className = "ph-color-swatch";
    const label = document.createElement("span");
    label.className = "ph-color-label";
    const caret = document.createElement("span");
    caret.className = "ph-color-caret";
    caret.textContent = "⌄";
    trigger.append(swatch, label, caret);
    select.parentNode.insertBefore(trigger, select);

    const update = () => {
      const item = palette.find(([name, hex]) => hex.toLowerCase() === select.value.toLowerCase());
      swatch.style.backgroundColor = select.value;
      label.textContent = item ? item[0] : "Warna tidak dikenal";
      trigger.setAttribute("aria-label", "Pilih warna: " + label.textContent);
    };
    const open = () => {
      document.querySelectorAll(".ph-color-menu").forEach(menu => menu.remove());
      const menu = document.createElement("div");
      menu.className = "ph-color-menu";
      menu.setAttribute("role", "listbox");
      palette.forEach(([name, hex]) => {
        const option = document.createElement("button");
        option.type = "button";
        option.className = "ph-color-option";
        option.setAttribute("role", "option");
        option.setAttribute("aria-selected", String(hex.toLowerCase() === select.value.toLowerCase()));
        const sample = document.createElement("span");
        sample.className = "ph-color-swatch";
        sample.style.backgroundColor = hex;
        const text = document.createElement("span");
        text.textContent = name;
        option.append(sample, text);
        option.addEventListener("click", () => {
          select.value = hex;
          select.dispatchEvent(new Event("input", { bubbles: true }));
          select.dispatchEvent(new Event("change", { bubbles: true }));
          update();
          menu.remove();
        });
        menu.appendChild(option);
      });
      document.body.appendChild(menu);
      const rect = trigger.getBoundingClientRect();
      const width = Math.min(Math.max(rect.width, 180), innerWidth - 16);
      menu.style.width = width + "px";
      menu.style.left = Math.max(8, Math.min(rect.left, innerWidth - width - 8)) + "px";
      menu.style.top = Math.max(8, Math.min(rect.bottom + 4, innerHeight - menu.offsetHeight - 8)) + "px";
      const dismiss = event => {
        if (!menu.contains(event.target) && !trigger.contains(event.target)) {
          menu.remove();
          document.removeEventListener("pointerdown", dismiss, true);
        }
      };
      setTimeout(() => document.addEventListener("pointerdown", dismiss, true), 0);
    };
    trigger.addEventListener("click", open);
    select.addEventListener("change", update);
    update();
    return { trigger, update };
  }

  window.DSPenHolderColorSelector = { decorateColorSelect };
})();