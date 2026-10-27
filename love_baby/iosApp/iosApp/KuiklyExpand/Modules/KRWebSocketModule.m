#import "KRWebSocketModule.h"
#import <OpenKuiklyIOSRender/NSObject+KR.h>

@interface KRWebSocketModule () <NSURLSessionDelegate, NSURLSessionWebSocketDelegate>

@property (nonatomic, strong, nullable) NSURLSession *urlSession;
@property (nonatomic, strong, nullable) NSURLSessionWebSocketTask *webSocketTask;
@property (nonatomic, strong) NSMutableDictionary<NSString *, dispatch_source_t> *timers;
@property (nonatomic, strong) dispatch_queue_t timerQueue;
@property (nonatomic, strong, nullable) nw_path_monitor_t pathMonitor;

@property (nonatomic, copy, nullable) KuiklyRenderCallback wsEventCallback;
@property (nonatomic, strong) NSMutableDictionary<NSString *, KuiklyRenderCallback> *timerCallbacks;
@property (nonatomic, copy, nullable) KuiklyRenderCallback networkEventCallback;

@end

@implementation KRWebSocketModule

- (instancetype)init {
    self = [super init];
    if (self) {
        _timers = [NSMutableDictionary dictionary];
        _timerCallbacks = [NSMutableDictionary dictionary];
        _timerQueue = dispatch_queue_create("com.kuiklybase.websocket.timer", DISPATCH_QUEUE_SERIAL);
    }
    return self;
}

#pragma mark - Module methods (called via hrv_callWithMethod:)

- (void)connect:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    self.wsEventCallback = callback;

    NSString *url = params[@"url"] ?: @"";
    NSTimeInterval timeoutMs = [params[@"timeoutMs"] doubleValue] ?: 10000;
    NSDictionary *headers = params[@"headers"] ?: @{};
    [self doConnectWithUrl:url headers:headers timeoutMs:timeoutMs];
}

- (void)disconnect:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSInteger code = [params[@"code"] integerValue] ?: 1000;
    NSString *reason = params[@"reason"] ?: @"";
    [self doDisconnectWithCode:code reason:reason];
}

- (void)send:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *data = params[@"data"] ?: @"";
    [self doSend:data];
}

- (void)sendBinary:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *base64Data = params[@"data"] ?: @"";
    [self doSendBinary:base64Data];
}

- (void)startTimer:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];

    NSString *timerId = params[@"timerId"] ?: @"";
    NSTimeInterval intervalMs = [params[@"intervalMs"] doubleValue];
    BOOL repeating = [params[@"repeating"] boolValue];

    if (callback) {
        self.timerCallbacks[timerId] = callback;
    }
    [self doStartTimerWithId:timerId intervalMs:intervalMs repeating:repeating];
}

- (void)stopTimer:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *timerId = params[@"timerId"] ?: @"";
    [self.timerCallbacks removeObjectForKey:timerId];
    [self doStopTimerWithId:timerId];
}

- (void)registerNetwork:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    self.networkEventCallback = callback;
    [self doRegisterNetwork];
}

- (void)unregisterNetwork:(NSDictionary *)args {
    [self doUnregisterNetwork];
    self.networkEventCallback = nil;
}

#pragma mark - Event notification

- (void)notifyEvent:(KuiklyRenderCallback)callback type:(NSString *)type data:(NSString *)data {
    if (!callback) return;
    NSDictionary *event = @{@"type": type, @"data": data ?: @""};
    NSData *jsonData = [NSJSONSerialization dataWithJSONObject:event options:0 error:nil];
    NSString *jsonStr = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
    callback(jsonStr);
}

#pragma mark - WebSocket

- (void)doConnectWithUrl:(NSString *)url headers:(NSDictionary *)headers timeoutMs:(NSTimeInterval)timeoutMs {
    [self doDisconnectWithCode:1000 reason:@"Reconnecting"];

    NSURLSessionConfiguration *config = [NSURLSessionConfiguration defaultSessionConfiguration];
    config.timeoutIntervalForRequest = timeoutMs / 1000.0;
    config.timeoutIntervalForResource = timeoutMs / 1000.0;

    self.urlSession = [NSURLSession sessionWithConfiguration:config
                                                   delegate:self
                                              delegateQueue:[NSOperationQueue mainQueue]];

    NSURL *wsURL = [NSURL URLWithString:url];
    if (!wsURL) {
        [self notifyEvent:self.wsEventCallback type:@"error" data:@"Invalid WebSocket URL"];
        return;
    }

    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:wsURL];
    [headers enumerateKeysAndObjectsUsingBlock:^(NSString *key, NSString *value, BOOL *stop) {
        [request setValue:value forHTTPHeaderField:key];
    }];

    self.webSocketTask = [self.urlSession webSocketTaskWithRequest:request];
    [self.webSocketTask resume];
    [self receiveMessage];
}

