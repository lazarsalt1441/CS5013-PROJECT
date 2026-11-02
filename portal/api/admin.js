const { errorResponse, json, readBody, requireAdmin, requireMethod } = require("./_lib");
const { buildExports } = require("./_exports");
const store = require("./_store");
const { validatePublication } = require("./_validation");

const ACTIONS = {
  async snapshot() {
    const snapshot = await store.snapshot();
    return { ok: true, ...snapshot, ...buildExports(snapshot) };
  },
  async publish_results(payload) {
    const published = await store.publishResults(validatePublication(payload));
    return { ok: true, message: "Published slotbook results.", ...published };
  },
  async clear_demo_data() {
    return { ok: true, ...(await store.clearAll()) };
  }
};

module.exports = async function handler(req, res) {
  if (!requireMethod(req, res, ["POST"])) return;
  if (!requireAdmin(req, res)) return;
  try {
    const body = await readBody(req);
    const action = body.action || "snapshot";
    if (!Object.hasOwn(ACTIONS, action)) {
      return json(res, 400, { ok: false, error: "Unsupported admin action." });
    }
    return json(res, 200, await ACTIONS[action](body.payload || {}));
  } catch (error) {
    return errorResponse(res, error);
  }
};
