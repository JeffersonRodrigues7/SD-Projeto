package projetofinal;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.Scanner;
import com.google.gson.*;

public class Peer {
	
	static Scanner reader = new Scanner(System.in);
	public static Gson gson = new Gson();
	private static String folder;
	private static String path;
	public static File[] files;
	public static boolean leave = false;
	public static String peersWithSolicitedFiles = "";
	
	public static void main(String[] args) throws Exception {
		
		ThreadAlivePeer thread = null;//Thread pra requisição alive vinda do servidor e resposta das requisições SEARCH e LEAVE
		TCPThread threadTCP = null;//Thread pra conexão TCP com outro peer
		
		InetAddress IPAddress = getAdress();//Pega endereço
		DatagramSocket clientSocket = getPort();//Pega porta	
		Mensagem peer = new Mensagem(IPAddress.getHostAddress(), clientSocket.getLocalPort());//Cria objeto
		setFiles(peer);//Coloca os arquivos no objeto
		String response = joinMethod(peer, clientSocket);//Envia o JOIN, espera o JOIN_OK
		
		/*Funcionalidade do Peer (b) */
		/*Vamos ficar nesse do while enquanto o servidor não responder com um JOIN_OK, quando ele receber vai printar no console do Peer seus arquivos. */
		do {
			if(response.equals("JOIN_OK")) {
				System.out.print(peer.toStringPeer());
				break;
			}
			response = joinMethod(peer, clientSocket);
		}while (!response.equals("JOIN_OK"));
			
		//Inicializando as Threads
		try {
			thread = new ThreadAlivePeer(IPAddress, clientSocket, peer);
			thread.start();
			
			ServerSocket serverSocket = new ServerSocket(clientSocket.getLocalPort());
			threadTCP = new TCPThread(serverSocket, peer);
			threadTCP.start();
		} catch (IOException e) {System.out.println(e);}
		
		/*Esse while fica sempre perguntando para o Peer o que ele gostaria de fazer, LEAVE SEARCH ou DOWNLOAD.
		 *OBS: O JOIN não é necessário porque ele já foi realizado */
		while(true) {
			System.out.println("\nO que você quer fazer agora: LEAVE, SEARCH, DOWNLOAD");
			String option = reader.nextLine();//Armazena opção do menu escolhida
			String desiredFile;//Vai armazenar o arquivo que o usuário que baixar
			boolean downloadAnswer;//Variável que indica se o outro Peer aceitou enviar o arquivo ou não, será utilizada na parte de DOWNLOAD
			
			
			/*Funcionalidade do Peer (c) */
			/*Caso o usuário decida pelo LEAVE, primeiramente enviamos a mensagem pro servidor e ficamos esperando pelo LEAVE_OK na Thread ThreadAlivePeer 
			 *Quando ele receber um LEAVE_OK a variável estática leave será true, dessa forma encerraremos as threads e a conexão com o servidor*/
			if(option.equals("LEAVE")) {
				leave = false;
				peer.setMessage("LEAVE");
				do {
					sendPacket(peer, IPAddress, clientSocket);
				}while (leave != true);//Fica no laço até o servidor responder a requisição com LEAVE_OK
				break;	
			}
			
			else if(option.equals("SEARCH")){
				System.out.println("\nDigite o nome do arquivo que você precisa com a extensão, exemplo: aula.mp4.");
				desiredFile = reader.nextLine();
				peer.setMessage("SEARCH");
				peer.setDesiredFile(desiredFile);
				
				sendPacket(peer, IPAddress, clientSocket);	
			}
			
			else if(option.equals("DOWNLOAD")) {
				String[] possiblePeers = peersWithSolicitedFiles.split(" ");//Cria lista de peers com o arquivo pedido, devo ignorar a posição 0, pois ela é um espaço em branco
				
				System.out.println("Digite o IP do peer que você quer pedir");
				String firstIpDownload = reader.nextLine();
				System.out.println("Digite a porta do peer que você quer pedir");
				int firstPortDownload = Integer.parseInt(reader.nextLine());

				downloadAnswer = solicitFile(firstIpDownload, firstPortDownload, peer, clientSocket);//Primeira solicitação
				
				if(!downloadAnswer) System.out.print("peer " + firstIpDownload + ":" + firstPortDownload + " negou o download, pedindo agora para o peer ");
				
				for(int i = 0; (i < possiblePeers.length) && !downloadAnswer ; i++) {
					String ipPossiblePeers = possiblePeers[i].substring( 0, possiblePeers[i].indexOf(":"));
					int portPossiblePeers = Integer.parseInt(possiblePeers[i].substring(possiblePeers[i].indexOf(":")+1, possiblePeers[i].length()));
		
					if(!ipPossiblePeers.equals(firstIpDownload) || portPossiblePeers != firstPortDownload) {
						System.out.println(ipPossiblePeers + ":" + portPossiblePeers);//Novo peer que vou pedir arquivo
						downloadAnswer = solicitFile(ipPossiblePeers, portPossiblePeers, peer, clientSocket);//Solicitação para os outros peers da lista
						if(!downloadAnswer) System.out.print("peer " + ipPossiblePeers + ":" + portPossiblePeers + " negou o download, pedindo agora para o peer ");
					}
				}
				
				if(!downloadAnswer) System.out.println(firstIpDownload + ":" + firstPortDownload);//Novo peer que vou pedir arquivo;
				
				while(!downloadAnswer) {
					Thread.sleep(5000);
					downloadAnswer = solicitFile(firstIpDownload, firstPortDownload, peer, clientSocket);//Vou ficar solicitando pro primeiro peer até ele aceitar
					if(!downloadAnswer) System.out.println("peer " + firstIpDownload + ":" + firstPortDownload + " negou o download. Pedindo novamente");
				}		
			}
			

			
			else {System.out.println("Comando desconhecido, digite novamente");	}
			
			Thread.sleep(100);
		}
		
		thread.interrupt();
		threadTCP.interrupt();
		clientSocket.close();
		System.exit(0);

	}
	
