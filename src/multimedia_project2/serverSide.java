package multimedia_project2;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.builder.FFmpegOutputBuilder;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;

public class serverSide {
	//Λειτουργία των logs στη κονσόλα
	static Logger log = LogManager.getLogger(serverSide.class);
	//Port του σέρβερ
	private static final int SERVER_PORT = 12345;
	//Λίστα με τα διαθέσιμα βίντεο
	private static Map<String, List<String>> availableVideos = new HashMap<>();
	
	public static void main(String[] args) {
		processVideos();
		loadAvailableVideos();
		startServer();
	}
	
	//Βρίσκει και επιστρέφει το αρχικό βίντεο με τη μεγαλύτερη ανάλυση για κάθε διαφορετικό όνομα video
	private static Map<String, String> findInputFile(String inputDir) {
	    Map<String, String> inputFiles = new HashMap<>();
	    Map<String, Integer> resolutionOrder = Map.of(
	        "240p", 1, "360p", 2, "480p", 3, "720p", 4, "1080p", 5
	    );
	    
	    //dir περιέχει όλα τα βίντεο
	    File dir = new File(inputDir);
	    
	    for (File file : dir.listFiles()) {
	        String name = file.getName();
	        //Αγνοεί βίντεο που δεν έχουν το όνομα με στύλ "όνομα-720p.mp4/avi/mkv"
	        if (!name.matches(".+-\\d{3,4}p\\.(avi|mp4|mkv)")) continue;
	        
	        //Χωρίζει το όνομα του αρχείου σε κωμμάτια
	        //π.χ Panasonic-720.mp4 -> parts[0] = "Panasonic", parts[1] = "720p", parts[2] = "mp4"
	        String[] parts = name.split("-|\\.");
	        
	        String baseName = parts[0];
	        String resolution = parts[1];
	        
	        //Αγνοεί το αρχείο αν δεν έχει ένα από τα διαθέσιμα resolutions
	        if (!resolutionOrder.containsKey(resolution)) continue;
	        
	        //Εδώ βρίσκει το αρχέιο με το μεγαλύτερο resolution
	        if (!inputFiles.containsKey(baseName) ||
	            resolutionOrder.get(resolution) > resolutionOrder.get(inputFiles.get(baseName).replaceAll(".*-(\\d{3,4}p).*", "$1"))) {
	            inputFiles.put(baseName, file.getName());
	        }
	    }
	    return inputFiles;
	}

	
	//Συγκρίνει την ανάλυση βίντεο για να δει ποια είναι μεγαλύτερη
	private static int compareResolutions(String res1, String res2) {
	    Map<String, Integer> resolutionOrder = new HashMap<>();
	    resolutionOrder.put("240p", 1);
	    resolutionOrder.put("360p", 2);
	    resolutionOrder.put("480p", 3);
	    resolutionOrder.put("720p", 4);
	    resolutionOrder.put("1080p", 5);
	    
	    return Integer.compare(resolutionOrder.get(res1), resolutionOrder.get(res2));
	}
	
