(() => {
  const status = document.querySelector("#results-status");
  const content = document.querySelector("#results-content");
  const table = document.querySelector("#results-table");
  const card = document.querySelector("#explanation-card");
  let slotbook = [];
  let explanationByCourse = new Map();

  function render(rows) {
    table.innerHTML = rows.length ? rows.map(row => `<tr>
      <td><strong>${SlotbookPortal.escapeHtml(row.course_id)}</strong></td>
      <td>${SlotbookPortal.escapeHtml(row.title)}</td>
      <td>${SlotbookPortal.escapeHtml(row.instructor)}</td>
      <td><span class="pill">${SlotbookPortal.escapeHtml(row.assigned_slot)}</span></td>
      <td>${SlotbookPortal.escapeHtml(row.room)}</td>
      <td><button class="button small secondary" type="button" data-course="${SlotbookPortal.escapeHtml(row.course_id)}">Inspect</button></td>
    </tr>`).join("") : '<tr><td colspan="6" class="empty">No courses match this search.</td></tr>';
  }

  table.addEventListener("click", event => {
    const button = event.target.closest("button[data-course]");
    if (!button) return;
    const courseId = button.dataset.course;
    const row = slotbook.find(item => item.course_id === courseId);
    const explanation = explanationByCourse.get(courseId);
    card.hidden = false;
    card.innerHTML = `<h3>${SlotbookPortal.escapeHtml(courseId)} · slot ${SlotbookPortal.escapeHtml(row?.assigned_slot)}</h3>
      <p><strong>Reasons:</strong> ${SlotbookPortal.escapeHtml(explanation?.reasons || "No explanation row was published.")}</p>
      <p><strong>Alternatives:</strong> ${SlotbookPortal.escapeHtml(explanation?.rejected_or_feasible_alternatives || "Not available.")}</p>`;
    card.scrollIntoView({ behavior: "smooth", block: "nearest" });
  });

  document.querySelector("#course-search").addEventListener("input", event => {
    const query = event.target.value.trim().toLowerCase();
    render(slotbook.filter(row => !query || [row.course_id, row.title, row.instructor, row.assigned_slot, row.room].some(value => String(value || "").toLowerCase().includes(query))));
    card.hidden = true;
  });

  SlotbookPortal.api("/api/results")
    .then(result => {
      slotbook = result.slotbook || [];
      explanationByCourse = new Map((result.explanations || []).map(row => [row.course_id, row]));
      if (!slotbook.length) {
        SlotbookPortal.showStatus(status, "No slotbook has been published yet. An administrator can generate or upload one from the dashboard.", "info");
        return;
      }
      document.querySelector("#result-solver").textContent = `Solver: ${result.solver || "unknown"}`;
      document.querySelector("#result-date").textContent = `Published: ${new Date(result.published_at).toLocaleString()}`;
      document.querySelector("#result-count").textContent = `${slotbook.length} courses`;
      status.className = "status";
      content.hidden = false;
      render(slotbook);
    })
    .catch(error => SlotbookPortal.showStatus(status, error.message, "error"));
})();
