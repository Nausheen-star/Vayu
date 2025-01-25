//package com.webserver;
//
//import io.netty.buffer.ByteBuf;
//import io.netty.buffer.Unpooled;
//import io.netty.channel.*;
//import io.netty.handler.codec.http.*;
//import io.netty.handler.ssl.SslHandler;
//import io.netty.handler.stream.ChunkedFile;
//import io.netty.util.CharsetUtil;
//import io.netty.util.internal.SystemPropertyUtil;
//
//import javax.activation.MimetypesFileTypeMap;
//import java.io.File;
//import java.io.FileNotFoundException;
//import java.io.RandomAccessFile;
//import java.io.UnsupportedEncodingException;
//import java.net.URLDecoder;
//import java.text.SimpleDateFormat;
//import java.util.*;
//import java.util.regex.Pattern;
//
//import static io.netty.handler.codec.http.HttpMethod.GET;
//import static io.netty.handler.codec.http.HttpResponseStatus.*;
//import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
//import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
///**
// * 62   * A simple handler that serves incoming HTTP requests to send their respective
// * 63   * HTTP responses.  It also implements {@code 'If-Modified-Since'} header to
// * 64   * take advantage of browser cache, as described in
// * 65   * <a href="https://tools.ietf.org/html/rfc2616#section-14.25">RFC 2616</a>.
// * 66   *
// * 67   * <h3>How Browser Caching Works</h3>
// * 68   *
// * 69   * Web browser caching works with HTTP headers as illustrated by the following
// * 70   * sample:
// * 71   * <ol>
// * 72   * <li>Request #1 returns the content of {@code /file1.txt}.</li>
// * 73   * <li>Contents of {@code /file1.txt} is cached by the browser.</li>
// * 74   * <li>Request #2 for {@code /file1.txt} does not return the contents of the
// * 75   *     file again. Rather, a 304 Not Modified is returned. This tells the
// * 76   *     browser to use the contents stored in its cache.</li>
// * 77   * <li>The server knows the file has not been modified because the
// * 78   *     {@code If-Modified-Since} date is the same as the file's last
// * 79   *     modified date.</li>
// * 80   * </ol>
// * 81   *
// * 82   * <pre>
// * 83   * Request #1 Headers
// * 84   * ===================
// * 85   * GET /file1.txt HTTP/1.1
// * 86   *
// * 87   * Response #1 Headers
// * 88   * ===================
// * 89   * HTTP/1.1 200 OK
// * 90   * Date:               Tue, 01 Mar 2011 22:44:26 GMT
// * 91   * Last-Modified:      Wed, 30 Jun 2010 21:36:48 GMT
// * 92   * Expires:            Tue, 01 Mar 2012 22:44:26 GMT
// * 93   * Cache-Control:      private, max-age=31536000
// * 94   *
// * 95   * Request #2 Headers
// * 96   * ===================
// * 97   * GET /file1.txt HTTP/1.1
// * 98   * If-Modified-Since:  Wed, 30 Jun 2010 21:36:48 GMT
// * 99   *
// * 100  * Response #2 Headers
// * 101  * ===================
// * 102  * HTTP/1.1 304 Not Modified
// * 103  * Date:               Tue, 01 Mar 2011 22:44:28 GMT
// * 104  *
// * 105  * </pre>
// * 106
// */
//public class HttpStaticFileServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
//
//    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
//
//    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
//
//    public static final int HTTP_CACHE_SECONDS = 60;
//
//    private FullHttpRequest request;
//
//    @Override
//
//
//    public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
//        this.request = request;
//        if (!request.decoderResult().isSuccess()) {
//            sendError(ctx, BAD_REQUEST);
//            return;
//
//        }
//
//        if (!GET.equals(request.method())) {
//            sendError(ctx, METHOD_NOT_ALLOWED);
//            return;
//
//        }
//
//        final boolean keepAlive = HttpUtil.isKeepAlive(request);
//        final String uri = request.uri();
//        final String path = sanitizeUri(uri);
//        if (path == null) {
//            sendError(ctx, FORBIDDEN);
//            return;
//
//        }
//
//        File file = new File(path);
//        if (file.isHidden() || !file.exists()) {
//            sendError(ctx, NOT_FOUND);
//            return;
//
//        }
//
//        if (file.isDirectory()) {
//            if (uri.endsWith("/")) {
//                sendListing(ctx, file, uri);
//
//            } else {
//                sendRedirect(ctx, uri + '/');
//
//            }
//            return;
//
//        }
//
//        if (!file.isFile()) {
//            sendError(ctx, FORBIDDEN);
//            return;
//
//        }
//
//        // Cache Validation
//        String ifModifiedSince = request.headers().get(HttpHeaderNames.IF_MODIFIED_SINCE);
//        if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
//            SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
//            Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);
//
//            // Only compare up to the second because the datetime format we send to the client
//            // does not have milliseconds
//            long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
//            long fileLastModifiedSeconds = file.lastModified() / 1000;
//            if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
//                sendNotModified(ctx);
//                return;
//
//            }
//
//        }
//
//        RandomAccessFile raf;
//        try {
//            raf = new RandomAccessFile(file, "r");
//
//        } catch (FileNotFoundException ignore) {
//            sendError(ctx, NOT_FOUND);
//            return;
//
//        }
//        if (!keepAlive) {
//            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
//
//        } else if (request.protocolVersion().equals(HTTP_1_0)) {
//            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
//
//        }
//
//        // Write the initial line and the header.
//        ctx.write(response);
//
//        // Write the content.
//        ChannelFuture sendFileFuture;
//        ChannelFuture lastContentFuture;
//        if (ctx.pipeline().get(SslHandler.class) == null) {
//            sendFileFuture =
//                    ctx.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength), ctx.newProgressivePromise());
//            // Write the end marker.
//            lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
//
//        } else {
//            sendFileFuture =
//                    ctx.writeAndFlush(new HttpChunkedInput(new ChunkedFile(raf, 0, fileLength, 8192)),
//                            ctx.newProgressivePromise());
//            // HttpChunkedInput will write the end marker (LastHttpContent) for us.
//            lastContentFuture = sendFileFuture;
//
//        }
//
//        sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
//            @Override
//
//
//            public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
//                if (total < 0) { // total unknown
//                    System.err.println(future.channel() + " Transfer progress: " + progress);
//
//                } else {
//                    System.err.println(future.channel() + " Transfer progress: " + progress + " / " + total);
//
//                }
//
//            }
//
//            @Override
//
//
//            public void operationComplete(ChannelProgressiveFuture future) {
//                System.err.println(future.channel() + " Transfer complete.");
//
//            }
//
//        });
//
//        // Decide whether to close the connection or not.
//        if (!keepAlive) {
//            // Close the connection when the whole content is written out.
//            lastContentFuture.addListener(ChannelFutureListener.CLOSE);
//
//        }
//
//    }
//
//
//    @Override
//    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
//         cause.printStackTrace();
//         if (ctx.channel().isActive()) {
//             sendError(ctx, INTERNAL_SERVER_ERROR);
//
//        }
//
//    }
//
//    private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");
//
//
//    private static String sanitizeUri(String uri) {
//                 // Decode the path.
//         try {
//             uri = URLDecoder.decode(uri, "UTF-8");
//
//        } catch (UnsupportedEncodingException e) {
//             throw new Error(e);
//
//        }
//
//         if (uri.isEmpty() || uri.charAt(0) != '/') {
//             return null;
//
//        }
//
//                 // Convert file separators.
//         uri = uri.replace('/', File.separatorChar);
//
//                 // Simplistic dumb security check.
//                 // You will have to do something serious in the production environment.
//         if (uri.contains(File.separator + '.') ||
//                 uri.contains('.' + File.separator) ||
//                 uri.charAt(0) == '.' || uri.charAt(uri.length() - 1) == '.' ||
//                 INSECURE_URI.matcher(uri).matches()){
//             return null;
//
//        }
//
//                 // Convert to absolute path.
//         return SystemPropertyUtil.get("user.dir") + File.separator + uri;
//
//    }
//
//    private static final Pattern ALLOWED_FILE_NAME = Pattern.compile("[^-\\._]?[^<>&\\\"]*");
//
//
//    private void sendListing(ChannelHandlerContext ctx, File dir, String dirPath) {
//         StringBuilder buf = new StringBuilder()
//         .append("<!DOCTYPE html>\r\n")
//         .append("<html><head><meta charset='utf-8' /><title>")
//         .append("Listing of: ")
//         .append(dirPath)
//         .append("</title></head><body>\r\n")
//
//         .append("<h3>Listing of: ")
//         .append(dirPath)
//         .append("</h3>\r\n")
//
//         .append("<ul>")
//         .append("<li><a href=\"../\">..</a></li>\r\n");
//
//         File[] files = dir.listFiles();
//         if (files != null) {
//             for (File f : files) {
//                 if (f.isHidden() || !f.canRead()) {
//                     continue;
//
//                }
//
//                 String name = f.getName();
//                 if (!ALLOWED_FILE_NAME.matcher(name).matches()) {
//                  continue;
//                }
//
//                 buf.append("<li><a href=\"")
//                 .append(name)
//                 .append("\">")
//                 .append(name)
//                 .append("</a></li>\r\n");
//
//            }
//
//        }
//
//         buf.append("</ul></body></html>\r\n");
//
//         ByteBuf buffer = ctx.alloc().buffer(buf.length());
//         buffer.writeCharSequence(buf.toString(), CharsetUtil.UTF_8);
//
//         FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, buffer);
//         response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
//
//         sendAndCleanupConnection(ctx, response);
//
//    }
//
//
//    private void sendRedirect(ChannelHandlerContext ctx, String newUri) {
//         FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, FOUND, Unpooled.EMPTY_BUFFER);
//         response.headers().set(HttpHeaderNames.LOCATION, newUri);
//
//         sendAndCleanupConnection(ctx, response);
//
//    }
//
//
//    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
//        FullHttpResponse response = new DefaultFullHttpResponse(
//            HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
//        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
//
//        sendAndCleanupConnection(ctx, response);
//
//    }
//
//    /**
// 336      * When file timestamp is the same as what the browser is sending up, send a "304 Not Modified"
// 337      *
// 338      * @param ctx
// 339      *            Context
// 340      */
//
//
//    private void sendNotModified(ChannelHandlerContext ctx) {
//         FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_MODIFIED, Unpooled.EMPTY_BUFFER);
//         setDateHeader(response);
//
//         sendAndCleanupConnection(ctx, response);
//
//    }
//
//    /**
//       * If Keep-Alive is disabled, attaches "Connection: close" header to the response
//       * and closes the connection after the response being sent.
//       */
//
//
//    private void sendAndCleanupConnection(ChannelHandlerContext ctx, FullHttpResponse response) {
//         final FullHttpRequest request = this.request;
//         final boolean keepAlive = HttpUtil.isKeepAlive(request);
//         HttpUtil.setContentLength(response, response.content().readableBytes());
//         if (!keepAlive) {
//                         // We're going to close the connection as soon as the response is sent,
//                         // so we should also make it clear for the client.
//             response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
//
//        } else if (request.protocolVersion().equals(HTTP_1_0)) {
//            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
//
//        }
//
//         ChannelFuture flushPromise = ctx.writeAndFlush(response);
//
//         if (!keepAlive) {
//                         // Close the connection as soon as the response is sent.
//             flushPromise.addListener(ChannelFutureListener.CLOSE);
//
//        }
//
//    }
//     /**
//      * Sets the Date header for the HTTP response
//      *
//      * @param response
//      *            HTTP response
//      */
//
//
//    private static void setDateHeader(FullHttpResponse response) {
//         SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
//         dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));
//
//         Calendar time = new GregorianCalendar();
//         response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));
//
//    }
//
//    /**
//       * Sets the Date and Cache headers for the HTTP Response
//       *
//       * @param response
//       *            HTTP response
//       * @param fileToCache
//       *            file to extract content type
//       */
//
//
//    private static void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
//         SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
//         dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));
//
//                 // Date header
//         Calendar time = new GregorianCalendar();
//         response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));
//
//                 // Add cache headers
//         time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
//         response.headers().set(HttpHeaderNames.EXPIRES, dateFormatter.format(time.getTime()));
//         response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
//         response.headers().set(
//             HttpHeaderNames.LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));
//
//    }
//
//     /**
//       * Sets the content type header for the HTTP Response
//       *
//       * @param response
//       *            HTTP response
//       * @param file
//       *            file to extract content type
//       */
//
//
//    private static void setContentTypeHeader(HttpResponse response, File file) {
//         MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
//         response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
//
//    }
//}
