package newMultiMediaLab;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class clientSide {
	private static final String SERVER_IP = "localhost";
    private static final int SERVER_PORT = 1234;
    
	public static void main(String[] args) {
		try (
	            Socket socket = new Socket(SERVER_IP, SERVER_PORT);
	            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
	            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
	            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))
	        ) {
	            // Receive list of available videos
	            System.out.println(in.readLine());

	            // Select a video
	            System.out.print("Select video: ");
	            String selection = userInput.readLine();
	            out.println(selection);

	            // Receive available versions
	            System.out.println(in.readLine());
	        } catch (IOException e) {
	            System.err.println("Client error: " + e.getMessage());
	        }
	}
}
