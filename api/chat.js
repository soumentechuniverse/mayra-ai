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
    const upstream = await fetch(
      "https://api.openai.com/v1/responses",
      {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${apiKey}`,
          "Content-Type": "application/json",
          "Accept": req.body?.stream
            ? "text/event-stream"
            : "application/json"
        },
        body: JSON.stringify(req.body ?? {})
      }
    );

    const contentType =
      upstream.headers.get("content-type") ||
      "application/json";

    res.status(upstream.status);
    res.setHeader("Content-Type", contentType);
    res.setHeader("Cache-Control", "no-store");

    const text = await upstream.text();

    return res.send(text);

  } catch (error) {
    return res.status(500).json({
      error: error instanceof Error
        ? error.message
        : "Unknown server error"
    });
  }
}
