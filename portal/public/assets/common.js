const SlotbookPortal = (() => {
  async function api(path, options = {}) {
    const response = await fetch(path, {
      ...options,
      headers: { "Content-Type": "application/json", ...(options.headers || {}) }
    });
    const result = await response.json().catch(() => ({ ok: false, error: `Server returned ${response.status}.` }));
    if (!response.ok || result.ok === false) {
      const error = new Error(result.error || `Request failed (${response.status}).`);
      error.code = result.code;
      throw error;
    }
    return result;
  }

  async function loadCatalog() {
    const response = await fetch("/data/catalog.json", { cache: "no-store" });
    if (!response.ok) throw new Error("Course catalog could not be loaded.");
    return response.json();
  }

  function showStatus(element, message, type = "info") {
    element.textContent = message;
    element.className = `status visible ${type}`;
    element.setAttribute("role", type === "error" ? "alert" : "status");
  }

  /** Shows a message followed by a bulleted list (used for warnings). */
  function showStatusList(element, message, items, type = "warning") {
    element.innerHTML = `${escapeHtml(message)}<ul>${items.map(item => `<li>${escapeHtml(item)}</li>`).join("")}</ul>`;
    element.className = `status visible ${type}`;
    element.setAttribute("role", "status");
  }

  /** Reads a PDF as base64; the server checks the content again. */
  function readPdfAsBase64(file, maxBytes = 3_000_000) {
    if (file.size > maxBytes) return Promise.reject(new Error("The grade card must be smaller than 3 MB."));
    if (file.type && file.type !== "application/pdf") return Promise.reject(new Error("The grade card must be a PDF file."));
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result).replace(/^data:[^,]*,/, ""));
      reader.onerror = () => reject(new Error("The grade card could not be read."));
      reader.readAsDataURL(file);
    });
  }

  function clearStatus(element) {
    element.textContent = "";
    element.className = "status";
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function downloadText(filename, text, type = "text/csv;charset=utf-8") {
    const blob = new Blob([text], { type });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = filename;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }

  function parseCsv(text) {
    const rows = [];
    let row = [];
    let field = "";
    let quoted = false;
    for (let index = 0; index < text.length; index += 1) {
      const char = text[index];
      if (quoted) {
        if (char === '"' && text[index + 1] === '"') {
          field += '"';
          index += 1;
        } else if (char === '"') quoted = false;
        else field += char;
      } else if (char === '"') quoted = true;
      else if (char === ",") {
        row.push(field);
        field = "";
      } else if (char === "\n") {
        row.push(field.replace(/\r$/, ""));
        if (row.some(value => value !== "")) rows.push(row);
        row = [];
        field = "";
      } else field += char;
    }
    if (field || row.length) {
      row.push(field.replace(/\r$/, ""));
      rows.push(row);
    }
    if (quoted) throw new Error("CSV contains an unclosed quoted field.");
    if (!rows.length) return [];
    const headers = rows[0].map(header => header.trim());
    return rows.slice(1).map(values => Object.fromEntries(headers.map((header, index) => [header, values[index] ?? ""])));
  }

  async function readFile(file) {
    if (!file) throw new Error("Choose a CSV file first.");
    if (file.size > 2_000_000) throw new Error("CSV file must be smaller than 2 MB.");
    return file.text();
  }

  return { api, clearStatus, downloadText, escapeHtml, loadCatalog, parseCsv, readFile, readPdfAsBase64, showStatus, showStatusList };
})();
