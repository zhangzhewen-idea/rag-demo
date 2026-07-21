import {describe, expect, it} from 'vitest'
import {parseSse} from './sse'

describe('parseSse', () => {
  it('解析完整事件并保留未完成分块', () => {
    const events: Array<[string, unknown]> = [];
    const remainder = parseSse('event: delta\ndata: {"content":"你"}\n\nevent: done\ndata: {"messageId":1}', (event, data) => events.push([event, data]));
    expect(events).toEqual([['delta', {content: '你'}]]);
    expect(remainder).toContain('event: done')
  });
  it('支持 references 数组', () => {
    const events: Array<[string, unknown]> = [];
    parseSse('event: references\ndata: [{"documentId":1}]\n\n', (event, data) => events.push([event, data]));
    expect(events[0]?.[0]).toBe('references')
  })
})
