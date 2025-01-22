package com.webserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.logging.Level;
import java.util.logging.Logger;

public class VayuWebServer {
    private static final Logger LOGGER = Logger.getLogger(VayuWebServer.class.getName());
    private final int port;

    public VayuWebServer(int port) {
        this.port = port;
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

                bootstrap
                        .option(ChannelOption.SO_BACKLOG, 1024) // Set backlog for the server socket
                        .childOption(ChannelOption.SO_KEEPALIVE, true) // (transport layer) Enable TCP keep-alive for client connections. low-level TCP setting to manage idle TCP connections.
                        .childOption(ChannelOption.TCP_NODELAY, true)  // Disable Nagle's algorithm for better performance in small requests
                        .childOption(ChannelOption.SO_RCVBUF, 32 * 1024) // 32 KB Set socket receive buffer size
                        .childOption(ChannelOption.SO_SNDBUF, 32 * 1024) // 32 KB Set socket send buffer size
                        .childOption(ChannelOption.SO_LINGER, 0) // Disable socket lingering
                        .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000); // Set connection timeout (5 seconds)


                // Bind server to the specified port and wait for it to start
                ChannelFuture future = bootstrap.bind(port).sync();
                LOGGER.info("Vayu Web Server started on port: " + port);

                // Wait until the server socket is closed
                future.channel().closeFuture().sync();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Server encountered an error", e);

            } finally {
                LOGGER.log(Level.INFO, "Shutting down server...");

                // Gracefully shut down both event loops
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        }


    private ChannelInitializer<Channel> createChannelInitializer() {
        return new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel channel) {


                ChannelPipeline pipeline = channel.pipeline();

                // SSL/TLS handler
//              pipeline.addLast("ssl", new SslHandler());

                //  HTTP Codec: Decodes requests and encodes responses
                pipeline.addLast("http-codec", new HttpServerCodec());

                // HTTP Object Aggregator: Aggregates multiple HTTP chunks into one full request/response
                pipeline.addLast("aggregator", new HttpObjectAggregator(8192)); // 8 KB max message size

                // 4. IdleStateHandler: Handles read/write idle timeout
                pipeline.addLast("idle-state-handler", new IdleStateHandler(60, 30, 0)); // 60s read, 30s write timeout

                pipeline.addLast("http-compressor", new HttpContentCompressor()); // Enable Gzip/Deflate compression



                pipeline.addLast("http-request-handler", new HttpRequestHandler());

                // support for HTTP/1.1 Keep-Alive & other features
                pipeline.addLast("http-keep-alive", new HttpServerKeepAliveHandler()); // Application-level Keep-Alive support

//                pipeline.addLast("http-request-handler", requestHandlerFactory.createRequestHandler());
            }
        };
    }

    public static void main(String[] args) {
        try {
            new VayuWebServer(8080).start();
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Server interrupted", e);
            Thread.currentThread().interrupt();
        }
        catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fatal error: ",e);
        }
    }
    }