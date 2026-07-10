/**
 * AI Router — streams from Azure-hosted models
 * Supports: Claude Opus 4.7, Claude Sonnet 4.6, Claude Opus 4.6,
 *           GPT 5.4, GPT 5.4 Pro, Kimi k-2.6, DeepSeek V4
 */
const axios = require('axios');

// ── Model config ─────────────────────────────────────────────────────────────
const MODELS = {
  'claude-opus-4-7': {
    type: 'anthropic',
    apiKey: process.env.AZURE_KEY_PRIMARY,
    url: process.env.AZURE_CLAUDE_URL,
    model: 'claude-opus-4-7',
    maxTokens: 16000,
  },
  'claude-sonnet-4-6': {
    type: 'anthropic',
    apiKey: process.env.AZURE_KEY_PRIMARY,
    url: process.env.AZURE_CLAUDE_URL,
    model: 'claude-sonnet-4-6',
    maxTokens: 16000,
  },
  'claude-opus-4-6': {
    type: 'anthropic',
    apiKey: process.env.AZURE_KEY_PRIMARY,
    url: process.env.AZURE_CLAUDE_URL,
    model: 'claude-opus-4-6',
    maxTokens: 16000,
  },
  'gpt-5-4': {
    type: 'openai_responses',
    apiKey: process.env.AZURE_KEY_PRIMARY,
    url: process.env.AZURE_GPT_URL,
    model: 'gpt-5-4',
    maxTokens: 16000,
  },
  'gpt-5-4-pro': {
    type: 'openai_responses',
    apiKey: process.env.AZURE_KEY_SECONDARY,
    url: process.env.AZURE_GPT_PRO_URL,
    model: 'gpt-5-4-pro',
    maxTokens: 16000,
  },
  'kimi-k-2-6': {
    type: 'openai_chat',
    apiKey: process.env.AZURE_KEY_SECONDARY,
    url: process.env.AZURE_KIMI_URL,
    model: 'Kimi-K2',
    maxTokens: 16000,
  },
  'deepseek-v4': {
    type: 'openai_chat',
    apiKey: process.env.AZURE_KEY_SECONDARY,
    url: process.env.AZURE_DEEPSEEK_URL,
    model: 'DeepSeek-V4-0',
    maxTokens: 16000,
  },
};

const SYSTEM_PROMPT = `You are Velaud, a powerful AI assistant that helps users with any task. 
You are helpful, accurate, and concise. You support markdown formatting in your responses.
Reply in the same language as the user.`;

// ── Anthropic streaming ───────────────────────────────────────────────────────
async function streamAnthropic(cfg, messages, showThinking, callbacks) {
  const anthropicMessages = messages.map(m => ({ role: m.role === 'user' ? 'user' : 'assistant', content: m.content }));

  const body = {
    model: cfg.model,
    max_tokens: cfg.maxTokens,
    system: SYSTEM_PROMPT,
    messages: anthropicMessages,
    stream: true,
  };

  if (showThinking) {
    body.thinking = { type: 'enabled', budget_tokens: 8000 };
  }

  const response = await axios.post(cfg.url, body, {
    headers: {
      'x-api-key': cfg.apiKey,
      'anthropic-version': '2023-06-01',
      'content-type': 'application/json',
    },
    responseType: 'stream',
    timeout: 120000,
  });

  let inThinking = false;

  await new Promise((resolve, reject) => {
    let buf = '';
    response.data.on('data', chunk => {
      buf += chunk.toString();
      const lines = buf.split('\n');
      buf = lines.pop();
      for (const line of lines) {
        if (!line.startsWith('data: ')) continue;
        try {
          const ev = JSON.parse(line.slice(6));
          if (ev.type === 'content_block_start') {
            if (ev.content_block?.type === 'thinking') {
              inThinking = true;
              callbacks.onThinkingStart?.();
            }
          } else if (ev.type === 'content_block_stop') {
            if (inThinking) { inThinking = false; callbacks.onThinkingEnd?.(); }
          } else if (ev.type === 'content_block_delta') {
            const d = ev.delta;
            if (d.type === 'thinking_delta') callbacks.onThinkingDelta?.(d.thinking);
            else if (d.type === 'text_delta') callbacks.onDelta?.(d.text);
          } else if (ev.type === 'message_stop') {
            resolve();
          }
        } catch (_) {}
      }
    });
    response.data.on('end', resolve);
    response.data.on('error', reject);
  });
}