- (void)doDisconnectWithCode:(NSInteger)code reason:(NSString *)reason {
    [self.webSocketTask cancelWithCloseCode:code reason:[reason dataUsingEncoding:NSUTF8StringEncoding]];
    self.webSocketTask = nil;
    [self.urlSession invalidateAndCancel];
    self.urlSession = nil;
}

- (void)doSend:(NSString *)data {
    if (!self.webSocketTask) return;
    NSURLSessionWebSocketMessage *message = [[NSURLSessionWebSocketMessage alloc] initWithString:data];
    [self.webSocketTask sendMessage:message completionHandler:^(NSError * _Nullable error) {
        if (error) {
            dispatch_async(dispatch_get_main_queue(), ^{
                [self notifyEvent:self.wsEventCallback type:@"error" data:error.localizedDescription ?: @"Send failed"];
            });
        }
    }];
}

- (void)doSendBinary:(NSString *)base64Data {
    if (!self.webSocketTask) return;
    NSData *binaryData = [[NSData alloc] initWithBase64EncodedString:base64Data options:0];
    if (!binaryData) {
        [self notifyEvent:self.wsEventCallback type:@"error" data:@"Invalid Base64 data"];
        return;
    }
    NSURLSessionWebSocketMessage *message = [[NSURLSessionWebSocketMessage alloc] initWithData:binaryData];
    [self.webSocketTask sendMessage:message completionHandler:^(NSError * _Nullable error) {
        if (error) {
            dispatch_async(dispatch_get_main_queue(), ^{
                [self notifyEvent:self.wsEventCallback type:@"error" data:error.localizedDescription ?: @"Send binary failed"];
            });
        }
    }];
}

#pragma mark - Message receive loop

- (void)receiveMessage {
    if (!self.webSocketTask) return;
    __weak typeof(self) weakSelf = self;
    [self.webSocketTask receiveMessageWithCompletionHandler:^(NSURLSessionWebSocketMessage * _Nullable message, NSError * _Nullable error) {
        __strong typeof(weakSelf) strongSelf = weakSelf;
        if (!strongSelf) return;

        if (error) {
            dispatch_async(dispatch_get_main_queue(), ^{
                if (error.code == 57 || error.code == NSURLErrorCancelled) {
                    NSDictionary *closeInfo = @{@"code": @(1000), @"reason": @"Connection closed"};
                    NSData *jsonData = [NSJSONSerialization dataWithJSONObject:closeInfo options:0 error:nil];
                    NSString *jsonStr = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
                    [strongSelf notifyEvent:strongSelf.wsEventCallback type:@"disconnected" data:jsonStr ?: @""];
                } else {
                    [strongSelf notifyEvent:strongSelf.wsEventCallback type:@"error" data:error.localizedDescription ?: @"Receive failed"];
                }
            });
            return;
        }

        if (message) {
            switch (message.type) {
                case NSURLSessionWebSocketMessageTypeString: {
                    NSString *text = message.string;
                    if (text) {
                        dispatch_async(dispatch_get_main_queue(), ^{
                            [strongSelf notifyEvent:strongSelf.wsEventCallback type:@"message" data:text];
                        });
                    }
                    break;
                }
                case NSURLSessionWebSocketMessageTypeData: {
                    NSData *data = message.data;
                    if (data) {
                        NSString *base64 = [data base64EncodedStringWithOptions:0];
                        dispatch_async(dispatch_get_main_queue(), ^{
                            [strongSelf notifyEvent:strongSelf.wsEventCallback type:@"binaryMessage" data:base64];
                        });
                    }
                    break;
                }
            }
        }
        [strongSelf receiveMessage];
    }];
}

#pragma mark - NSURLSessionWebSocketDelegate

- (void)URLSession:(NSURLSession *)session webSocketTask:(NSURLSessionWebSocketTask *)webSocketTask didOpenWithProtocol:(NSString *)protocol {
    [self notifyEvent:self.wsEventCallback type:@"connected" data:@""];
}

- (void)URLSession:(NSURLSession *)session webSocketTask:(NSURLSessionWebSocketTask *)webSocketTask didCloseWithCode:(NSURLSessionWebSocketCloseCode)closeCode reason:(NSData *)reason {
    NSString *reasonStr = reason ? [[NSString alloc] initWithData:reason encoding:NSUTF8StringEncoding] : @"";
    NSDictionary *closeInfo = @{@"code": @(closeCode), @"reason": reasonStr ?: @""};
    NSData *jsonData = [NSJSONSerialization dataWithJSONObject:closeInfo options:0 error:nil];
    NSString *jsonStr = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
    [self notifyEvent:self.wsEventCallback type:@"disconnected" data:jsonStr ?: @""];
}

