(() => {
  const passwordInput = document.querySelector("#admin-password");
  const dashboard = document.querySelector("#dashboard");
  const adminStatus = document.querySelector("#admin-status");
  let snapshot = null;

  passwordInput.value = sessionStorage.getItem("slotbook_admin_password") || "";

  async function adminCall(action, payload = {}) {
    const password = passwordInput.value;
    if (!password) throw new Error("Enter the admin password.");
    sessionStorage.setItem("slotbook_admin_password", password);
    return SlotbookPortal.api("/api/admin", {
      method: "POST",
      headers: { "X-Admin-Password": password },
      body: JSON.stringify({ action, payload })
    });
  }

  function renderRows(target, rows, keys) {
    target.innerHTML = rows.length ? rows.slice(0, 100).map(row => `<tr>${keys.map(key => `<td>${SlotbookPortal.escapeHtml(row[key])}</td>`).join("")}</tr>`).join("") : `<tr><td colspan="${keys.length}" class="empty">No submissions yet.</td></tr>`;
  }

  function renderSnapshot(data) {
    snapshot = data;
    document.querySelector("#student-count").textContent = data.student_count;
    document.querySelector("#student-rows").textContent = data.student_rows;
    document.querySelector("#instructor-count").textContent = data.instructor_count;
    document.querySelector("#instructor-rows").textContent = data.instructor_rows;
    renderRows(document.querySelector("#student-preview"), data.students || [], ["student_id", "course_id", "rank"]);
    renderRows(document.querySelector("#instructor-preview"), data.instructors || [], ["instructor", "slot", "penalty", "reason"]);
    dashboard.hidden = false;
  }

  async function loadDashboard() {
    SlotbookPortal.showStatus(adminStatus, "Loading Google Sheets snapshot…", "info");
    try {
      const data = await adminCall("snapshot");
      renderSnapshot(data);
      SlotbookPortal.showStatus(adminStatus, `Dashboard refreshed at ${new Date(data.generated_at).toLocaleString()}.`, "success");
    } catch (error) {
      dashboard.hidden = true;
      SlotbookPortal.showStatus(adminStatus, error.message, "error");
    }
  }

  document.querySelector("#load-dashboard").addEventListener("click", loadDashboard);
  document.querySelector("#refresh-dashboard").addEventListener("click", loadDashboard);
  passwordInput.addEventListener("keydown", event => { if (event.key === "Enter") loadDashboard(); });

  document.querySelector("#download-students").addEventListener("click", () => {
    if (!snapshot) return;
    SlotbookPortal.downloadText("student_preferences.csv", snapshot.student_preferences_csv);
  });
  document.querySelector("#download-instructors").addEventListener("click", () => {
    if (!snapshot) return;
    SlotbookPortal.downloadText("instructor_preferences.csv", snapshot.instructor_preferences_csv);
  });

  document.querySelector("#run-solver").addEventListener("click", async event => {
    const button = event.currentTarget;
    const status = document.querySelector("#solver-status");
    button.disabled = true;
    SlotbookPortal.showStatus(status, "Sending current preferences to the solver backend…", "info");
    try {
      const result = await SlotbookPortal.api("/api/solver", {
        method: "POST",
        headers: { "X-Admin-Password": passwordInput.value },
        body: JSON.stringify({ solver: document.querySelector("#solver-choice").value })
      });
      SlotbookPortal.showStatus(status, `Published ${result.slotbook.length} course assignments and ${result.explanations.length} explanations.`, "success");
    } catch (error) {
      SlotbookPortal.showStatus(status, error.message, error.code === "SOLVER_NOT_CONFIGURED" ? "info" : "error");
    } finally {
      button.disabled = false;
    }
  });

  document.querySelector("#publish-results").addEventListener("click", async event => {
    const button = event.currentTarget;
    const status = document.querySelector("#publish-status");
    button.disabled = true;
    SlotbookPortal.showStatus(status, "Reading and validating solver exports…", "info");
    try {
      const slotbookText = await SlotbookPortal.readFile(document.querySelector("#slotbook-file").files[0]);
      const explanationText = await SlotbookPortal.readFile(document.querySelector("#explanations-file").files[0]);
      const slotbook = SlotbookPortal.parseCsv(slotbookText);
      const explanations = SlotbookPortal.parseCsv(explanationText);
      const assignmentHeaders = ["course_id", "title", "semester_group", "course_type", "instructor", "assigned_slot", "additional_slot", "room", "class_size"];
      const explanationHeaders = ["course_id", "assigned_slot", "reasons", "rejected_or_feasible_alternatives"];
      if (!slotbook.length || !assignmentHeaders.every(key => Object.hasOwn(slotbook[0], key))) throw new Error("generated_slotbook.csv has missing or incorrect headers.");
      if (!explanations.length || !explanationHeaders.every(key => Object.hasOwn(explanations[0], key))) throw new Error("assignment_explanations.csv has missing or incorrect headers.");
      const result = await adminCall("publish_results", { slotbook, explanations, solver: "manual upload" });
      SlotbookPortal.showStatus(status, `${result.message} ${result.slotbook_rows} assignments and ${result.explanation_rows} explanations are public.`, "success");
    } catch (error) {
      SlotbookPortal.showStatus(status, error.message, "error");
    } finally {
      button.disabled = false;
    }
  });

  document.querySelector("#clear-data").addEventListener("click", async event => {
    const status = document.querySelector("#clear-status");
    if (!confirm("Clear all student, instructor, slotbook, and explanation rows? This cannot be undone.")) return;
    event.currentTarget.disabled = true;
    try {
      const result = await adminCall("clear_demo_data");
      SlotbookPortal.showStatus(status, result.message, "success");
      await loadDashboard();
    } catch (error) {
      SlotbookPortal.showStatus(status, error.message, "error");
    } finally {
      event.currentTarget.disabled = false;
    }
  });
})();
