package projetofinal;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;
import com.google.gson.Gson;

public class Server {
	
	private static Scanner reader = new Scanner(System.in);//Capturar dados do teclado
	private static DatagramSocket serverSocket;
	protected static ArrayList<Mensagem> peers = new ArrayList<Mensagem>();//Esse ArrayList vai armazenar as mensagens que referenciam os peers que se conectaram ao servidor
	private static Queue<DatagramPacket> requisitions = new LinkedList<>();//Essa fila vai armazenar os pacotes que o servidor vai receber
	private static Gson gson = new Gson();//Biblioteca que permite passar um Objeto para formato JSON e vice-versa
	
	public static void main(String[] args) throws Exception {
		
		serverSocket = startServer();//Chamando função que inicia o server e devolve o serverSocket (Funcionalidade Servidor(g))
		
		DatagramPacket recPkt;
		ThreadAliveServer thread = new ThreadAliveServer();
		thread.start();
		
		
		/*Funcionalidade do servidor (a) */
		/*Recebimento das requisições simultâneas, o servidor espera o pacote da função receivePacket, guarda ela na fila e então trata todos os pacotes armazenados na fila*/
		while(true) {	
			try {
				recPkt = receivePacket(serverSocket);
				requisitions.add(recPkt);
				
				while(!requisitions.isEmpty())
					treatInformation(requisitions.poll());

			} catch (SocketTimeoutException e) {System.out.println(e);}
		}	
	}
	
	/*Funcionalidade do servidor (a, b, c, d, e, f) */
	/*A função treatinformation vai receber um pacote, primeiramente ela vai extrair a mensagem de JSON para Objeto Mensagem através da biblioteca gson(indicada pelo professor)
	 *a partir do comando peer.getMessage() vamos descobrir qual a mensagem enviada ao Servidor para então destinar o Objeto Mensagem para o if correspondente */
	private static void treatInformation(DatagramPacket recPkt) {
		try {
			String information = new String(recPkt.getData(), recPkt.getOffset(), recPkt.getLength());
			Mensagem peer = gson.fromJson(information, Mensagem.class);
			
			String ip = peer.getIp();
			int port = peer.getPort();
			
			
			/*Funcionalidade do servidor (b) */
			/*Para uma requisição JOIN o servidor vai responder colocando um JOIN_OK na mensagem e então chamando a função sendPacket para enviar de volta ao Peer 
			 *Por fim adicionamos o novo peer na ArrayList que mantém os peers no servidor e então imprimimos os dados do peer no console do servidor */
			if(peer.getMessage().equals("JOIN")) {
				peer.setMessage("JOIN_OK");
				sendPacket(peer, peer.getIPAddress(), peer.getPort());
				
				peers.add(peer);
				System.out.println(peer.toStringServer());
			}
			
			
			/*Funcionalidade do servidor (c) */
			/*Para uma requisição LEAVE o servidor vai responder colocando um LEAVE_OK na mensagem e então chamando a função sendPacket para enviar de volta ao Peer 
			 *Aqui também chamamos a função deltePeer passando o peer*/
			else if(peer.getMessage().equals("LEAVE")) {
				deletePeer(peer, false);
				peer.setMessage("LEAVE_OK");
				sendPacket(peer, peer.getIPAddress(), peer.getPort());			
			}
			
			
			/*Funcionalidade do servidor (d) */
			/*Para uma requisição SEARCH o servidor vai verificar quais peers que estão no servidor possuem o arquivo desejado: if(peers.get(i).verifyFile(peer.getDesiredFile()))
			 *O servidor vai armazenar na String text o IP e Porta de cada peer que possui o arquivo, separando-os por espaços 
			 *Por fim colocamos os peers na Mensagem através do comando peer.setPeersWithSolicitedFiles(text), definimos a mensagem como SEARCH_ANSWER e chamamos a função sendPacket */
			else if(peer.getMessage().equals("SEARCH")){
				System.out.println("Peer " + ip + ":" + port + " solicitou o arquivo: " + peer.getDesiredFile());
				
				String text = "";
				for(int i = 0; i < peers.size(); i++) 
					if(peers.get(i).verifyFile(peer.getDesiredFile())) 
						text += peers.get(i).getIp() + ":" + peers.get(i).getPort() + " ";
				
				peer.setMessage("SEARCH_ANSWER");
				peer.setPeersWithSolicitedFiles(text);
				sendPacket(peer, recPkt.getAddress(), port);
			}
			
			
			/*Funcionalidade do servidor (e) */
			/*Nessa parte procuramos o peer correspondente armazenado no servidor if(peers.get(i).getIp().equals(ip) && peers.get(i).getPort() == port)  
			 *e então adicionamos o novo arquivo no peer através do comando peers.get(i).addFile(peer.getDesiredFile());
			 *Por fim o servidor vai responder colocando um UPDATE_OK na mensagem e então chamando a função sendPacket para enviar de volta ao Peer*/
			else if(peer.getMessage().equals("UPDATE")) {

				for(int i = 0; i < peers.size(); i++)
					if(peers.get(i).getIp().equals(ip) && peers.get(i).getPort() == port) 
						peers.get(i).addFile(peer.getDesiredFile());
				
				peer.setMessage("UPDATE_OK");
				sendPacket(peer, recPkt.getAddress(), port);
			}
			
			
			/*Funcionalidade do servidor (f) */
			/* */
			else if(peer.getMessage().equals("ALIVE_OK")) {
				for(int i = 0; i < peers.size(); i++) 
					if(peers.get(i).getIp().equals(ip) && peers.get(i).getPort() == port) 
						peers.get(i).setAlive(true);
		
			}

		}catch(Exception e) { System.out.println(e); }
	}
	
