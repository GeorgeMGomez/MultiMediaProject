package multimedia_project2;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

import okhttp3.*;

public class VideoClientGUI extends JFrame {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private JComboBox<String> formatBox;
    private JComboBox<String> protocolBox;
    private JComboBox<String> videoBox;
    private JButton speedTestButton, refreshButton, streamButton;
    private JTextArea outputArea;
    private double speedMbps = 0;
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345; //Load balancer

    public VideoClientGUI() {
        setTitle("Video Streaming Client");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(600, 450);

        formatBox = new JComboBox<>(new String[]{".avi", ".mp4", ".mkv"});
        protocolBox = new JComboBox<>(new String[]{"TCP", "UDP", "RTP/UDP"});
        videoBox = new JComboBox<>();
        speedTestButton = new JButton("Speed Test");
        refreshButton = new JButton("Load Videos");
        streamButton = new JButton("Start Streaming");
        outputArea = new JTextArea(12, 50);
        outputArea.setEditable(false);

        JPanel panel = new JPanel();
        panel.add(new JLabel("Format:"));
        panel.add(formatBox);
        panel.add(new JLabel("Protocol:"));
        panel.add(protocolBox);
        panel.add(new JLabel("Video:"));
        panel.add(videoBox);
        panel.add(speedTestButton);
        panel.add(refreshButton);
        panel.add(streamButton);

        add(panel, BorderLayout.NORTH);
        add(new JScrollPane(outputArea), BorderLayout.CENTER);

        speedTestButton.addActionListener(e -> startSpeedTest());
        refreshButton.addActionListener(e -> fetchVideos());
        streamButton.addActionListener(e -> startStreaming());

        // Αυτόματο speed test και φόρτωση λίστας
        startSpeedTest();
        fetchVideos();
    }

    private void startSpeedTest() {
        speedTestButton.setEnabled(false);
        outputArea.append("Starting download speed test...\n");
        SwingWorker<Double, Void> worker = new SwingWorker<>() {
            @Override
            protected Double doInBackground() {
                return measureDownloadSpeed("http://ipv4.download.thinkbroadband.com/10MB.zip");
            }
            @Override
            protected void done() {
                try {
                    speedMbps = get();
                    outputArea.append(String.format("Download speed: %.2f Mbps\n", speedMbps));
                } catch (Exception ex) {
                    outputArea.append("Speed test failed.\n");
                }
                speedTestButton.setEnabled(true);
            }
        };
        worker.execute();
    }

