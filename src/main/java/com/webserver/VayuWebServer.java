package com.webserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;

public class VayuWebServer {

        private final int port;

    public VayuWebServer(int port) throws Exception {
        this.port = port;
    }

        // Start the server
        public void start() throws InterruptedException {

            // Create two EventLoopGroups: one for accepting connections and one for handling them
            EventLoopGroup bossGroup = new NioEventLoopGroup(1); // Boss group handles connection requests
            EventLoopGroup workerGroup = new NioEventLoopGroup(); // Worker group handles the traffic

            try {
                // Create a server bootstrap for configuring and starting the server
                ServerBootstrap bootstrap = new ServerBootstrap();
                bootstrap.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class) // Use non-blocking server socket channel
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ChannelPipeline pipeline = ch.pipeline();

                                // Add HTTP codec, aggregator, and custom handler
                                pipeline.addLast(new HttpServerCodec());
                                pipeline.addLast(new HttpObjectAggregator(1048576)); // Aggregate HTTP messages
                                pipeline.addLast(new HttpRequestHandler()); // Custom HTTP request handler
                            }
                        })
                        .option(ChannelOption.SO_BACKLOG, 128) // Set backlog for the server socket
                        .childOption(ChannelOption.SO_KEEPALIVE, true); // Enable keep-alive for client connections

                // Bind server to the specified port and wait for it to start
                ChannelFuture future = bootstrap.bind(port).sync();
                System.out.println("Vayu server started on port: " + port);

                // Wait until the server socket is closed
                future.channel().closeFuture().sync();
            } finally {

                // Gracefully shut down both event loops
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        }

        public static void main(String[] args) {
            try {
                new VayuWebServer(8080).start();

            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }