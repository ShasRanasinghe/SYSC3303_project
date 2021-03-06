package TFTP;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Scanner;

import static TFTPPackets.TFTPPacket.MAX_SIZE;

/**
 * The {@link TFTP.TFTPServer} class represents a TFTP Server (based on
 * Assignment 1 solution)
 *
 * @author Team 3000000
 * @author Aritra Sengupta
 * @author Shasthra Ranasinghe
 * @version 4.0
 */

public class TFTPServer implements Runnable {
	private DatagramSocket receiveSocket;
	private static String filePath;
	private static boolean verbose;
	private static volatile boolean acceptingNewConnections;
	private static ArrayList<Thread> transferThreadsList = new ArrayList<>();
	private static ArrayList<Integer> transferPortsList = new ArrayList<>();
	static TFTPServer serverInstance;

	private TFTPServer() {
		try {
			// Construct a datagram socket and bind it to port 69
			// on the local host machine. This socket will be used to
			// receive UDP Datagram packets.
			receiveSocket = new DatagramSocket(69);
			acceptingNewConnections = true;
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}
	}

	void removeFromTransferPortList(Long threadID, int port){
        if(verbose){
            System.out.println(Long.toString(threadID) + ": Removed port " + port + " from transfer ports list");
        }
		transferPortsList.remove(new Integer(port));
	}

	private void addToTransferPortList(int port){
        if(verbose){
            System.out.println("\nPort " + port + " added to transfer ports list");
        }
		transferPortsList.add(port);
	}

	/**
	 * This method sends or receives files from the client
	 *
	 * @since 1.0
	 */
	private void receivePacketFromClient() {
		byte dataBuffer[] = new byte[MAX_SIZE];
		DatagramPacket receivePacket = new DatagramPacket(dataBuffer, dataBuffer.length);
		try {
			receiveSocket.receive(receivePacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if(transferPortsList.contains(receivePacket.getPort())){
            System.out.println("Dropping packet from port " + receivePacket.getPort()
                    + " because a transfer from this port is already in progress");
        } else {
            Thread fileTransferThread = new Thread(new TFTPServerTransferThread(receivePacket, filePath, verbose));
            addToTransferPortList(receivePacket.getPort());
            fileTransferThread.start();
			transferThreadsList.add(fileTransferThread);
        }
	}

	public static void main(String args[]) throws Exception {
		// Requests the user to input a filepath for the directory you want to
		// work with
		Scanner in = new Scanner(System.in);
		// request the user for a path
		System.out.println("Enter the Directory Path:");
		System.out.println("Type \"DEFAULT\" to use the relative directory or Enter the filepath of the directory");
		for (;;) {
			String userInput = in.nextLine();
			if (userInput.equals("DEFAULT")) {
				// if default print the dir and finish
				System.out.println("You are now in: " + System.getProperty("user.dir") + "\\Server");
				filePath = System.getProperty("user.dir") + "\\Server" + "\\";
				break;
			} else {
				if (new File(userInput).isDirectory()) {
					// is the path was provided finish
					filePath = userInput + "\\";
					System.out.println("You have entered a valid Directory Path\n");
					break;
				} else {
					// if the directory does not exist, ask for an input again
					System.out.println("Invalid Directory\nPlease Try Again.");
				}
			}
		}

		String userInput;
		for (;;) {
			// request user for verbose or quiet mode
			System.out.println("Verbose(Y/N)?");
			userInput = in.nextLine();
			if (userInput.equals("Y")) {
				verbose = true;
				System.out.println("You have chosen Verbose mode");
				break;
			} else if (userInput.equals("N")) {
				verbose = false;
				System.out.println("You have chosen Quiet mode");
				break;
			} // if input is invalid, ask again
		}

		// Start the main program
		serverInstance = new TFTPServer();
		Thread serverSocketListeningThread = new Thread(serverInstance);
		serverSocketListeningThread.start();

		for (;;) {
			System.out.println("Type \"QUIT\" to quit the server or \"cd\" to change directory");
			userInput = in.nextLine();
			if (userInput.equals("QUIT")) {
				acceptingNewConnections = false;
				in.close();
				System.out.println("Waiting for current file transfers to finish");
				for (Thread aTransferThreadsList : transferThreadsList) {
					aTransferThreadsList.join();
				}
				System.out.println("Shutting down server");
				System.exit(0);
				in.close();
				break;
			} else if (userInput.equals("cd")) {// change directory
				System.out.println("Enter the Directory Path:");
				System.out.println(
						"Type \"DEFAULT\" to use the relative directory or Enter the filepath of the directory");

				for (;;) {
					userInput = in.nextLine();
					if (userInput.equals("DEFAULT")) {
						// if default print the dir and finish
						System.out.println("You are now in: " + System.getProperty("user.dir") + "\\Server");
						filePath = System.getProperty("user.dir") + "\\Server" + "\\";
						break;
					} else {
						if (new File(userInput).isDirectory()) {
							// if the path was provided finish
							filePath = userInput + "\\";
							System.out.println("You have entered a valid Directory Path\n");
							break;
						} else {
							// if the directory does not exist, ask for an input
							// again
							System.out.println("Invalid Directory\nPlease Try Again.");
						}
					}
				}
			}
		}
	}

	@Override
	public void run() {
		while (acceptingNewConnections) {
			this.receivePacketFromClient();
		}
	}
}
