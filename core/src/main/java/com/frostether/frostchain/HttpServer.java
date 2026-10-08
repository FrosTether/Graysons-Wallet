package com.frostether.frostchain;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class HttpServer {
    public static final int MAX_BODY = 2097152;
    private Thread acceptor;
    private final Handler handler;
    private ExecutorService pool;
    private volatile boolean running;
    private ServerSocket server;

    public interface Handler {
        Response handle(Request request) throws Exception;
    }

    public static final class Request {
        public String method;
        public String path;
        public String remote;
        public final Map<String, String> query = new HashMap();
        public final Map<String, String> headers = new HashMap();
        public byte[] body = new byte[0];

        public String bodyText() {
            return new String(this.body, Bytes.UTF8);
        }

        public String q(String str, String str2) {
            String str3 = this.query.get(str);
            return str3 == null ? str2 : str3;
        }
    }

    public static final class Response {
        public int status = 200;
        public String type = "application/json; charset=utf-8";
        public byte[] body = new byte[0];
        public final Map<String, String> headers = new HashMap();

        public static Response json(Object obj) {
            Response response = new Response();
            response.body = Bytes.utf8(Json.write(obj));
            return response;
        }

        public static Response error(int i, String str) {
            Response json = json(Json.o("error", str));
            json.status = i;
            return json;
        }
    }

    public HttpServer(Handler handler) {
        this.handler = handler;
    }

    public int start(String str, int i) throws IOException {
        this.server = new ServerSocket();
        this.server.setReuseAddress(true);
        this.server.bind(new InetSocketAddress(str == null ? null : InetAddress.getByName(str), i), 50);
        this.pool = new ThreadPoolExecutor(2, 12, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue(64), new RejectedExecutionHandler() {
            @Override // java.util.concurrent.RejectedExecutionHandler
            public void rejectedExecution(Runnable runnable, ThreadPoolExecutor threadPoolExecutor) {
                if (runnable instanceof Conn) {
                    ((Conn) runnable).close();
                }
            }
        });
        this.running = true;
        this.acceptor = new Thread(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                while (HttpServer.this.running) {
                    try {
                        HttpServer.this.pool.execute(HttpServer.this.new Conn(HttpServer.this.server.accept()));
                    } catch (Throwable th) {
                        if (!HttpServer.this.running) {
                            return;
                        }
                    }
                }
            }
        }, "http-accept-" + i);
        this.acceptor.setDaemon(true);
        this.acceptor.start();
        return this.server.getLocalPort();
    }

    public void stop() {
        this.running = false;
        try {
            if (this.server != null) {
                this.server.close();
            }
        } catch (IOException e) {
        }
        if (this.pool != null) {
            this.pool.shutdownNow();
        }
    }

    private final class Conn implements Runnable {
        final Socket s;

        Conn(Socket socket) {
            this.s = socket;
        }

        @Override // java.lang.Runnable
        public void run() {
            HttpServer.this.serve(this.s);
        }

        void close() {
            try {
                this.s.close();
            } catch (IOException e) {
            }
        }
    }

    // Rebuilt from the 0.3.0 bytecode, which swallows any error from one connection.
    private void serve(Socket socket) {
        try {
            serveOrThrow(socket);
        } catch (Throwable ignored) {
            // A bad or dropped request only ends its own connection.
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }

    private void serveOrThrow(Socket socket) throws IOException {
        Response error;
        int read;
        socket.setSoTimeout(15000);
        InputStream inputStream = socket.getInputStream();
        Request request = new Request();
        request.remote = socket.getInetAddress().getHostAddress();
        String readLine = readLine(inputStream);
        if (readLine != null) {
            String[] split = readLine.split(" ");
            if (split.length >= 2) {
                request.method = split[0].toUpperCase(Locale.ROOT);
                String str = split[1];
                int indexOf = str.indexOf(63);
                request.path = indexOf >= 0 ? str.substring(0, indexOf) : str;
                if (indexOf >= 0) {
                    try {
                        for (String str2 : str.substring(indexOf + 1).split("&")) {
                            if (!str2.isEmpty()) {
                                int indexOf2 = str2.indexOf(61);
                                request.query.put(URLDecoder.decode(indexOf2 >= 0 ? str2.substring(0, indexOf2) : str2, "UTF-8"), URLDecoder.decode(indexOf2 >= 0 ? str2.substring(indexOf2 + 1) : "", "UTF-8"));
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        write(socket, Response.error(400, "bad query string"));
                        return;
                    }
                }
                int i = 0;
                while (true) {
                    String readLine2 = readLine(inputStream);
                    if (readLine2 == null || readLine2.isEmpty()) {
                        break;
                    }
                    i += readLine2.length();
                    if (i <= 32768) {
                        int indexOf3 = readLine2.indexOf(58);
                        if (indexOf3 > 0) {
                            request.headers.put(readLine2.substring(0, indexOf3).trim().toLowerCase(Locale.ROOT), readLine2.substring(indexOf3 + 1).trim());
                        }
                    } else {
                        write(socket, Response.error(431, "headers too large"));
                        return;
                    }
                }
                String str3 = request.headers.get("content-length");
                if (str3 != null) {
                    try {
                        long parseLong = Long.parseLong(str3.trim());
                        if (parseLong >= 0 && parseLong <= 2097152) {
                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream((int) Math.min(parseLong, 65536L));
                            byte[] bArr = new byte[16384];
                            while (parseLong > 0 && (read = inputStream.read(bArr, 0, (int) Math.min(bArr.length, parseLong))) >= 0) {
                                byteArrayOutputStream.write(bArr, 0, read);
                                parseLong -= read;
                            }
                            request.body = byteArrayOutputStream.toByteArray();
                        } else {
                            write(socket, Response.error(413, "body too large"));
                            return;
                        }
                    } catch (NumberFormatException e2) {
                        write(socket, Response.error(400, "bad length"));
                        return;
                    }
                }
                try {
                    error = this.handler.handle(request);
                    if (error == null) {
                        error = Response.error(404, "not found");
                    }
                } catch (IllegalArgumentException e3) {
                    error = Response.error(400, e3.getMessage());
                } catch (Exception e4) {
                    Log.w("http", request.path + ": " + e4);
                    error = Response.error(500, "internal error");
                }
                write(socket, error);
                return;
            }
            write(socket, Response.error(400, "bad request"));
        }
    }

    private static String readLine(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        do {
            int read = inputStream.read();
            if (read >= 0 && read != 10) {
                if (read != 13) {
                    byteArrayOutputStream.write(read);
                }
            } else {
                if (read >= 0 || byteArrayOutputStream.size() != 0) {
                    return new String(byteArrayOutputStream.toByteArray(), Bytes.UTF8);
                }
                return null;
            }
        } while (byteArrayOutputStream.size() <= 16384);
        throw new IOException("line too long");
    }

    private static void write(Socket socket, Response response) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(response.status).append(response.status == 200 ? " OK" : " Error").append("\r\n");
        sb.append("Content-Type: ").append(response.type).append("\r\n");
        sb.append("Content-Length: ").append(response.body.length).append("\r\n");
        sb.append("Connection: close\r\n");
        sb.append("X-Content-Type-Options: nosniff\r\n");
        for (Map.Entry<String, String> entry : response.headers.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }
        sb.append("\r\n");
        outputStream.write(Bytes.utf8(sb.toString()));
        outputStream.write(response.body);
        outputStream.flush();
    }
}