#pragma mark - Timers

- (void)doStartTimerWithId:(NSString *)timerId intervalMs:(NSTimeInterval)intervalMs repeating:(BOOL)repeating {
    [self doStopTimerWithId:timerId];

    dispatch_source_t timer = dispatch_source_create(DISPATCH_SOURCE_TYPE_TIMER, 0, 0, dispatch_get_main_queue());
    uint64_t interval = (uint64_t)(intervalMs * NSEC_PER_MSEC);
    uint64_t leeway = (uint64_t)(100 * NSEC_PER_MSEC);

    if (repeating) {
        dispatch_source_set_timer(timer, dispatch_time(DISPATCH_TIME_NOW, interval), interval, leeway);
    } else {
        dispatch_source_set_timer(timer, dispatch_time(DISPATCH_TIME_NOW, interval), DISPATCH_TIME_FOREVER, leeway);
    }

    __weak typeof(self) weakSelf = self;
    dispatch_source_set_event_handler(timer, ^{
        __strong typeof(weakSelf) strongSelf = weakSelf;
        if (!strongSelf) return;
        KuiklyRenderCallback cb = strongSelf.timerCallbacks[timerId];
        if (cb) {
            NSDictionary *timerData = @{@"timerId": timerId};
            NSData *jsonData = [NSJSONSerialization dataWithJSONObject:timerData options:0 error:nil];
            NSString *jsonStr = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
            [strongSelf notifyEvent:cb type:@"timer" data:jsonStr];
        }
        if (!repeating) {
            [strongSelf doStopTimerWithId:timerId];
            [strongSelf.timerCallbacks removeObjectForKey:timerId];
        }
    });

    dispatch_async(self.timerQueue, ^{
        self.timers[timerId] = timer;
    });
    dispatch_resume(timer);
}

- (void)doStopTimerWithId:(NSString *)timerId {
    dispatch_sync(self.timerQueue, ^{
        dispatch_source_t timer = self.timers[timerId];
        if (timer) {
            dispatch_source_cancel(timer);
            [self.timers removeObjectForKey:timerId];
        }
    });
}

#pragma mark - Network monitoring

- (void)doRegisterNetwork {
    [self doUnregisterNetwork];

    self.pathMonitor = nw_path_monitor_create();
    nw_path_monitor_set_queue(self.pathMonitor, dispatch_get_main_queue());

    __weak typeof(self) weakSelf = self;
    nw_path_monitor_set_update_handler(self.pathMonitor, ^(nw_path_t _Nonnull path) {
        __strong typeof(weakSelf) strongSelf = weakSelf;
        if (!strongSelf || !strongSelf.networkEventCallback) return;

        nw_path_status_t status = nw_path_get_status(path);
        BOOL isAvailable = (status == nw_path_status_satisfied || status == nw_path_status_satisfiable);

        NSString *networkType = @"unknown";
        if (nw_path_uses_interface_type(path, nw_interface_type_wifi)) {
            networkType = @"wifi";
        } else if (nw_path_uses_interface_type(path, nw_interface_type_cellular)) {
            networkType = @"cellular";
        } else if (nw_path_uses_interface_type(path, nw_interface_type_wired)) {
            networkType = @"ethernet";
        } else if (!isAvailable) {
            networkType = @"none";
        }

        NSDictionary *info = @{@"isAvailable": @(isAvailable), @"networkType": networkType};
        NSData *jsonData = [NSJSONSerialization dataWithJSONObject:info options:0 error:nil];
        NSString *jsonStr = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
        [strongSelf notifyEvent:strongSelf.networkEventCallback type:@"network" data:jsonStr ?: @""];
    });

    nw_path_monitor_start(self.pathMonitor);
}

- (void)doUnregisterNetwork {
    if (self.pathMonitor) {
        nw_path_monitor_cancel(self.pathMonitor);
        self.pathMonitor = nil;
    }
    self.networkEventCallback = nil;
}

#pragma mark - Lifecycle

- (void)destroy {
    dispatch_sync(self.timerQueue, ^{
        [self.timers enumerateKeysAndObjectsUsingBlock:^(NSString *key, dispatch_source_t timer, BOOL *stop) {
            dispatch_source_cancel(timer);
        }];
        [self.timers removeAllObjects];
    });
    [self.timerCallbacks removeAllObjects];
    [self doUnregisterNetwork];
    [self doDisconnectWithCode:1000 reason:@"Module destroyed"];
    self.wsEventCallback = nil;
}

- (void)dealloc {
    [self destroy];
}

@end
