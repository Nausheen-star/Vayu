package com.webserver;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;

public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    /**
     * Processes incoming HTTP requests, validating and responding based on predefined criteria.
     * @example
     * HttpRequestHandler handler = new HttpRequestHandler();
     * handler.channelRead0(ctx, request);
     * // If request is a valid GET with or without an "X-Data" header, processes and sends response.
     * @param {ChannelHandlerContext} ctx - The context of the current HTTP request, used for interaction with the channel.
     * @param {FullHttpRequest} request - The full HTTP request object containing details like headers and method.
     * @return {void} - This method does not return a value directly but sends a response through the channel context.
     * @description
     *   - Handles requests only if they have been successfully decoded.
     *   - Processes only GET requests; others result in METHOD_NOT_ALLOWED.
     *   - Checks for a custom "X-Data" header, modifying response if present.
     *   - Manages connection persistence via Keep-Alive headers.
     */
    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (!request.decoderResult().isSuccess()) {
            sendError(ctx, BAD_REQUEST);
            return;
        }

        // Only handle GET requests
        if (!GET.equals(request.method())) {
            sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }

        // Check if "X-Data" header exists
        String customData = request.headers().get("X-Data");
        String responseMessage = "Hello from Vayu Web Server!";

        if (customData != null && !customData.isEmpty()) {
            responseMessage = "Received header X-Data: " + customData;
        }

        // Create the response
        ByteBuf content = Unpooled.copiedBuffer(responseMessage, CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, OK, content);

        // Set headers for the response
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        HttpUtil.setContentLength(response, content.readableBytes());

        // Handle Keep-Alive connections
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (!keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        } else {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }

        // Send the response and clean up
        ChannelFuture flushPromise = ctx.writeAndFlush(response);
        if (!keepAlive) {
            flushPromise.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        if (ctx.channel().isActive()) {
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        ByteBuf content = Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

        HttpUtil.setContentLength(response, content.readableBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
