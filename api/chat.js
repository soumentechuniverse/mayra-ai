export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") {
    return res.status(204).end();
  }

  if (req.method !== "POST") {
    return res.status(405).json({ error: "Method not allowed" });
  }

  const apiKey = process.env.OPENAI_API_KEY;

  if (!apiKey) {
    return res.status(500).json({
      error: "Server OpenAI API key is not configured."
    });
  }

  try {
    const payload = req.body && typeof req.body === "object" && !Array.isArray(req.body)
      ? { ...req.body }
      : {};

    if (!Array.isArray(payload.input) || payload.input.length === 0 || payload.input.length > 12) {
      return res.status(400).json({ error: "Invalid or oversized chat input." });
    }

    // The public mobile client must not be allowed to select arbitrary expensive models.
    const allowedModels = new Set(["gpt-4.1-mini", "gpt-4.1"]);
    if (!allowedModels.has(payload.model)) {
      payload.model = "gpt-4.1-mini";
    }

    const requestedTokens = Number(payload.max_output_tokens ?? 1024);
    payload.max_output_tokens = Number.isFinite(requestedTokens)
      ? Math.min(4096, Math.max(256, Math.floor(requestedTokens)))
      : 1024;

    if (typeof payload.instructions === "string") {
      payload.instructions = payload.instructions.slice(0, 16000);
    }

    const wantsStream = Boolean(payload.stream);

    const upstream = await fetch(
      "https://api.openai.com/v1/responses",
      {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${apiKey}`,
          "Content-Type": "application/json",
          "Accept": wantsStream
            ? "text/event-stream"
            : "application/json"
        },
        body: JSON.stringify(payload)
      }
    );

    const contentType =
      upstream.headers.get("content-type") ||
      (wantsStream ? "text/event-stream" : "application/json");

    res.status(upstream.status);
    res.setHeader("Content-Type", contentType);
    res.setHeader(
      "Cache-Control",
      wantsStream ? "no-cache, no-transform" : "no-store"
    );
    res.setHeader("X-Accel-Buffering", "no");

    if (!wantsStream || !upstream.body) {
      const text = await upstream.text();
      return res.send(text);
    }

    const reader = upstream.body.getReader();

    try {
      while (true) {
        const { done, value } = await reader.read();

        if (done) {
          break;
        }

        if (value) {
          res.write(Buffer.from(value));
        }
      }
    } finally {
      reader.releaseLock();
    }

    return res.end();

  } catch (error) {
    if (!res.headersSent) {
      return res.status(500).json({
        error: error instanceof Error
          ? error.message
          : "Unknown server error"
      });
    }

    return res.end();
  }
}
