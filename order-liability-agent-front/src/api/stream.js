// 流式发送（API.md 3.5）：POST + SSE 不能用 EventSource（只支持 GET），用 fetch + ReadableStream
// 解析逻辑直接采用 API.md 3.5 参考代码，不自己发明解析器
// url 参数默认真实端点，仅便于本地模拟断流测试
export async function sendStream(
  chatId,
  message,
  { onDelta, onDone, onError },
  url = 'http://localhost:8787/api/chat/send/stream'
) {
  let doneReceived = false
  try {
    const resp = await fetch(url, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ chatId, message })
    })
    if (!resp.ok) {
      onError('网络异常')
      return
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buf = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      // SSE 事件以空行分隔
      let idx
      while ((idx = buf.indexOf('\n\n')) >= 0) {
        const raw = buf.slice(0, idx)
        buf = buf.slice(idx + 2)
        let event = 'message'
        const dataLines = []
        for (const line of raw.split('\n')) {
          if (line.startsWith('event:')) event = line.slice(6).trim()
          else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''))
        }
        const data = dataLines.join('\n') // data 可能含换行，被拆成多行 data:
        if (event === 'delta') onDelta(data)
        else if (event === 'done') {
          doneReceived = true
          onDone(data)
        }
      }
    }
    // 流读完却没收到 done：视同连接中断，交给调用方走 history 恢复
    if (!doneReceived) {
      onError('连接中断')
    }
  } catch {
    // fetch 抛错或读取中断：不重发，由调用方调 history 恢复最终状态
    onError('连接中断')
  }
}
