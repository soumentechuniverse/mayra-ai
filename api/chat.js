export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") return res.status(405).json({ error: "Method not allowed" });

  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    return res.status(503).json({
      error: "Free Gemini API is not configured yet. Add GEMINI_API_KEY in Vercel Environment Variables."
    });
  }

  try {
    const payload = req.body && typeof req.body === "object" && !Array.isArray(req.body)
      ? req.body
      : {};

    if (!Array.isArray(payload.input) || payload.input.length === 0 || payload.input.length > 12) {
      return res.status(400).json({ error: "Invalid or oversized chat input." });
    }

    const systemParts = [];
    if (typeof payload.instructions === "string" && payload.instructions.trim()) {
      systemParts.push(payload.instructions.slice(0, 16000));
    }

    const contents = [];
    for (const item of payload.input) {
      if (!item || typeof item !== "object") continue;
      const role = item.role === "assistant" ? "model" : "user";
      const rawContent = item.content;
      const parts = [];

      if (typeof rawContent === "string") {
        if (rawContent.trim()) parts.push({ text: rawContent.slice(0, 12000) });
      } else if (Array.isArray(rawContent)) {
        for (const part of rawContent) {
          if (!part || typeof part !== "object") continue;
          if (typeof part.text === "string" && part.text.trim()) {
            parts.push({ text: part.text.slice(0, 12000) });
          } else if (typeof part.image_url === "string") {
            const match = part.image_url.match(/^data:([^;]+);base64,(.+)$/s);
            if (match) parts.push({ inline_data: { mime_type: match[1], data: match[2] } });
          } else if (typeof part.file_data === "string") {
            const match = part.file_data.match(/^data:([^;]+);base64,(.+)$/s);
            if (match) parts.push({ inline_data: { mime_type: match[1], data: match[2] } });
          }
        }
      }

      if (item.role === "system") {
        for (const part of parts) if (part.text) systemParts.push(part.text);
      } else if (parts.length) {
        contents.push({ role, parts });
      }
    }

    if (!contents.length) {
      return res.status(400).json({ error: "Please send a text message or supported attachment." });
    }

    const requestedTokens = Number(payload.max_output_tokens ?? 1024);
    const maxOutputTokens = Number.isFinite(requestedTokens)
      ? Math.min(4096, Math.max(256, Math.floor(requestedTokens)))
      : 1024;

    const body = {
      contents,
      generationConfig: { maxOutputTokens, temperature: 0.7 }
    };
    if (systemParts.length) body.systemInstruction = { parts: [{ text: systemParts.join("\n\n").slice(0, 20000) }] };

    // Google Search grounding is used only when the app explicitly requests web search.
    if (Array.isArray(payload.tools) && payload.tools.some(tool => tool && tool.type === "web_search")) {
      body.tools = [{ google_search: {} }];
    }

    const upstream = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent",
      {
        method: "POST",
        headers: {
          "x-goog-api-key": apiKey,
          "Content-Type": "application/json"
        },
        body: JSON.stringify(body)
      }
    );

    const upstreamText = await upstream.text();
    let data;
    try { data = JSON.parse(upstreamText); } catch { data = {}; }

    if (!upstream.ok) {
      const message = data?.error?.message || "Gemini API request failed.";
      const status = upstream.status === 429 ? 429 : upstream.status === 401 || upstream.status === 403 ? 502 : upstream.status;
      return res.status(status).json({
        error: upstream.status === 429
          ? "Free Gemini API limit reached for now. Please wait and try again."
          : message
      });
    }

    const outputText = (data?.candidates?.[0]?.content?.parts || [])
      .map(part => typeof part.text === "string" ? part.text : "")
      .join("");

    if (!outputText.trim()) {
      return res.status(502).json({ error: "Gemini returned an empty response. Please try again." });
    }

    const responseBody = {
      id: "mayra-gemini-response",
      object: "response",
      status: "completed",
      output_text: outputText,
      output: [{ type: "message", role: "assistant", content: [{ type: "output_text", text: outputText }] }]
    };

    res.setHeader("Cache-Control", "no-store");
    if (payload.stream) {
      res.setHeader("Content-Type", "text/event-stream; charset=utf-8");
      res.setHeader("Cache-Control", "no-cache, no-transform");
      res.write(`data: ${JSON.stringify({ type: "response.output_text.delta", delta: outputText })}\n\n`);
      res.write(`data: ${JSON.stringify({ type: "response.completed", response: responseBody })}\n\n`);
      res.write("data: [DONE]\n\n");
      return res.end();
    }

    return res.status(200).json(responseBody);
  } catch (error) {
    return res.status(500).json({
      error: error instanceof Error ? error.message : "Unknown server error"
    });
  }
}
