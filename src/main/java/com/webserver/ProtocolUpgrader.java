package com.webserver;

import io.netty.channel.ChannelPipeline;

// For future protocol support (HTTP/2, WebSockets, etc.).
public class ProtocolUpgrader {

    public static void upgradeToHttp2(ChannelPipeline pipeline) {
        // Implement protocol upgrade logic for HTTP/2 here
        // Add necessary handlers like Http2FrameCodec, Http2ConnectionHandler, etc.
    }

    public static void upgradeToWebSockets(ChannelPipeline pipeline) {
        // Implement WebSocket upgrade logic here
    }
}