	/*Funcionalidade do servidor (a) */
	/*Recebimento das requisições simultâneas, o servidor espera o pacote pelo comando serverSocket.receive(recPkt) e então retorna o pacote */
	private static DatagramPacket receivePacket(DatagramSocket serverSocket) throws IOException {
		byte[] recBuffer = new byte[1024];
		DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
		serverSocket.receive(recPkt);
		
		return recPkt;
	}
	
	/*Funcionalidade do servidor (a, b, c, d, e, f) */
	/*Envio das requisições simultâneas, o sendPacket recebe o Objeto Mensagem e então através da biblioteca gson(indicada pelo professor) 
	 * passa a informação para o formato JSON, após isso ele envia a informação ao Peer. */
	static protected void sendPacket(Mensagem peer, InetAddress IPAddress, int port) throws IOException {
		String json = gson.toJson(peer);
		byte[] sendData = new byte[1024];
		sendData = json.getBytes();
		
		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);
		serverSocket.send(sendPacket);
	}

	/*Funcionalidade do servidor (c, f) */
	/* A função deletePeer recebe um peer e um valor booleano dead, se ele for true significa que o Peer saiu sem avisar, se for false significa que o Peer saiu enviando a mensagem LEAVE.
	 * Aqui procura-se o peer a ser eliminado na ArrayList que mantém os peers no servidor, após encontrarmos o eliminamos da estrutura. 
	 * Caso dead seja true uma mensagem será impressa no console do servidor*/
	static protected void deletePeer(Mensagem peer, boolean dead) {
		for(int i = 0; i < peers.size(); i++) {
			if(peers.get(i).getIp().equals(peer.getIp()) && peers.get(i).getPort() == peer.getPort()) {
				if(dead) System.out.println("Peer " + peer.getIp() + ":" + peer.getPort() + " morto. Eliminando seus arquivos " + peers.get(i).getFiles());
				peers.remove(i);
			}
		}
	}
	
	/*Funcionalidade do servidor (g) */
	/*Será necessário entrar com IP e Porta para levantar o servidor, após isso criamos o DatagramSocket serverSocket e retornamos para a função main*/
	private static DatagramSocket startServer() throws SocketException {
		try {
			System.out.println("Entre com o seu endereço IP. Por exemplo: 127.0.0.1");
			String adress = reader.nextLine();
			InetAddress IPAddress;
			IPAddress = InetAddress.getByName(adress);
			
			System.out.println("Entre com a sua porta. Por exemplo: 10098");
			int port = Integer.parseInt(reader.nextLine());
			
			DatagramSocket serverSocket = new DatagramSocket(port, IPAddress);
			return serverSocket;
		} catch (UnknownHostException e) {e.printStackTrace();}
		return null;
	}
}

/*Funcionalidade do servidor (f) */
/* */
class ThreadAliveServer extends Thread { //Thread que vai ficar verificando que o peer está vivo
	
    public void run() {
    	while(true) {
			try {
				
				for(int i = 0; i < Server.peers.size(); i++) {
					Server.peers.get(i).setAlive(false);
					Server.peers.get(i).setMessage("ALIVE");
					
					//Envia pacote com Req Alive
					Server.sendPacket(Server.peers.get(i), Server.peers.get(i).getIPAddress(), Server.peers.get(i).getPort());
	
					//Dando um tempo para verificar a resposta
					Thread.sleep(2500);
					
					//Deletando peer dos dados do servidor
					if(i < Server.peers.size() && Server.peers.get(i) != null && !Server.peers.get(i).isAlive()) 
						Server.deletePeer(Server.peers.get(i), true);		
				}
				Thread.sleep(30);
				
			} catch (IOException | InterruptedException e) { System.out.println("Erro ao checar se os peers estão ativos"); } 
    	}
    }
}