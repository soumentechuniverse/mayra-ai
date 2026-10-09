export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") {
    return res.status(204).end();
  }

  if (req.method !== "POST") {
    return res.status(405).json({
      error: "Method not allowed"
    });
  }

  const apiKey = process.env.OPENAI_API_KEY;

  if (!apiKey) {
    return res.status(500).json({
      error: "Server OpenAI API key is not configured."
    });
  }

  try {
    const incoming = req.body && typeof req.body === "object" && !Array.isArray(req.body)
      ? req.body
      : {};

    const prompt = typeof incoming.prompt === "string" ? incoming.prompt.trim() : "";
    if (!prompt || prompt.length > 4000) {
      return res.status(400).json({ error: "Image prompt must contain 1–4000 characters." });
    }

    const allowedSizes = new Set(["1024x1024", "1536x1024", "1024x1536"]);
    const size = allowedSizes.has(incoming.size) ? incoming.size : "1024x1024";

    // Keep image generation on the model and output format supported by the app.
    const payload = {
      model: "gpt-image-1",
      prompt,
      size,
      quality: "auto",
      output_format: "png"
    };

    const upstream = await fetch(
      "https://api.openai.com/v1/images/generations",
      {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${apiKey}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify(payload)
      }
    );

    const body = await upstream.text();

    res.status(upstream.status);
    res.setHeader(
      "Content-Type",
      upstream.headers.get("content-type") ||
        "application/json"
    );

    res.setHeader("Cache-Control", "no-store");

    return res.send(body);

  } catch (error) {
    return res.status(500).json({
      error:
        error instanceof Error
          ? error.message
          : "Unknown image generation error"
    });
  }
}