	/*Funcionalidade do Peer (a, b...) */
	/*Envio das requisições por UDP ao servidor, o sendPacket recebe o Objeto Mensagem e então através da biblioteca gson(indicada pelo professor) 
	 * passa a informação para o formato JSON, após isso ele envia a informação ao Servidor. */
	protected static void sendPacket(Mensagem peer, InetAddress IPAddress, DatagramSocket clientSocket) throws IOException {
		String json = gson.toJson(peer);
		byte[] sendData = new byte[1024];
		sendData = json.getBytes();
		
		DatagramPacket packet = new DatagramPacket(sendData, sendData.length, IPAddress, 10098);
		clientSocket.send(packet);
	}
	
	/*Funcionalidade do Peer (a, b) */
	/*Esse receivePacket é exclusivamente para receber o JOIN_OK, o peer espera o pacote pelo comando clientSocket.receive(recPkt) e então retorna a informação */
	private static String receivePacket(DatagramSocket clientSocket) throws IOException {
		byte[] recBuffer = new byte[1024];
		DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
		clientSocket.receive(recPkt);
		String information = new String(recPkt.getData(), recPkt.getOffset(), recPkt.getLength());
		
		return information;
	}
	
	/*Funcionalidade do Peer (b) */
	/*Essa função define a mensagem como JOIN e envia o peer para função sendPacket, após isso fica esperando uma resposta do servidor: String information = receivePacket(clientSocket);
	 * Por fim ela retorna a mensagem enviada pelo servidor */
	private static String joinMethod(Mensagem peer, DatagramSocket clientSocket) throws IOException{
		peer.setMessage("JOIN");
		sendPacket(peer, peer.getIPAddress(), clientSocket);
		
		String information = receivePacket(clientSocket);
		peer = gson.fromJson(information, Mensagem.class);
		return peer.getMessage();
	}
	
