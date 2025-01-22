package com.webserver;

import io.netty.handler.codec.http.*;


public class ResponseGenerator {

    // Generate an HTTP response based on the request
    public static FullHttpResponse generateHttpResponse(FullHttpRequest request) {
        FullHttpResponse response;
        try {
            response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    request.content().copy() // Echo back the request body (just for demonstration)
            );

            // Set the appropriate headers for the response
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        } catch (Exception e) {
            response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
            response.content().writeBytes("Internal Server Error".getBytes());
        }

        // TODO Compress response using Gzip/Deflate

        return response;
    }
}
