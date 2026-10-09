export default async function handler(req, res) {
  res.setHeader("Cache-Control", "no-store");

  if (req.method !== "GET" && req.method !== "HEAD") {
    res.setHeader("Allow", "GET, HEAD");
    return res.status(405).json({ error: "Method not allowed" });
  }

  const keyConfigured = Boolean(
    typeof process.env.OPENAI_API_KEY === "string" &&
    process.env.OPENAI_API_KEY.trim().length > 0
  );

  return res.status(keyConfigured ? 200 : 503).json({
    service: "Mayra AI API",
    status: keyConfigured ? "ready" : "configuration_required",
    chat: keyConfigured ? "configured" : "not_configured",
    imageGeneration: keyConfigured ? "configured" : "not_configured"
  });
}
