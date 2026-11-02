(() => {
  const form = document.querySelector("#professor-form");
  const grid = document.querySelector("#preference-grid");
  const submitButton = document.querySelector("#professor-submit");
  const status = document.querySelector("#professor-status");
  const courseSelect = document.querySelector("#course");
  let slots = [];

  function render() {
    grid.innerHTML = slots.map(slot => `<div class="preference-row" data-slot="${SlotbookPortal.escapeHtml(slot.slot_id)}">
      <div class="slot-label"><strong>Slot ${SlotbookPortal.escapeHtml(slot.slot_id)}</strong><span>${SlotbookPortal.escapeHtml(slot.summary)}</span></div>
      <label class="sr-only" for="level-${SlotbookPortal.escapeHtml(slot.slot_id)}">Preference for slot ${SlotbookPortal.escapeHtml(slot.slot_id)}</label>
      <select id="level-${SlotbookPortal.escapeHtml(slot.slot_id)}" class="level">
        <option value="">Not specified</option>
        <option value="0">Preferred · penalty 0</option>
        <option value="2">Acceptable · penalty 2</option>
        <option value="8">Discouraged · penalty 8</option>
      </select>
      <input class="reason" maxlength="240" placeholder="Reason, e.g. standing research meeting" disabled aria-label="Reason for slot ${SlotbookPortal.escapeHtml(slot.slot_id)}">
    </div>`).join("");
  }

  grid.addEventListener("change", event => {
    if (!event.target.classList.contains("level")) return;
    const row = event.target.closest(".preference-row");
    const reason = row.querySelector(".reason");
    reason.disabled = event.target.value === "";
    if (reason.disabled) reason.value = "";
  });

  form.addEventListener("submit", async event => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    const preferences = [...grid.querySelectorAll(".preference-row")].flatMap(row => {
      const value = row.querySelector(".level").value;
      if (value === "") return [];
      return [{ slot: row.dataset.slot, penalty: Number(value), reason: row.querySelector(".reason").value.trim() }];
    });
    if (!preferences.length) return SlotbookPortal.showStatus(status, "Specify at least one slot preference.", "error");
    if (!courseSelect.value && document.querySelector("#prerequisites").value.trim()) {
      return SlotbookPortal.showStatus(status, "Choose the course these prerequisites belong to.", "error");
    }
    const discouragedWithoutReason = preferences.find(item => item.penalty >= 8 && !item.reason);
    if (discouragedWithoutReason) return SlotbookPortal.showStatus(status, `Add a reason for discouraged slot ${discouragedWithoutReason.slot}.`, "error");
    submitButton.disabled = true;
    SlotbookPortal.showStatus(status, "Saving instructor preferences…", "info");
    try {
      const result = await SlotbookPortal.api("/api/submit", {
        method: "POST",
        body: JSON.stringify({
          type: "instructor",
          payload: {
            instructor: document.querySelector("#instructor").value,
            course_id: courseSelect.value,
            // Sent only with a course: the server treats a missing field as "unchanged".
            prerequisites: courseSelect.value ? document.querySelector("#prerequisites").value : undefined,
            preferences
          }
        })
      });
      const prerequisiteNote = result.prerequisites_saved === null || result.prerequisites_saved === undefined
        ? "" : ` ${result.prerequisites_saved} prerequisite(s) recorded for ${courseSelect.value}.`;
      SlotbookPortal.showStatus(status, `${result.message} ${result.rows_saved} slot penalties are ready for export.${prerequisiteNote}`, "success");
    } catch (error) {
      SlotbookPortal.showStatus(status, error.message, "error");
    } finally {
      submitButton.disabled = false;
    }
  });

  document.querySelector("#professor-reset").addEventListener("click", () => {
    form.reset();
    [...grid.querySelectorAll(".reason")].forEach(input => input.disabled = true);
    SlotbookPortal.clearStatus(status);
  });

  SlotbookPortal.loadCatalog()
    .then(catalog => {
      slots = catalog.slots.filter(slot => slot.category === "LECTURE");
      courseSelect.innerHTML = '<option value="">No specific course</option>' + catalog.courses
        .map(course => `<option value="${SlotbookPortal.escapeHtml(course.course_id)}">${SlotbookPortal.escapeHtml(course.course_id)} · ${SlotbookPortal.escapeHtml(course.title)}</option>`)
        .join("");
      render();
    })
    .catch(error => SlotbookPortal.showStatus(status, error.message, "error"));
})();