	private static InetAddress getAdress() throws UnknownHostException {
		System.out.println("Entre com o seu endereço IP. Por exemplo: 127.0.0.1");
		String adress = reader.nextLine();
		InetAddress IPAddress = InetAddress.getByName(adress);
		
		return IPAddress;
	}
	
	private static DatagramSocket getPort() throws SocketException {
		System.out.println("Entre com a sua porta. Por exemplo: 62642");
		int port = Integer.parseInt(reader.nextLine());

		DatagramSocket clientSocket = new DatagramSocket(port);
		return clientSocket;
	}
	
	private static void setFiles(Mensagem peer) throws Exception {
		System.out.println("Entre com a sua pasta de arquivos. Por exemplo: peer1");
		folder = reader.nextLine();
		
		//Encontrando pasta de arquivos do usuário
		Path currentRelativePath = Paths.get("");
		String s = currentRelativePath.toAbsolutePath().toString();
		path = (s+"\\"+folder);
		
	    File file = new File(s+"\\"+folder);
	    files = file.listFiles();
	    
	    for (File archive : files)//Esse laço adiciona os arquivos no objeto
	    	peer.addFile(archive.getName());
	}
	
	public static boolean solicitFile(String ipDownload, int portDownload, Mensagem peer, DatagramSocket clientSocket) {
		try {
			Socket s =  new Socket(ipDownload, portDownload);//Criando conexão entre host e porta do servidor, que é o próprio Peer
			
			//Vamos utilizar para enviar mensagem para o peer
			ObjectOutputStream send = new ObjectOutputStream(s.getOutputStream());
			
            //Vamos utilizar para ler a mensagem enviada pelo peer
			ObjectInputStream reader2 = new ObjectInputStream(s.getInputStream());
			
			send.writeObject(gson.toJson(peer));
			peer = Peer.gson.fromJson((String) reader2.readObject(), Mensagem.class);
            if(peer.getTCPmessage().equals("DOWNLOAD_NEGADO")) return false;
            long fileLength = peer.getMessageLength();
              
            //Abaixo estou recebendo o arquivo do Peer
    		DataInputStream dis = new DataInputStream(s.getInputStream());
    		FileOutputStream fos = new FileOutputStream(path + "\\" + peer.getDesiredFile());
    		byte[] buffer = new byte[4096];
    		
    		long filesize = fileLength;
    		int read = 0;
    		long remaining = filesize;
    		while((read = dis.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
    			remaining -= read;
    			fos.write(buffer, 0, read);
    		}
    		
    		fos.close();
    		dis.close();
    		
    		System.out.println("Arquivo " + peer.getDesiredFile() + " baixado com sucesso na pasta " + folder);
    		peer.setMessage("UPDATE");
    		sendPacket(peer, peer.getIPAddress(), clientSocket);
    		
		}catch(Exception e) { System.out.println(e); }
		
		return true;
	}
}

/*Funcionalidade do Peer (a) */
/*A Thread ThreadAlivePeer além de ficar sempre ativa para receber as requisições “ALIVE” do servidor, também irá tratar todas as outras requisições do servidor que não sejam o “JOIN_OK”. 
Quando a Thread recebe o start na função main ela entra no laço while(true), recebendo os pacotes pelo comando recPkt = new DatagramPacket(recBuffer, recBuffer.length); */
class ThreadAlivePeer extends Thread {
	
	private InetAddress IPAddress;
	private DatagramSocket clientSocket;
	private Mensagem peer;
	
	public ThreadAlivePeer(InetAddress ip, DatagramSocket clientSocket, Mensagem peer) {
		this.IPAddress = ip;
		this.clientSocket = clientSocket;
		this.peer = peer;
	}
	
