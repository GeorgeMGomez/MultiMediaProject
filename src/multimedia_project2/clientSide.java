package multimedia_project2;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import okhttp3.*;

public class clientSide {
    static Logger log = LogManager.getLogger(clientSide.class);
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345; //Load balancer

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in);
             Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
        		 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        	     PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        	     DataInputStream dataIn = new DataInputStream(socket.getInputStream())) {
        	
        	System.out.println("Connected: " + in.readLine());
            out.println("Hi Server!");
            System.out.println("Server replied: " + in.readLine());

            System.out.println("Starting download speed test...");
            double speedMbps = measureDownloadSpeed("http://ipv4.download.thinkbroadband.com/10MB.zip");
            System.out.printf("Estimated download speed: %.2f Mbps%n", speedMbps);

            System.out.print("Enter desired video format (.avi/.mp4/.mkv): ");
            String format = scanner.nextLine();
            out.println(speedMbps);
            out.println(format);

            System.out.println("Receiving available videos...");
            String line;
            List<String> videoOptions = new ArrayList<>();
            while (!(line = in.readLine()).equals("END")) {
                videoOptions.add(line);
                System.out.println(line);
            }

            System.out.print("Choose a video filename from above: ");
            String fileChoice = scanner.nextLine();

            System.out.print("Choose protocol (TCP, UDP, RTP/UDP) or leave blank: ");
            String protocol = scanner.nextLine();
            if (protocol.isEmpty()) {
                if (fileChoice.contains("240p")) protocol = "TCP";
                else if (fileChoice.contains("360p") || fileChoice.contains("480p")) protocol = "UDP";
                else protocol = "RTP/UDP";
            }

            out.println(fileChoice);
            out.println(protocol);
            
            String confirmation = in.readLine();  //π.χ. "Sending SDP file..."
            System.out.println(confirmation);
            //Αν είναι RTP, περιμένουμε stream.sdp
            if (protocol.equalsIgnoreCase("RTP") || protocol.equalsIgnoreCase("RTP/UDP")) {
                receiveSDPFile(dataIn, "stream.sdp");
                log.info("Received stream.sdp file from server");
            }
            
            try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
            launchFFplay(protocol, fileChoice);

        } catch (IOException e) {
            log.error("Client error: " + e.getMessage());
        }
    }

    private static void receiveSDPFile(DataInputStream dataIn, String filePath) throws IOException {
        int fileSize = dataIn.readInt();
        byte[] buffer = new byte[fileSize];
        dataIn.readFully(buffer);
        Files.write(Paths.get(filePath), buffer);
    }

    public static double measureDownloadSpeed(String testUrl) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder().url(testUrl).build();
        long startTime = System.currentTimeMillis();
        long bytesReadTotal = 0;

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                byte[] buffer = new byte[8192];
                InputStream input = response.body().byteStream();
                long timeLimit = 5000;

                while (System.currentTimeMillis() - startTime < timeLimit) {
                    int bytesRead = input.read(buffer);
                    if (bytesRead == -1) break;
                    bytesReadTotal += bytesRead;
                }

                double timeSeconds = timeLimit / 1000.0;
                double sizeMB = bytesReadTotal / (1024.0 * 1024.0);
                return (sizeMB * 8) / timeSeconds;
            } else {
                System.out.println("Download failed. HTTP Code: " + response.code());
            }
        } catch (IOException e) {
            System.out.println("Download error: " + e.getMessage());
        }
        return 0.0;
    }

    public static void launchFFplay(String protocol, String file) {
        try {
            List<String> command = new ArrayList<>();
            command.add("C:\\Users\\georg\\AppData\\Local\\Microsoft\\WinGet\\Packages\\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\\ffmpeg-7.1.1-full_build\\bin\\ffplay.exe");
            
            if (protocol.equalsIgnoreCase("RTP") || protocol.equalsIgnoreCase("RTP/UDP")) {
                //RTP mode: use .sdp
                command.add("-protocol_whitelist");
                command.add("file,udp,rtp");
                command.add("-i");
                command.add("stream.sdp");
            } else {
                //TCP or UDP
                command.add("-fflags");
                command.add("nobuffer");

                switch (protocol.toUpperCase()) {
                    case "TCP":
                        command.add("tcp://localhost:5000");
                        break;
                    case "UDP":
                        command.add("udp://localhost:5000");
                        break;
                    default:
                        log.warn("Unknown protocol. Defaulting to UDP.");
                        command.add("udp://localhost:5000");
                }
            }

            new ProcessBuilder(command).inheritIO().start();
            log.info("Video playback started using protocol: " + protocol);
        } catch (IOException e) {
            log.error("Could not start ffplay: " + e.getMessage());
        }
    }
}