	//Επεξεργασία video resolutions 
	private static void processVideos() {
		//Τα διαθέσιμα resolutions που μπορεί να έχει ένα βίντεο
		Map<String, String> resolutions = new HashMap<>();
		resolutions.put("1080p", "scale=-2:1080");
		resolutions.put("720p", "scale=-2:720");
		resolutions.put("480p", "scale=-2:480");
		resolutions.put("360p", "scale=-2:360");
		resolutions.put("240p", "scale=-2:240");
		
		//Προσθέτουμε τη διεύθυνση όπου ο κώδικας θα πάρει τα βίντεο και θα τα επιστρέψει με καινούρια resolutions στο φάκελο videos
		String inputDir = System.getProperty("user.dir") + "/videos/";
		String outDir = System.getProperty("user.dir") + "/videos/";
		FFmpeg ffmpeg = null;
		FFprobe ffprobe = null;
		
		try {
			//Εδώ βάλε τη διέυθυνση όπου έχεις το ffmpeg.exe και ffprobe.exe
			log.debug("Initialising FFMpegClient");
			ffmpeg = new FFmpeg("C:\\Users\\georg\\AppData\\Local\\Microsoft\\WinGet\\Packages\\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\\ffmpeg-7.1.1-full_build\\bin\\ffmpeg.exe");
			ffprobe = new FFprobe("C:\\Users\\georg\\AppData\\Local\\Microsoft\\WinGet\\Packages\\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\\ffmpeg-7.1.1-full_build\\bin\\ffprobe.exe");
			FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
			
			//Επιστρέφει μια λίστα βιντεο με τις μεγαλύτερες αναλύσεις τους
			Map<String, String> inputFiles = findInputFile(inputDir);
			
			//Επεξεργασία του βίντεο σε κάθε format και σε χαμηλώτερη ανάλυση που λείπει
			for (Map.Entry<String, String> inputEntry : inputFiles.entrySet()) {
				String baseName = inputEntry.getKey(); 
			    String fileName = inputEntry.getValue();
			 
			    String inputResolution = fileName.replaceAll(".*-(\\d+p).*", "$1");
			    log.info("Processing {} (resolution: {})", fileName, inputResolution);
			    
				for (String format : new String[]{"avi", "mp4", "mkv"}) { 
					for(Map.Entry<String, String> entry : resolutions.entrySet()) {
						String resolutionName = entry.getKey();
						String outputFile = outDir + baseName + "-" + resolutionName + "." + format;
						
						//Αν υπάρχει το βίντεο ήδη
						if (new File(outputFile).exists()) {
						    log.info("Skipping {} - already exists", outputFile);
						    continue;
						}
						
						//Αν η ανάλυση είναι μεγαλύτερη από τη μεγαλύτερη ανάλυση του βίντεο 
					    if (compareResolutions(resolutionName, inputResolution) > 0) {
					    	log.info("Skipping {} {} - higher than input", format, resolutionName);
					        continue;
					    }
						
//						if (new File(outDir + outputFile).exists()) {
//							//Process file
//							log.debug("Skipping " + baseName + resolutionName + "." + format + " file already exists");
//							continue;
//						}
						
						//Επεξεργασία νέου αρχείου
						log.debug("Processing " + baseName + resolutionName + "." + format);
							
						FFmpegOutputBuilder builder = new FFmpegBuilder()
				                .setInput(inputDir + fileName)
				                .addOutput(outputFile)
					            	.setVideoFilter(entry.getValue())
					            	.setFormat(format);
						
						switch (format) {
						    case "avi":
						        builder.setFormat("avi")
						              .setVideoCodec("mpeg4")
						              .setAudioCodec("mp3");
						        break;
						    case "mp4":
						        builder.setFormat("mp4")
						              .setVideoCodec("libx264")
						              .setAudioCodec("aac");
						        break;
						    case "mkv":
						        builder.setFormat("matroska")
						              .setVideoCodec("libx265")
						              .setAudioCodec("aac");
						        break;
						}
						executor.createJob(builder.done()).run();
					}
			}
		}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//Load Balancer όριο
	//Μέγιστο αριθμό clients
	private static final int MAX_CLIENTS = 10; 
	private static final ExecutorService threadPool = Executors.newFixedThreadPool(MAX_CLIENTS);
	
	//Έναρξη server
	public static void startServer() {
	    try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
	        log.info("Streaming Server started on port " + SERVER_PORT);

	        // Shutdown Hook για καθαρό τερματισμό
	        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
	            log.info("Shutting down thread pool...");
	            threadPool.shutdownNow();
	        }));

