(() => {
  const form = document.querySelector("#student-form");
  const picker = document.querySelector("#course-picker");
  const list = document.querySelector("#ranked-list");
  const addButton = document.querySelector("#add-course");
  const submitButton = document.querySelector("#student-submit");
  const status = document.querySelector("#student-status");
  let courses = [];
  let selected = [];

  function renderPicker() {
    const available = courses.filter(course => !selected.includes(course.course_id));
    picker.innerHTML = `<option value="">${available.length ? "Choose a course" : "All available courses selected"}</option>` + available
      .map(course => `<option value="${SlotbookPortal.escapeHtml(course.course_id)}">${SlotbookPortal.escapeHtml(course.course_id)} · ${SlotbookPortal.escapeHtml(course.title)}</option>`)
      .join("");
    picker.disabled = selected.length >= 10 || !available.length;
    addButton.disabled = picker.disabled;
  }

  function renderList() {
    if (!selected.length) {
      list.innerHTML = '<li class="empty">No courses added yet.</li>';
    } else {
      list.innerHTML = selected.map((courseId, index) => {
        const course = courses.find(item => item.course_id === courseId);
        return `<li class="ranked-item">
          <span class="rank-badge">${index + 1}</span>
          <span><strong>${SlotbookPortal.escapeHtml(courseId)}</strong><br><span class="hint">${SlotbookPortal.escapeHtml(course?.title || "")}</span></span>
          <span class="rank-actions">
            <button class="icon-button" type="button" data-action="up" data-index="${index}" aria-label="Move ${SlotbookPortal.escapeHtml(courseId)} up" ${index === 0 ? "disabled" : ""}>↑</button>
            <button class="icon-button" type="button" data-action="down" data-index="${index}" aria-label="Move ${SlotbookPortal.escapeHtml(courseId)} down" ${index === selected.length - 1 ? "disabled" : ""}>↓</button>
            <button class="icon-button" type="button" data-action="remove" data-index="${index}" aria-label="Remove ${SlotbookPortal.escapeHtml(courseId)}">×</button>
          </span>
        </li>`;
      }).join("");
    }
    renderPicker();
  }

  addButton.addEventListener("click", () => {
    if (!picker.value || selected.length >= 10) return;
    selected.push(picker.value);
    SlotbookPortal.clearStatus(status);
    renderList();
  });

  picker.addEventListener("change", () => {
    if (picker.value) SlotbookPortal.clearStatus(status);
  });

  list.addEventListener("click", event => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const index = Number(button.dataset.index);
    const action = button.dataset.action;
    if (action === "remove") selected.splice(index, 1);
    else if (action === "up" && index > 0) [selected[index - 1], selected[index]] = [selected[index], selected[index - 1]];
    else if (action === "down" && index < selected.length - 1) [selected[index + 1], selected[index]] = [selected[index], selected[index + 1]];
    renderList();
  });

  form.addEventListener("submit", async event => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    if (!selected.length) return SlotbookPortal.showStatus(status, "Add at least one course preference.", "error");
    submitButton.disabled = true;
    SlotbookPortal.showStatus(status, "Saving your ranked preferences…", "info");
    try {
      const result = await SlotbookPortal.api("/api/submit", {
        method: "POST",
        body: JSON.stringify({
          type: "student",
          payload: {
            student_id: document.querySelector("#student-id").value,
            programme: document.querySelector("#programme").value,
            year: document.querySelector("#year").value,
            preferences: selected.map(course_id => ({ course_id }))
          }
        })
      });
      SlotbookPortal.showStatus(status, `${result.message} ${result.rows_saved} ranked course rows are now ready for the model.`, "success");
    } catch (error) {
      SlotbookPortal.showStatus(status, error.message, "error");
    } finally {
      submitButton.disabled = false;
    }
  });

  document.querySelector("#student-reset").addEventListener("click", () => {
    form.reset();
    selected = [];
    renderList();
    SlotbookPortal.clearStatus(status);
  });

  SlotbookPortal.loadCatalog()
    .then(catalog => {
      courses = catalog.courses;
      renderList();
    })
    .catch(error => {
      picker.innerHTML = '<option value="">Catalog unavailable</option>';
      addButton.disabled = true;
      SlotbookPortal.showStatus(status, error.message, "error");
    });
})();
