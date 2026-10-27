import webSocket from '@ohos.net.webSocket'
import { connection } from '@kit.NetworkKit'
import { util } from '@kit.ArkTS'
import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from '@kuikly-open/render'

/**
 * 鸿蒙端 WebSocket 原生 Module
 *
 * 基于 @ohos.net.webSocket 实现，继承 KuiklyRenderBaseModule。
 * KMP 层通过 toNative 调用 call()，原生端通过 keepCallbackAlive 的 callback 回传事件。
 *
 * 注册方式：
 * ```ets
 * registerExternalModule("KRWebSocketModule", () => new KRWebSocketModule())
 * ```
 */
export class KRWebSocketModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = 'KRWebSocketModule'

  private ws: webSocket.WebSocket | null = null
  private timers: Map<string, number> = new Map()

  /** connect 时传入的常驻回调，用于回传所有 WebSocket 事件 */
  private wsEventCallback: KuiklyRenderCallback | null = null

  /** 各定时器对应的常驻回调 */
  private timerCallbacks: Map<string, KuiklyRenderCallback> = new Map()

  /** 网络监听的常驻回调 */
  private networkEventCallback: KuiklyRenderCallback | null = null

  /** 网络连接对象 */
  private netConnection: connection.NetConnection | null = null

  syncMode(): boolean {
    return false
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    const paramsStr = params as string
    const json: Record<string, Object> = paramsStr ? JSON.parse(paramsStr) as Record<string, Object> : {} as Record<string, Object>

    switch (method) {
      case 'connect': {
        this.wsEventCallback = callback
        const url = (json['url'] as string) || ''
        const timeoutMs = (json['timeoutMs'] as number) || 10000
        const headersObj = (json['headers'] as Record<string, string>) || {}
        this.doConnect(url, headersObj, timeoutMs)
        break
      }
      case 'disconnect': {
        const code = (json['code'] as number) || 1000
        const reason = (json['reason'] as string) || ''
        this.doDisconnect(code, reason)
        break
      }
      case 'send': {
        const data = (json['data'] as string) || ''
        this.doSend(data)
        break
      }
      case 'sendBinary': {
        const data = (json['data'] as string) || ''
        this.doSendBinary(data)
        break
      }
      case 'startTimer': {
        const timerId = (json['timerId'] as string) || ''
        if (callback) {
          this.timerCallbacks.set(timerId, callback)
        }
        const intervalMs = (json['intervalMs'] as number) || 0
        const repeating = (json['repeating'] as boolean) || false
        this.doStartTimer(timerId, intervalMs, repeating)
        break
      }
      case 'stopTimer': {
        const timerId = (json['timerId'] as string) || ''
        this.timerCallbacks.delete(timerId)
        this.doStopTimer(timerId)
        break
      }
      case 'registerNetwork': {
        this.networkEventCallback = callback
        this.doRegisterNetwork()
        break
      }
      case 'unregisterNetwork': {
        this.networkEventCallback = null
        this.doUnregisterNetwork()
        break
      }
      default:
        console.warn(`KRWebSocketModule: Unknown method: ${method}`)
        break
    }
    return null
  }

  // ===== 事件通知 =====

  /**
   * 通过常驻 callback 回传事件到 KMP 层
   */
  private notifyEvent(callback: KuiklyRenderCallback | null, type: string, data: string = ''): void {
    console.info(`KRWebSocketModule: notifyEvent type=${type}, hasCallback=${callback !== null}`)
    if (!callback) return
    const json = JSON.stringify({ type, data })
    callback(json)
  }

  // ===== WebSocket =====

  private doConnect(url: string, headers: Record<string, string>, timeoutMs: number): void {
    this.doDisconnect(1000, 'Reconnecting')

    this.ws = webSocket.createWebSocket()

    this.ws.on('open', (err, value) => {
      console.info(`KRWebSocketModule: open event fired, err=${JSON.stringify(err)}, value=${JSON.stringify(value)}`)
      if (!err || err.code === 0) {
        this.notifyEvent(this.wsEventCallback, 'connected')
      } else {
        this.notifyEvent(this.wsEventCallback, 'error', err.message || 'Connection failed')
      }
    })

    this.ws.on('message', (err, value) => {
      if (!err || err.code === 0) {
        if (typeof value === 'string') {
          this.notifyEvent(this.wsEventCallback, 'message', value)
        } else if (value instanceof ArrayBuffer) {
          // Binary Frame → Base64 编码后回传
          const helper = new util.Base64Helper()
          const uint8 = new Uint8Array(value)
          const base64 = helper.encodeToStringSync(uint8)
          this.notifyEvent(this.wsEventCallback, 'binaryMessage', base64)
        }
      }
    })

    this.ws.on('close', (err, value) => {
      const closeInfo = JSON.stringify({
        code: value?.code || 0,
        reason: value?.reason || ''
      })
      this.notifyEvent(this.wsEventCallback, 'disconnected', closeInfo)
    })

    this.ws.on('error', (err) => {
      this.notifyEvent(this.wsEventCallback, 'error', err?.message || 'WebSocket error')
    })

    this.ws.connect(url, {
      header: headers
    }).catch((err: Error) => {
      this.notifyEvent(this.wsEventCallback, 'error', err.message || 'Connect failed')
    })
  }

  private doDisconnect(code: number, reason: string): void {
    if (this.ws) {
      try {
        this.ws.close({
          code: code,
          reason: reason
        })
      } catch (e) {
        // 忽略关闭异常
      }
      this.ws = null
    }
  }

  private doSend(data: string): void {
    if (this.ws) {
      this.ws.send(data).catch((err: Error) => {
        this.notifyEvent(this.wsEventCallback, 'error', err.message || 'Send failed')
      })
    }
  }

  private doSendBinary(base64Data: string): void {
    if (this.ws) {
      try {
        const helper = new util.Base64Helper()
        const uint8 = helper.decodeSync(base64Data)
        // 构造独立的 ArrayBuffer，避免 shared buffer 问题
        const ab = new ArrayBuffer(uint8.length)
        const view = new Uint8Array(ab)
        for (let i = 0; i < uint8.length; i++) {
          view[i] = uint8[i]
        }
        this.ws.send(ab).catch((err: Error) => {
          console.error(`KRWebSocketModule: sendBinary failed: ${err.message}`)
          this.notifyEvent(this.wsEventCallback, 'error', err.message || 'Send binary failed')
        })
      } catch (e) {
        const errMsg = (e as Error).message || 'Send binary exception'
        console.error(`KRWebSocketModule: sendBinary exception: ${errMsg}`)
        this.notifyEvent(this.wsEventCallback, 'error', errMsg)
      }
    }
  }

  // ===== 定时器 =====

  private doStartTimer(timerId: string, intervalMs: number, repeating: boolean): void {
    this.doStopTimer(timerId)

    if (repeating) {
      const id = setInterval(() => {
        const timerData = JSON.stringify({ timerId })
        this.notifyEvent(this.timerCallbacks.get(timerId) || null, 'timer', timerData)
      }, intervalMs)
      this.timers.set(timerId, id)
    } else {
      const id = setTimeout(() => {
        this.timers.delete(timerId)
        const cb = this.timerCallbacks.get(timerId) || null
        this.timerCallbacks.delete(timerId)
        const timerData = JSON.stringify({ timerId })
        this.notifyEvent(cb, 'timer', timerData)
      }, intervalMs)
      this.timers.set(timerId, id)
    }
  }

  private doStopTimer(timerId: string): void {
    const id = this.timers.get(timerId)
    if (id !== undefined) {
      clearTimeout(id)
      clearInterval(id)
      this.timers.delete(timerId)
    }
  }

  // ===== 网络监听 =====

  private doRegisterNetwork(): void {
    try {
      this.netConnection = connection.createNetConnection()
      this.netConnection.register(() => {})

      this.netConnection.on('netAvailable', () => {
        this.notifyNetworkEvent(true, 'unknown')
      })

      this.netConnection.on('netUnavailable', () => {
        this.notifyNetworkEvent(false, 'none')
      })

      this.netConnection.on('netCapabilitiesChange', (data) => {
        const caps = data?.netCap?.bearerTypes || []
        let networkType = 'unknown'
        if (caps.indexOf(connection.NetBearType.BEARER_WIFI) >= 0) {
          networkType = 'wifi'
        } else if (caps.indexOf(connection.NetBearType.BEARER_CELLULAR) >= 0) {
          networkType = 'cellular'
        } else if (caps.indexOf(connection.NetBearType.BEARER_ETHERNET) >= 0) {
          networkType = 'ethernet'
        }
        this.notifyNetworkEvent(true, networkType)
      })
    } catch (e) {
      console.error('KRWebSocketModule: registerNetwork failed', e)
    }
  }

  private doUnregisterNetwork(): void {
    try {
      if (this.netConnection) {
        this.netConnection.unregister(() => {})
        this.netConnection = null
      }
    } catch (e) {
      console.error('KRWebSocketModule: unregisterNetwork failed', e)
    }
  }

  private notifyNetworkEvent(isAvailable: boolean, networkType: string): void {
    const data = JSON.stringify({ isAvailable, networkType })
    this.notifyEvent(this.networkEventCallback, 'network', data)
  }

  // ===== 生命周期 =====

  onDestroy(): void {
    // 停止所有定时器
    this.timers.forEach((id) => {
      clearTimeout(id)
      clearInterval(id)
    })
    this.timers.clear()
    this.timerCallbacks.clear()

    // 关闭 WebSocket
    this.doDisconnect(1000, 'Module destroyed')

    // 取消网络监听
    this.doUnregisterNetwork()

    this.wsEventCallback = null
    this.networkEventCallback = null
  }
}