	        while (!Thread.currentThread().isInterrupted()) {
	            Socket clientSocket = serverSocket.accept();
	            threadPool.execute(() -> handleClient(clientSocket));  // Χρήση thread pool
	        }
	    } catch (IOException e) {
	        log.error("Server error: " + e.getMessage());
	    } finally {
	    	//Καθαρισμός όταν τερματίσει ο server
	        threadPool.shutdown(); 
	    }
	}
	
    //Βάζει τα διαθέσιμα βίντεο σε μια λίστα
    private static void loadAvailableVideos() {
        File dir = new File(System.getProperty("user.dir") + "/videos/");
        File[] files = dir.listFiles();

        if (files != null) {
            for (File file : files) {
                String[] parts = file.getName().split("-|\\."); 

                if (parts.length == 3) {
                    String name = parts[0];
                    String resolution = parts[1];
                    String format = parts[2];
                    
                    /*
                     * "Jelly_Fish": ["720p.mp4", "720p.mkv", ...., "240p.avi"],
                     * 
                     */
                    availableVideos.computeIfAbsent(name, k -> new ArrayList<>())
                                 .add(resolution + "." + format);
                }
            }
        }
        log.info("Loaded videos: " + availableVideos);
    }
    
    //
    private static void handleClient(Socket clientSocket) {
        Process ffmpegProcess = null; 

        try (clientSocket;
             BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true); 
        ) {
            out.println("Hello from server " + SERVER_PORT);
            out.flush();
            String s = in.readLine();
            System.out.println("Received: " + s);
            out.println("Echo: " + s);
            out.flush();
            
            double speedMbps = Double.parseDouble(in.readLine());
            String format = in.readLine();
            
            List<String> filteredList = getCompatibleVideos(speedMbps, format);
            for (String video : filteredList) out.println(video);
            out.println("END");
            out.flush();

            String selectedFile = in.readLine();
            String protocol = in.readLine();
            
            if (selectedFile == null || protocol == null) {
                return;
            }
            
            if (protocol.equals("auto")) {
                protocol = selectProtocolFromResolution(selectedFile);
            }
            
            out.println("Streaming started with protocol: " + protocol);
            out.flush();
            out.println("END");
            out.flush();
            
            if (protocol.equalsIgnoreCase("RTP") || protocol.equalsIgnoreCase("RTP/UDP")) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } // μικρή αναμονή για να φτιαχτεί το stream.sdp
                sendSDPFile(clientSocket.getOutputStream(), "stream.sdp");
            }
            
            // --- CHANGED: Keep the process, don't just start it
            ffmpegProcess = startStreaming(selectedFile, protocol);

            try {
                while (in.readLine() != null) {
                    //Optionally log/print, but likely nothing
                }
            } catch (IOException e) {
                //This is expected when the client closes connection!
            }

        } catch (IOException e) {
            log.error("Client handling error: " + e.getMessage());
        } finally {
            // ---- KILL FFMPEG ----
            if (ffmpegProcess != null && ffmpegProcess.isAlive()) {
                ffmpegProcess.destroy();
                log.info("FFmpeg process destroyed for client.");
            }
            try {
                clientSocket.close();
            } catch (IOException e) {
                log.error("Socket close error: " + e.getMessage());
            }
        }
    }

    
    //Gets the compatible videos according to the download test of the client
    private static List<String> getCompatibleVideos(double speedMbps, String format) {
    	//Recommended values according to the project table
        Map<String, Double> resolutionBitrates = Map.of(
            "240p", 0.4, "360p", 0.75, "480p", 1.0,
            "720p", 2.5, "1080p", 4.5
        );

        List<String> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : availableVideos.entrySet()) {
            String title = entry.getKey();
            for (String version : entry.getValue()) {
                if (version.endsWith(format)) {
                    String res = version.split("\\.")[0];
                    if (resolutionBitrates.getOrDefault(res, Double.MAX_VALUE) <= speedMbps) {
                        result.add(title + "-" + version);
                    }
                }
            }
        }
        return result;
    }
    
    //Default protocol
    private static String selectProtocolFromResolution(String filename) {
        if (filename.contains("240p")) return "TCP";
        if (filename.contains("360p") || filename.contains("480p")) return "UDP";
        return "RTP/UDP";
    }
    
    //Starting streaming (Process builder)
    private static Process startStreaming(String filename, String protocol) {
        try {
            String filePath = System.getProperty("user.dir") + "/videos/" + filename;
            List<String> command = new ArrayList<>();
            command.add("C:\\Users\\georg\\AppData\\Local\\Microsoft\\WinGet\\Packages\\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\\ffmpeg-7.1.1-full_build\\bin\\ffmpeg.exe");
            command.add("-re");
            command.add("-i");
            command.add(filePath);

            switch (protocol) {
                case "TCP":
                    command.add("-f"); command.add("mpegts");
                    command.add("tcp://localhost:5000?listen=1");
                    break;
                case "UDP":
                    command.add("-f"); command.add("mpegts");
                    command.add("udp://localhost:5000");
                    break;
                case "RTP/UDP":
                    if (filename.endsWith(".avi")) {
                        log.error("AVI format is not compatible with RTP. Use MP4 or MKV.");
                        return null;
                    }
                    command.add("-an");
                    command.add("-vcodec");
                    command.add("libx264");
                    command.add("-f");
                    command.add("rtp");
                    command.add("-sdp_file");
                    command.add("stream.sdp");
                    command.add("rtp://localhost:5004");
                    break;
            }

            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

            log.info("Streaming started: " + filename + " via " + protocol);
            return process;

        } catch (IOException e) {
            log.error("Streaming failed: " + e.getMessage());
            return null;
        }
    }

    
    //RTP
    private static void sendSDPFile(OutputStream output, String filePath) throws IOException {
        byte[] data = Files.readAllBytes(Paths.get(filePath));
        DataOutputStream dataOut = new DataOutputStream(output);
        dataOut.writeInt(data.length);
        dataOut.write(data);
        dataOut.flush();
    }
}
