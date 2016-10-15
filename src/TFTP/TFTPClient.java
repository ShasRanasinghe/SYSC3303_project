package TFTP;

import static TFTPPackets.TFTPPacket.MAX_SIZE;

//TFTPClient.java
//This class is the client side for a very simple assignment based on TFTP on
//UDP/IP. The client uses one port and sends a read or write request and gets 
//the appropriate response from the server.  No actual file transfer takes place.   

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Scanner;

import Exceptions.PacketOverflowException;
import FileIO.TFTPReader;
import FileIO.TFTPWriter;
import TFTPPackets.*;
import TFTPPackets.TFTPPacket.Opcode;

/*
 * @author: Mohamed Zalat & Kunall Banerjee
 * TFTPClient
 */
public class TFTPClient {

	private DatagramPacket sendPacket, receivePacket;
	private DatagramSocket sendReceiveSocket;
	private static String filePath;
	private TFTPReader tftpReader;
	private TFTPWriter tftpWriter;
	private static Mode run;
	private static boolean firstTime;
	private static boolean verbose;

	// we can run in normal (send directly to server) or test
	// (send to simulator) mode
	public static enum Mode { NORMAL, TEST};

	/**
	 * This is the constructor for the client
	 * It created the required sockets and sets a timeout
	 */
	public TFTPClient()
	{
		firstTime = true;
		try {
			// Construct a datagram socket and bind it to any available
			// port on the local host machine. This socket will be used to
			// send and receive UDP Datagram packets.
			sendReceiveSocket = new DatagramSocket();
			sendReceiveSocket.setSoTimeout(10000);
		} catch (SocketException se) {   // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * @param sc
	 * @throws PacketOverflowException
	 * @throws FileNotFoundException
	 * 
	 * This function is run for every file transfer
	 * It asks the user if they want to do a read or a write request
	 * as well as if they want to quit or change directories.
	 * Once the information is give, the function will create the appropriate
	 * packet and send it to the server or error simulator
	 * 
	 */
	public void sendRequest(Scanner sc) throws PacketOverflowException, FileNotFoundException
	{
		String filename; // filename and mode as Strings
		int sendPort;
		TFTPPacket tftpPacket = new TFTPPacket();

		// In the assignment, students are told to send to 23, so just:
		// sendPort = 23; 
		// is needed.
		// However, in the project, the following will be useful, except
		// that test vs. normal will be entered by the user.

		if (run==Mode.NORMAL){ 
			sendPort = 69;
		}else{
			sendPort = 23;
		}
		boolean done = false;
		while(!done){
			System.out.println("Choose Read or Write request(R/W) or enter \"QUIT\" to close the client");
			String cmd = sc.nextLine();
			//write request
			if(cmd.equals("W")){
				System.out.println("Client: creating WRQ packet.");

				// next we have a file name
				for(;;){
					System.out.println("Enter file name");
					filename = sc.nextLine();
					if(new File (filePath + "\\" + filename).isFile()){
						//is the path was provided finish
						System.out.println("You have entered a valid file name");
						break;
					}else{
						//if the directory does not exist, ask for an input again
						System.out.println("\nError Code: 1\nError Message: File Not Found\nPlease Try Again\n");
					}
				}
				tftpPacket = new WRQPacket(filename, RRQWRQPacketCommon.Mode.NETASCII);
				done = true;
				try {
					tftpReader = new TFTPReader(new File(filePath + filename).getPath());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}else if(cmd.equals("R")) {//read request
				System.out.println("Client: creating RRQ packet.");

				// next we have a file name
				for(;;){
					System.out.println("Enter file name");
					filename = sc.nextLine();
					if(!new File (filePath + "\\" + filename).isFile()){
						//is the path was provided finish
						System.out.println("You have entered a valid file name");
						break;
					}else{
						//if the directory does not exist, ask for an input again
						System.out.println("\nError Code: 6\nError Message: File Already Exists\nPlease Try Again\n");
					}
				}
				tftpPacket = new RRQPacket(filename, RRQWRQPacketCommon.Mode.NETASCII);
				done = true;
				tftpWriter = new TFTPWriter(new File(filePath + filename).getPath(),false);
			}else if(cmd.equals("cd")) {//change directory
				System.out.println("Enter the Directory Path:");
				System.out.println("Type \"DEFAULT\" to use the relative directory or Enter the filepath of the directory");

				for(;;){
					String userInput = sc.nextLine();
					if(userInput.equals("DEFAULT")){
						//if default print the dir and finish
						System.out.println("You are now in: " + System.getProperty("user.dir") + "\\Client");
						filePath = System.getProperty("user.dir") + "\\Client" + "\\";
						break;
					}else{
						if(new File (userInput).isDirectory()){
							//if the path was provided finish
							filePath = userInput + "\\";
							System.out.println("You have entered a valid Directory Path\n");
							break;
						}else{
							//if the directory does not exist, ask for an input again
							System.out.println("Invalid Directory\nPlease Try Again.");
						}
					}
				}
			}else if(cmd.equals("QUIT")) {//quit
				System.out.println("Client: Closing socket and exiting.");

				// close scanner, socket and exit
				sc.close();
				sendReceiveSocket.close();
				System.exit(0);
			}
		}
		try {// Send the datagram packet to the server via the send/receive socket.
			sendPacketToServer(tftpPacket,InetAddress.getLocalHost(),sendPort);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		System.out.println("Client: Packet sent.");
	}

	/**
	 * This function deals with the actual file transfer
	 * Data, ACK and error packets go through this function
	 */
	private void sendReceivePacket(){
		byte dataBuffer[] = new byte[MAX_SIZE];
		byte[] data = null;
		TFTPPacket tftpPacket = new TFTPPacket();

		receivePacket = new DatagramPacket(dataBuffer, dataBuffer.length);
		try {
			//Receive packet
			sendReceiveSocket.receive(receivePacket);
			//Create byte array of proper size
			data = new byte[receivePacket.getLength()];
			System.arraycopy(dataBuffer, 0, data, 0, data.length);

			// Process the received datagram.

			if(verbose){
				System.out.println("\nClient: Packet received:");
				System.out.println("From host: " + receivePacket.getAddress());
				System.out.println("Host port: " + receivePacket.getPort());
				int len = receivePacket.getLength();
				System.out.println("Length: " + len);
				System.out.println("Containing: ");
				System.out.println(new String(Arrays.copyOfRange(data,0,len)));
				System.out.println("Byte Array: " + TFTPPacket.toString(Arrays.copyOfRange(data,0,len))+"\n");
			}

			//Get opcode
			Opcode opcode = Opcode.asEnum((int) data[1]);


			if(opcode == Opcode.DATA){
				if(verbose){
					System.out.println("Opcode: DATA");
				}
				//create/validate data
				DataPacket dataPacket = new DataPacket(data);
				if(new File(filePath).getUsableSpace()>= dataPacket.getData().length){ //check if there is enough space available
					//write the data you just received
					tftpWriter.writeToFile(dataPacket.getData());
					//create an ack packet from corresponding block number
					tftpPacket = new ACKPacket(dataPacket.getBlockNumber());
					sendPacketToServer(tftpPacket,receivePacket.getAddress(),receivePacket.getPort());
					if(dataPacket.getData().length < 512) {
						System.out.println("\nComplete File Has Been Received\n");
						firstTime = true;
						tftpWriter.closeHandle();
					}
				}else{
					System.out.println("\nError Code: 3\nError Message: Disk Full or Allocation Exceded\n");
					firstTime = true;
				}
			}else if(opcode == Opcode.ACK){
				if(verbose){
					System.out.println("Opcode: ACK");
				}
				ACKPacket ackPacket = new ACKPacket(data);
				//send next block of file until there are no more blocks
				if(ackPacket.getBlockNumber() < tftpReader.getNumberOfBlocks()){
					tftpPacket = new DataPacket(ackPacket.getBlockNumber() + 1, tftpReader.getFileBlock(ackPacket.getBlockNumber() + 1));
					sendPacketToServer(tftpPacket,receivePacket.getAddress(),receivePacket.getPort());
				}else if(ackPacket.getBlockNumber() == tftpReader.getNumberOfBlocks()){
					firstTime = true;
					System.out.println("\nComplete File Has Been Sent\n");
				}
			}else if(opcode == Opcode.ERROR){ // check for error packet and print message
				ErrorPacket errorPacket = new ErrorPacket(data);
				System.out.println("\nError Code: " + errorPacket.getErrorCode() + "\nError Message: " + errorPacket.getErrorMessage() + "\n");
				firstTime = true;
			}
			
		} catch (Exception e) {
			System.exit(0);
			e.printStackTrace();
		}
	}
	
	/**
	 * @param tftpPacket
	 * @param address
	 * @param port
	 * 
	 * This function uses the information provided to create a send packet
	 * and send it to the error simulator or the server
	 * 
	 */
	public void sendPacketToServer(TFTPPacket tftpPacket, InetAddress address, int port) {
        //Send packet to client
        sendPacket = new DatagramPacket(tftpPacket.getByteArray(), tftpPacket.getByteArray().length,
                address, port);
        //printing out information about the packet
        if(verbose){
        	System.out.println("\nClient: Sending packet");
        	System.out.println("To host: " + sendPacket.getAddress());
        	System.out.println("Destination host port: " + sendPacket.getPort());
        	int length = sendPacket.getLength();
        	System.out.println("Length: " + length);
        	if(firstTime){System.out.println(new String(tftpPacket.getByteArray(),0,tftpPacket.getByteArray().length));}
        	System.out.println("Byte Array: " + TFTPPacket.toString(sendPacket.getData()));
        }
        try {
            sendReceiveSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	/**
	 * @param args
	 * 
	 * The main function requests the user for a directory and
	 * asks if the client should run in verbose mode or quiet mode
	 * After that, run the client on a look
	 * 
	 */
	public static void main(String args[]) 
	{
		Scanner in = new Scanner(System.in);
		System.out.println("Enter the Directory Path:");
		System.out.println("Type \"DEFAULT\" to use the relative directory or Enter the filepath of the directory");

		for(;;){
			String userInput = in.nextLine();
			if(userInput.equals("DEFAULT")){
				//if default print the dir and finish
				System.out.println("You are now in: " + System.getProperty("user.dir") + "\\Client");
				filePath = System.getProperty("user.dir") + "\\Client" + "\\";
				System.out.println("\nYou can change the directory at any point by typing \"cd\"\n");
				break;
			}else{
				if(new File (userInput).isDirectory()){
					//if the path was provided finish
					filePath = userInput + "\\";
					System.out.println("You have entered a valid Directory Path\n");
					break;
				}else{
					//if the directory does not exist, ask for an input again
					System.out.println("Invalid Directory\nPlease Try Again.");
				}
			}
		}

		String userInput;
		for(;;){
			//request user for verbose or quiet mode
			System.out.println("Verbose(Y/N)?");
			userInput = in.nextLine();
			if(userInput.equals("Y")){
				verbose = true;
				System.out.println("You have chosen Verbose mode");
				break;
			}else if(userInput.equals("N")){
				verbose = false;
				System.out.println("You have chosen Quiet mode");
				break;
			}//if input is invalid, ask again
		}
		boolean done = false;

		while(!done){
			System.out.println("Enter mode (TEST for test and NORMAL for normal)");
			String m = in.nextLine();

			if(m.equals("TEST")){
				run = Mode.TEST;
				done = true;
			}
			else if(m.equals("NORMAL")){ 
				run = Mode.NORMAL;
				done = true;

			}
		}
		System.out.println("You can change the directory at any point by typing \"cd\"\n");
		TFTPClient c = new TFTPClient();

		while(true) {
			try {
				if(firstTime){c.sendRequest(in); firstTime = false;}//if its the first time, create the RRQ/WRQ packets
				c.sendReceivePacket();
			} catch(Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
	}

}