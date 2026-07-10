const express = require('express');
const router = express.Router();
const admin = require('../firebase');
const db = require('../db');
const { v4: uuidv4 } = require('uuid');
const aiRouter = require('../ai');

// Auth middleware
async function requireAuth(req, res, next) {
  const auth = req.headers.authorization;
  if (!auth?.startsWith('Bearer ')) return res.status(401).json({ error: 'Unauthorized' });
  const idToken = auth.slice(7);
  try {
    const decoded = await admin.auth().verifyIdToken(idToken);
    req.firebaseUid = decoded.uid;
    // Get local user
    const user = db.prepare('SELECT * FROM users WHERE firebase_uid=? OR id=?')
      .get(req.firebaseUid, req.firebaseUid.replace('email_', ''));
    req.user = user;
    next();
  } catch (err) {
    return res.status(401).json({ error: 'Invalid token' });
  }
}

// GET /api/chat/history
router.get('/history', requireAuth, (req, res) => {
  if (!req.user) return res.json({ conversations: [] });
  const convs = db.prepare(
    'SELECT id, title, model, updated_at FROM conversations WHERE user_id=? ORDER BY updated_at DESC LIMIT 60'
  ).all(req.user.id);
  res.json({ conversations: convs });
});

// GET /api/chat/:conversationId/messages
router.get('/:conversationId/messages', requireAuth, (req, res) => {
  const { conversationId } = req.params;
  const conv = db.prepare('SELECT * FROM conversations WHERE id=?').get(conversationId);
  if (!conv || (req.user && conv.user_id !== req.user.id)) {
    return res.status(404).json({ error: 'Not found' });
  }
  const messages = db.prepare(
    'SELECT id, role, content, thinking, model, created_at FROM messages WHERE conversation_id=? ORDER BY created_at ASC LIMIT 200'
  ).all(conversationId);
  res.json({ messages });
});

// POST /api/chat/message  (main chat endpoint)
router.post('/message', requireAuth, async (req, res) => {
  const { message, model = 'claude-opus-4-7', conversationId, webSearch = false, showThinking = false } = req.body;
  if (!message?.trim()) return res.status(400).json({ error: 'Empty message' });

  let convId = conversationId;
  let userId = req.user?.id;

  // Create or validate conversation
  if (!convId) {
    convId = uuidv4();
    const title = message.slice(0, 60) + (message.length > 60 ? '…' : '');
    db.prepare('INSERT INTO conversations (id, user_id, title, model) VALUES (?, ?, ?, ?)')
      .run(convId, userId, title, model);
  } else {
    const conv = db.prepare('SELECT * FROM conversations WHERE id=?').get(convId);
    if (!conv) return res.status(404).json({ error: 'Conversation not found' });
    db.prepare('UPDATE conversations SET model=?, updated_at=strftime(\'%s\',\'now\') WHERE id=?').run(model, convId);
  }

  // Store user message
  const userMsgId = uuidv4();
  db.prepare('INSERT INTO messages (id, conversation_id, role, content) VALUES (?, ?, ?, ?)')
    .run(userMsgId, convId, 'user', message.trim());

  // Fetch conversation history (last 20 turns)
  const history = db.prepare(
    'SELECT role, content FROM messages WHERE conversation_id=? ORDER BY created_at ASC LIMIT 40'
  ).all(convId);

  // Set SSE headers
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.setHeader('X-Conversation-Id', convId);
  res.flushHeaders();

  let fullContent = '';
  let thinkingContent = '';

  const send = (data) => res.write(`data: ${JSON.stringify(data)}\n\n`);

  try {
    await aiRouter.streamMessage({
      model,
      messages: history,
      webSearch,
      showThinking,
      onThinkingStart: () => send({ type: 'thinking_start' }),
      onThinkingDelta: (text) => { thinkingContent += text; send({ type: 'thinking_delta', text }); },
      onThinkingEnd: () => send({ type: 'thinking_end' }),
      onDelta: (text) => { fullContent += text; send({ type: 'delta', text }); },
      onEnd: () => {
        // Store AI message
        const aiMsgId = uuidv4();
        db.prepare('INSERT INTO messages (id, conversation_id, role, content, thinking, model) VALUES (?, ?, ?, ?, ?, ?)')
          .run(aiMsgId, convId, 'assistant', fullContent, thinkingContent || null, model);
        db.prepare('UPDATE conversations SET updated_at=strftime(\'%s\',\'now\') WHERE id=?').run(convId);
        send({ type: 'end', conversationId: convId, messageId: aiMsgId });
        res.end();
      },
      onError: (err) => {
        console.error('AI error:', err);
        send({ type: 'error', error: err.message || 'AI hatası' });
        res.end();
      }
    });
  } catch (err) {
    console.error('Chat error:', err);
    send({ type: 'error', error: err.message || 'Sunucu hatası' });
    res.end();
  }
});

// DELETE /api/chat/:conversationId
router.delete('/:conversationId', requireAuth, (req, res) => {
  const { conversationId } = req.params;
  const conv = db.prepare('SELECT * FROM conversations WHERE id=?').get(conversationId);
  if (!conv || (req.user && conv.user_id !== req.user.id)) {
    return res.status(404).json({ error: 'Not found' });
  }
  db.prepare('DELETE FROM messages WHERE conversation_id=?').run(conversationId);
  db.prepare('DELETE FROM conversations WHERE id=?').run(conversationId);
  res.json({ success: true });
});

module.exports = router;
