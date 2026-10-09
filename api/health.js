export default async function handler(req, res) {
  res.setHeader("Cache-Control", "no-store");

  if (req.method !== "GET" && req.method !== "HEAD") {
    res.setHeader("Allow", "GET, HEAD");
    return res.status(405).json({ error: "Method not allowed" });
  }

  const geminiConfigured = Boolean(
    typeof process.env.GEMINI_API_KEY === "string" &&
    process.env.GEMINI_API_KEY.trim().length > 0
  );

  const openAIConfigured = Boolean(
    typeof process.env.OPENAI_API_KEY === "string" &&
    process.env.OPENAI_API_KEY.trim().length > 0
  );

  return res.status(geminiConfigured ? 200 : 503).json({
    service: "Mayra AI API",
    status: geminiConfigured ? "ready" : "configuration_required",
    chat: geminiConfigured ? "free_tier_configured" : "GEMINI_API_KEY_required",
    imageGeneration: openAIConfigured ? "OpenAI key configured; API billing may still be required" : "paid_API_key_required"
  });
}
