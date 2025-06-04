package newMultimediaLab;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.builder.FFmpegOutputBuilder;

import java.io.BufferedReader;
import java.io.File;

public class serverSide {
	static Logger log = LogManager.getLogger(serverSide.class);
	private static final int SERVER_PORT = 1234;
	private static Map<String, List<String>> availableVideos = new HashMap<>();
	
	public static void main(String[] args) {
		//Begins the FFMPEG processing
		processVideos();
		
		//Start the Streaming Server in a separate thread
		System.out.println("Running Server");
        new Thread(() -> startStreamingServer()).start();
	}
	
	//Finds the file videos and puts them in a list
	private static Map<String, String> findInputFile(String inputDir) {
		Map<String, String> inputFiles = new HashMap<>();
		String[] resolutions = {"1080p", "720p", "480p", "360p", "240p"};
		String[] formats = {"avi", "mp4", "mkv"};
		
		for(String res : resolutions) {
			for(String format : formats) {
				File dir = new File(inputDir);
				
				for (File file : dir.listFiles((d, name) -> 
                	name.matches(".+-" + res + "\\." + format))) {
					String baseName = file.getName().split("-")[0];
               		inputFiles.put(baseName, file.getName());
				}
			}
		}
		return inputFiles;
	}
	
	//Helper method to compare resolutions
	private static int compareResolutions(String res1, String res2) {
	    Map<String, Integer> resolutionOrder = new HashMap<>();
	    resolutionOrder.put("240p", 1);
	    resolutionOrder.put("360p", 2);
	    resolutionOrder.put("480p", 3);
	    resolutionOrder.put("720p", 4);
	    resolutionOrder.put("1080p", 5);
	    
	    return Integer.compare(resolutionOrder.get(res1), resolutionOrder.get(res2));
	}
	
	private static void processVideos() {
		//Available resolutions
		Map<String, String> resolutions = new HashMap<>();
		resolutions.put("1080p", "scale=-2:1080");
		resolutions.put("720p", "scale=-2:720");
		resolutions.put("480p", "scale=-2:480");
		resolutions.put("360p", "scale=-2:360");
		resolutions.put("240p", "scale=-2:240");
		
		//We specify where the input and output files will go
		String inputDir = System.getProperty("user.dir") + "/videos/";
		String outDir = System.getProperty("user.dir") + "/videos/";
		FFmpeg ffmpeg = null;
		FFprobe ffprobe = null;
		
		try {
			//This is where we put the address where the 
			log.debug("Initialising FFMpegClient");
			ffmpeg = new FFmpeg("C:\\Users\\georg\\AppData\\Local\\Microsoft\\WinGet\\Packages\\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\\ffmpeg-7.1.1-full_build\\bin\\ffmpeg.exe");
			ffprobe = new FFprobe("C:\\Users\\georg\\AppData\\Local\\Microsoft\\WinGet\\Packages\\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\\ffmpeg-7.1.1-full_build\\bin\\ffprobe.exe");
			FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
			
			//Finds the file with the largest resolution regardless of format
			Map<String, String> inputFiles = findInputFile(inputDir);
			
//            log.info("Using input file: {} (resolution: {})", inputFile, inputResolution);
			for (Map.Entry<String, String> inputEntry : inputFiles.entrySet()) {
				String baseName = inputEntry.getKey(); 
			    String inputFile = inputEntry.getValue();
			    
			    String inputResolution = inputFile.replaceAll(".*-(\\d+p).*", "$1");
			    log.info("Processing {} (resolution: {})", inputFile, inputResolution);
			
				for (String format : new String[]{"avi", "mp4", "mkv"}) {
				//Processing each resolution for 
					for(Map.Entry<String, String> entry : resolutions.entrySet()) {
						String resolutionName = entry.getKey();
						String outputFile = outDir + baseName + "-" + resolutionName + "." + format;
						
						//Skip if output would be same as input
						if (new File(inputDir + inputFile).getAbsolutePath().equals(new File(outputFile).getAbsolutePath())) {
						    log.info("Skipping {} - same as input file", outputFile);
						    continue;
						}
						
						//Skip resolutions higher than input for 
					    if (compareResolutions(resolutionName, inputResolution) > 0) {
					    	log.info("Skipping {} {} - higher than input", format, resolutionName);
					        continue;
					    }
						
						//Skip if file already exists
						if (new File(outDir + outputFile).exists()) {
							//Process file
							log.debug("Skipping " + baseName + resolutionName + "." + format + " file already exists");
							continue;
						}
						
						//Process file
						log.debug("Processing " + baseName + resolutionName + "." + format);
							
						FFmpegOutputBuilder builder = new FFmpegBuilder()
				                .setInput(inputDir + inputFile)
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
	
	//Streaming Server Implementation
    private static void startStreamingServer() {
        loadAvailableVideos(); // Scan the videos directory

        try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
            log.info("Streaming Server started on port " + SERVER_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            log.error("Server error: " + e.getMessage());
        }
    }
    
    private static void loadAvailableVideos() {
        File dir = new File(System.getProperty("user.dir") + "/videos/");
        File[] files = dir.listFiles();

        if (files != null) {
            for (File file : files) {
                String fileName = file.getName();
                String[] parts = fileName.split("-|\\."); // Format: "Name-Resolution.Format"

                if (parts.length == 3) {
                    String name = parts[0];
                    String resolution = parts[1];
                    String format = parts[2];
                    
                    availableVideos.computeIfAbsent(name, k -> new ArrayList<>())
                                 .add(resolution + "." + format);
                }
            }
        }
        log.info("Loaded videos: " + availableVideos);
    }
    
    private static void handleClient(Socket clientSocket) {
        try (
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
        ) {
            // Send list of available videos
            out.println("Available Videos: " + String.join(", ", availableVideos.keySet()));

            // Wait for client selection
            String selectedVideo = in.readLine();
            if (availableVideos.containsKey(selectedVideo)) {
                out.println("Available versions: " + availableVideos.get(selectedVideo));
            } else {
                out.println("Error: Video not found");
            }
        } catch (IOException e) {
            log.error("Client handling error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                log.error("Socket close error: " + e.getMessage());
            }
        }
    }
}
