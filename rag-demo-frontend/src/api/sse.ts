import {getAccessToken} from './http'
import type {Reference} from '@/types'

export interface StreamHandlers {
  delta: (text: string) => void;
  references: (refs: Reference[]) => void;
  done: (data: { messageId: number; elapsedMs: number }) => void;
  error: (message: string) => void
}

/** 解析具名 SSE 事件，保留跨网络分块的未完成行。 */
export function parseSse(buffer: string, onEvent: (event: string, data: unknown) => void) {
  const blocks = buffer.replace(/\r\n/g, '\n').split('\n\n');
  const remainder = blocks.pop() ?? '';
  for (const block of blocks) {
    let event = 'message';
    const lines: string[] = [];
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim();
      if (line.startsWith('data:')) lines.push(line.slice(5).trim())
    }
    if (lines.length) {
      const raw = lines.join('\n');
      try {
        onEvent(event, JSON.parse(raw))
      } catch {
        onEvent(event, raw)
      }
    }
  }
  return remainder
}

/** 使用可取消 Fetch 进行流式问答。 */
export async function streamChat(conversationId: number, content: string, handlers: StreamHandlers, signal: AbortSignal) {
  const response = await fetch(`/api/conversations/${conversationId}/messages/stream`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json', Authorization: `Bearer ${getAccessToken()}`},
    body: JSON.stringify({content}),
    signal
  });
  if (!response.ok || !response.body) throw new Error('无法建立问答流');
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const {done, value} = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, {stream: true});
    buffer = parseSse(buffer, (event, data) => {
      const value = data as Record<string, unknown>;
      if (event === 'delta') handlers.delta(String(value.content ?? '')); else if (event === 'references') handlers.references(data as Reference[]); else if (event === 'done') handlers.done(data as {
        messageId: number;
        elapsedMs: number
      }); else if (event === 'error') handlers.error(String(value.message ?? '回答失败'))
    })
  }
}