// ── OpenAI Responses API streaming ───────────────────────────────────────────
async function streamOpenAIResponses(cfg, messages, showThinking, callbacks) {
  const input = [
    { role: 'system', content: SYSTEM_PROMPT },
    ...messages.map(m => ({ role: m.role === 'user' ? 'user' : 'assistant', content: m.content }))
  ];

  const body = {
    model: cfg.model,
    input,
    stream: true,
    max_output_tokens: cfg.maxTokens,
  };

  if (showThinking) body.reasoning = { effort: 'medium' };

  const response = await axios.post(cfg.url, body, {
    headers: {
      'api-key': cfg.apiKey,
      'content-type': 'application/json',
    },
    responseType: 'stream',
    timeout: 120000,
  });

  await new Promise((resolve, reject) => {
    let buf = '';
    let inReasoning = false;
    response.data.on('data', chunk => {
      buf += chunk.toString();
      const lines = buf.split('\n');
      buf = lines.pop();
      for (const line of lines) {
        if (!line.startsWith('data: ')) continue;
        const raw = line.slice(6).trim();
        if (raw === '[DONE]') { resolve(); continue; }
        try {
          const ev = JSON.parse(raw);
          if (ev.type === 'response.reasoning_summary_part.added') {
            inReasoning = true; callbacks.onThinkingStart?.();
          } else if (ev.type === 'response.reasoning_summary_text.delta') {
            callbacks.onThinkingDelta?.(ev.delta);
          } else if (ev.type === 'response.reasoning_summary_part.done') {
            inReasoning = false; callbacks.onThinkingEnd?.();
          } else if (ev.type === 'response.output_text.delta') {
            callbacks.onDelta?.(ev.delta);
          } else if (ev.type === 'response.completed') {
            resolve();
          }
        } catch (_) {}
      }
    });
    response.data.on('end', resolve);
    response.data.on('error', reject);
  });
}

// ── OpenAI Chat Completions streaming ────────────────────────────────────────
async function streamOpenAIChat(cfg, messages, showThinking, callbacks) {
  const openaiMessages = [
    { role: 'system', content: SYSTEM_PROMPT },
    ...messages.map(m => ({ role: m.role === 'user' ? 'user' : 'assistant', content: m.content }))
  ];

  const body = {
    model: cfg.model,
    messages: openaiMessages,
    max_tokens: cfg.maxTokens,
    stream: true,
  };

  const response = await axios.post(cfg.url, body, {
    headers: {
      'api-key': cfg.apiKey,
      'content-type': 'application/json',
    },
    responseType: 'stream',
    timeout: 120000,
  });

  await new Promise((resolve, reject) => {
    let buf = '';
    response.data.on('data', chunk => {
      buf += chunk.toString();
      const lines = buf.split('\n');
      buf = lines.pop();
      for (const line of lines) {
        if (!line.startsWith('data: ')) continue;
        const raw = line.slice(6).trim();
        if (raw === '[DONE]') { resolve(); continue; }
        try {
          const ev = JSON.parse(raw);
          const delta = ev.choices?.[0]?.delta;
          if (delta?.content) callbacks.onDelta?.(delta.content);
          if (ev.choices?.[0]?.finish_reason === 'stop') resolve();
        } catch (_) {}
      }
    });
    response.data.on('end', resolve);
    response.data.on('error', reject);
  });
}

// ── Public API ────────────────────────────────────────────────────────────────
async function streamMessage({ model, messages, webSearch, showThinking, onThinkingStart, onThinkingDelta, onThinkingEnd, onDelta, onEnd, onError }) {
  const cfg = MODELS[model] || MODELS['claude-opus-4-7'];
  const callbacks = { onThinkingStart, onThinkingDelta, onThinkingEnd, onDelta };

  try {
    if (cfg.type === 'anthropic') {
      await streamAnthropic(cfg, messages, showThinking, callbacks);
    } else if (cfg.type === 'openai_responses') {
      await streamOpenAIResponses(cfg, messages, showThinking, callbacks);
    } else {
      await streamOpenAIChat(cfg, messages, showThinking, callbacks);
    }
    onEnd?.();
  } catch (err) {
    onError?.(err);
  }
}

module.exports = { streamMessage };