	public void run() {
		DatagramPacket recPkt;
		
		while(true) {		
			byte[] recBuffer = new byte[1024];
			recPkt = new DatagramPacket(recBuffer, recBuffer.length);
			
			try {
				clientSocket.receive(recPkt);
				String information = new String(recPkt.getData(), recPkt.getOffset(), recPkt.getLength());
				peer = Peer.gson.fromJson(information, Mensagem.class);
				
				if(peer.getMessage().equals("ALIVE")) {					
					peer.setMessage("ALIVE_OK");
					
					Peer.sendPacket(peer, IPAddress, clientSocket);
				}
				
				/*Funcionalidade do Peer (c) */
				/*Quando o servidor enviar um LEAVE_OK pro Peer vamos colocar a variável estática leave pra true, para podermos sair do laço lá na main*/
				else if(peer.getMessage().equals("LEAVE_OK")) {
					Peer.leave = true;
					break;	
				}
				
				else if(peer.getMessage().equals("SEARCH_ANSWER")) {
					System.out.println("Peers com arquivo solicitado: " + peer.getPeersWithSolicitedFiles());	
					Peer.peersWithSolicitedFiles = peer.getPeersWithSolicitedFiles();
				}
			} catch (IOException e) {e.printStackTrace();}
				
		}
	}
}

/*Funcionalidade do Peer (a, h, i, k) */
/*Essa Thread é chamada logo no inicio, ela fica esperando requisições TCP de outros Peers, após receber uma requisição ela starta uma nova Thread requestThread*/
class TCPThread extends Thread {
	
	private ServerSocket serverSocket = null;
	private Mensagem peer;
	
	public TCPThread(ServerSocket serverSocket, Mensagem peer) {
		this.serverSocket = serverSocket;
		this.peer = peer;
	}
	
	public void run() {
		while(true) {

			try {
				Socket no = serverSocket.accept();
				requestThread thread = new requestThread(no, peer); //Criando uma thread eu posso ter várias conexões trabalhando simultaneamente
				thread.start();
			} catch (IOException e) { System.out.println(e); }	
			
		}
	}
}

/*Funcionalidade do Peer (a, h, i, k) */
/*Essa Thread é a que cuida de enviar o arquivo para o outro Peer utilizando comunicação TCP*/
class requestThread extends Thread {
	
	private Socket no = null;
	private Mensagem peer;
	private ObjectInputStream reader2;
	private ObjectOutputStream send;
	
	public requestThread(Socket node, Mensagem peer) {
		this.no = node;
		this.peer = peer;
	}
	
	public void run() {
		
		try {
            //Vamos utilizar para ler a mensagem enviada pelo peer
			reader2 = new ObjectInputStream(no.getInputStream());
			
			//Vamos utilizar para enviar mensagem para o peer
			send = new ObjectOutputStream(no.getOutputStream());

            Mensagem peerReq = Peer.gson.fromJson((String) reader2.readObject(), Mensagem.class);
			
			if(peer.getFiles().contains(peerReq.getDesiredFile())) {	
				Random random = new Random();
				boolean answer = random.nextBoolean();
				
				if(answer) {
					for(int i = 0; i< Peer.files.length ; i++) {//Procurando o arquivo para enviar
						if(Peer.files[i].getName().equals(peerReq.getDesiredFile())) {
							sendFile(Peer.files[i], peerReq); 
						}
					}
				}
				
				else {
					peerReq.setTCPmessage("DOWNLOAD_NEGADO");
					send.writeObject(Peer.gson.toJson(peerReq));
				}
			} else {
				peerReq.setTCPmessage("DOWNLOAD_NEGADO");
				send.writeObject(Peer.gson.toJson(peerReq));
			}
			
			no.close();
			
		} catch (Exception e) { System.out.println(e);}
	}
	
	private void sendFile(File file, Mensagem peerReq) throws IOException {
		peerReq.setTCPmessage("DOWNLOAD_PERMITIDO");
		peerReq.setMessageLength(file.length());
		send.writeObject(Peer.gson.toJson(peerReq));//enviando tamanho do arquivo
		
		//Enviando arquivo abaixo
		DataOutputStream dos = new DataOutputStream(no.getOutputStream());
		FileInputStream fis = new FileInputStream(file);
		byte[] buffer = new byte[4096];
		
		while (fis.read(buffer) > 0) {
			dos.write(buffer);
		}
		
		fis.close();
		dos.close();
	}
}
