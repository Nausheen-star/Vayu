package com.webserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.util.logging.Level;
import java.util.logging.Logger;

public class VayuWebServer {

    private static final Logger LOGGER = Logger.getLogger(VayuWebServer.class.getName());
    private final int port;

    public VayuWebServer(int port) {
        this.port = port;
    }

    public static void main(String[] args) {
        try {
            new VayuWebServer(8080).start();
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Server interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fatal error: ", e);
        }
    }

    // Start the server
    public void start() throws InterruptedException {

        // Create two EventLoopGroups: one for accepting connections and one for handling them
        EventLoopGroup bossGroup = new NioEventLoopGroup(1); // Boss group handles connection requests
        EventLoopGroup workerGroup = new NioEventLoopGroup(); // Worker group handles the traffic

        try {
            // Create a server bootstrap for configuring and starting the server
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class) // Use non-blocking server socket channel
                    .childHandler(createChannelInitializer());

            // Bind server to the specified port and wait for it to start
            Channel channel = bootstrap.bind(port).sync().channel();
            LOGGER.info("Vayu Web Server started on port: " + port);

            // Wait until the server socket is closed
            channel.closeFuture().sync();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Server encountered an error", e);

        } finally {
            LOGGER.log(Level.INFO, "Shutting down server...");

            // Gracefully shut down both event loops
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private ChannelInitializer<SocketChannel> createChannelInitializer() {
        return new ChannelInitializer<>() {
            @Override
            protected void initChannel(SocketChannel channel) {
                ChannelPipeline pipeline = channel.pipeline();
                pipeline.addLast("http-codec", new HttpServerCodec()); //  HTTP Codec: Decodes requests and encodes responses
                // HTTP Object Aggregator: Aggregates multiple HTTP chunks into one full request/response
                pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
                pipeline.addLast(new ChunkedWriteHandler());
                pipeline.addLast("http-compressor", new HttpContentCompressor()); // Enable Gzip/Deflate compression
                pipeline.addLast("http-request-handler", new HttpRequestHandler());

            }
        };
    }
}