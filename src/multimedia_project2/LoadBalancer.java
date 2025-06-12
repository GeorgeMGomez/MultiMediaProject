package multimedia_project2;

import java.io.*;
import java.net.*;
import java.util.*;

public class LoadBalancer {
    private static final int LB_PORT = 8000; // Ο Load Balancer ακούει εδώ
    private static final List<InetSocketAddress> SERVERS = Arrays.asList(
        new InetSocketAddress("localhost", 12345),
        new InetSocketAddress("localhost", 12346)
    );
    private static int serverIndex = 0;

    public static void main(String[] args) throws IOException {
        try (ServerSocket lbSocket = new ServerSocket(LB_PORT)) {
			System.out.println("Load Balancer running on port " + LB_PORT);

			while (true) {
			    Socket client = lbSocket.accept();
			    InetSocketAddress serverAddr = chooseServer();
			    new Thread(() -> proxy(client, serverAddr)).start();
			}
		}
    }

    private static synchronized InetSocketAddress chooseServer() {
        InetSocketAddress s = SERVERS.get(serverIndex);
        serverIndex = (serverIndex + 1) % SERVERS.size();
        return s;
    }

    private static void proxy(Socket client, InetSocketAddress serverAddr) {
        try (
            Socket server = new Socket(serverAddr.getHostName(), serverAddr.getPort());
            InputStream clientIn = client.getInputStream();
            OutputStream clientOut = client.getOutputStream();
            InputStream serverIn = server.getInputStream();
            OutputStream serverOut = server.getOutputStream();
        ) {
            Thread t1 = new Thread(() -> {
                forward(clientIn, serverOut);
                try { server.shutdownOutput(); } catch (IOException ignored) {}
            });
            Thread t2 = new Thread(() -> {
                forward(serverIn, clientOut);
                try { client.shutdownOutput(); } catch (IOException ignored) {}
            });
            t1.start(); t2.start();
            t1.join(); t2.join();
        } catch (Exception e) {
            System.out.println("Proxy error: " + e.getMessage());
        } finally {
            try { client.close(); } catch (Exception e) {}
        }
    }

    private static void forward(InputStream in, OutputStream out) {
        try {
            byte[] buffer = new byte[4096];
            int bytes;
            while ((bytes = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytes);
                out.flush();
            }
        } catch (IOException e) {}
    }
}