    private void fetchVideos() {
        videoBox.removeAllItems();
        outputArea.append("Connecting to server to load video list...\n");
        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() {
                List<String> videos = new ArrayList<>();
                try (
                    Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
                ) {
                    in.readLine(); // "Hello from server"
                    out.println("Hi Server!");
                    in.readLine(); // "Echo: Hi Server!"

                    out.println(speedMbps > 0 ? speedMbps : 10.0);
                    String selectedFormat = (formatBox.getSelectedItem() != null) ? formatBox.getSelectedItem().toString() : ".mp4";
                    out.println(selectedFormat);

                    String line;
                    while ((line = in.readLine()) != null && !line.equals("END")) {
                        videos.add(line);
                        System.out.println(line);
                    }
                    if (line == null) {
                        //Ο server έκλεισε πρόωρα
                        SwingUtilities.invokeLater(() -> outputArea.append("Server closed the connection unexpectedly.\n"));
                        return videos;
                    }
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> outputArea.append("Failed to load video list!\n"));
                }
                return videos;
            }
            @Override
            protected void done() {
                try {
                    List<String> videos = get();
                    for (String v : videos) videoBox.addItem(v);
                    outputArea.append("Loaded " + videos.size() + " video(s).\n");
                } catch (Exception ex) {
                    outputArea.append("Could not get videos.\n");
                }
            }
        };
        worker.execute();
    }
    
    private void startStreaming() {
        if (videoBox.getSelectedItem() == null) {
            outputArea.append("Please select a video.\n");
            return;
        }
        streamButton.setEnabled(false);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try (
                    Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    DataInputStream dataIn = new DataInputStream(socket.getInputStream())
                ) {
                    socket.setSoTimeout(10000);
                    in.readLine(); // "Hello from server"
                    out.println("Hi Server!");
                    in.readLine(); // "Echo: Hi Server!"

                    out.println(speedMbps > 0 ? speedMbps : 10.0);
                    out.println(formatBox.getSelectedItem());

                    String line;
                    while ((line = in.readLine()) != null && !line.equals("END")) {
                        // consume video list
                    }

                    String file = videoBox.getSelectedItem().toString();
                    String protocol = protocolBox.getSelectedItem().toString();

                    out.println(file);
                    out.println(protocol);

                    String confirmation = in.readLine();
                    if (confirmation != null) {
                        SwingUtilities.invokeLater(() -> outputArea.append(confirmation + "\n"));
                    }

                    String endMarker = in.readLine();
                    if (endMarker == null || !endMarker.equals("END")) {
                        SwingUtilities.invokeLater(() -> outputArea.append("Protocol handshake incomplete\n"));
                        return null;
                    }

                    if (protocol.equalsIgnoreCase("RTP") || protocol.equalsIgnoreCase("RTP/UDP")) {
                        receiveSDPFile(dataIn, "stream.sdp");
                        SwingUtilities.invokeLater(() -> outputArea.append("Received SDP file.\n"));
                    }

                    // ----- THE CHANGE: track the process and wait for it -----
                    Process ffplayProc = launchFFplay(protocol, file);
                    if (ffplayProc != null) {
                        try {
                            ffplayProc.waitFor(); // Block until ffplay window closes
                        } catch (InterruptedException ex) {
                            // Ignore or handle
                        }
                    }
                    // Now, as the socket closes, the server kills its ffmpeg process.

                } catch (SocketTimeoutException e) {
                    SwingUtilities.invokeLater(() -> outputArea.append("Connection timeout. Please try again.\n"));
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> outputArea.append("Streaming error: " + e.getMessage() + "\n"));
                } finally {
                    SwingUtilities.invokeLater(() -> streamButton.setEnabled(true));
                }
                return null;
            }
        };
        worker.execute();
    }



    private void receiveSDPFile(DataInputStream in, String filePath) throws IOException {
        int size = in.readInt();
        byte[] buffer = new byte[size];
        in.readFully(buffer);
        Files.write(Paths.get(filePath), buffer);
    }

    private double measureDownloadSpeed(String testUrl) {
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
        Request request = new Request.Builder().url(testUrl).build();
        long start = System.currentTimeMillis();
        long bytesReadTotal = 0;
        try (Response response = client.newCall(request).execute()) {
            if (response.body() != null) {
                byte[] buffer = new byte[8192];
                InputStream in = response.body().byteStream();
                while (System.currentTimeMillis() - start < 5000) {
                    int n = in.read(buffer);
                    if (n == -1) break;
                    bytesReadTotal += n;
                }
                double mb = bytesReadTotal / (1024.0 * 1024.0);
                return (mb * 8) / 5.0;
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> outputArea.append("Speed test error: " + e.getMessage() + "\n"));
        }
        return 0.0;
    }
    
    public static Process launchFFplay(String protocol, String file) {
        try {
            List<String> command = new ArrayList<>();
            command.add("C:\\Users\\georg\\AppData\\Local\\Microsoft\\WinGet\\Packages\\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\\ffmpeg-7.1.1-full_build\\bin\\ffplay.exe");

            if (protocol.equalsIgnoreCase("RTP") || protocol.equalsIgnoreCase("RTP/UDP")) {
                command.add("-protocol_whitelist");
                command.add("file,udp,rtp");
                command.add("-i");
                command.add("stream.sdp");
            } else {
                command.add("-fflags");
                command.add("nobuffer");
                switch (protocol.toUpperCase()) {
                    case "TCP": command.add("tcp://localhost:5000"); break;
                    case "UDP": command.add("udp://localhost:5000"); break;
                    default: command.add("udp://localhost:5000");
                }
            }
            return new ProcessBuilder(command).inheritIO().start();
        } catch (IOException e) {
            // Optional: JOptionPane.showMessageDialog(null, "Could not start ffplay: " + e.getMessage());
            return null;
        }
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VideoClientGUI().setVisible(true));
    }
}
