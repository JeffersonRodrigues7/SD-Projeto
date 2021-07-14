package projetofinal;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class Mensagem {
	
	private InetAddress IPAddress;//Endereço do peer no formato InetAddress
	private String ip;//Endereço do peer no formato String
	private int port;//porta do peer
	private ArrayList<String> filesName = new ArrayList<String>();//Lista de String com os arquivos
	private boolean alive;//Vai ser utilizado para verificar se o Peer ainda está na rede
	private String message;//mensagem a ser enviada entre Peer e Servidor
	private String desiredFile;//Arquivo que o Peer deseja
	private String peersWithSolicitedFiles;//String com lista de Peers com o arquivo solicitado
	private String TCPmessage;//Utilizado para troca de mensagem via TCP entre Peers
	private Long messageLength;//Tamanho do arquivo a ser enviado
	
	public Mensagem(String ip, int port) {
		this.ip = ip;
		this.port = port;
		try {this.IPAddress = InetAddress.getByName(ip);
		} catch (UnknownHostException e) {e.printStackTrace();}
	}

	public InetAddress getIPAddress() { return IPAddress; }
	
	public String getIp() {return ip;}

	public int getPort() {return port;}
	
	public String getMessage() { return message; }

	public void setMessage(String message) { this.message = message; }

	public void addFile(String file) { filesName.add(file); }
	
	public String getFiles() {//Retorna String com todos os arquivos
		String text = "";
	    for (String file : filesName)
	    	text += file + " ";
		return text;	
	}
	
	public boolean verifyFile(String name) {//Verifica se um arquivo existe
		for(int i = 0; i < filesName.size(); i++)
			if(name.equals(filesName.get(i))) 
				return true;
		return false;
	}
	
	public void setAlive(boolean alive) { this.alive = alive; }

	public boolean isAlive() { return alive; }	
	
	public void setTCPmessage(String tCPmessage) { TCPmessage = tCPmessage; }
	
	public String getTCPmessage() { return TCPmessage; }
	
	public void setDesiredFile(String desiredFile) { this.desiredFile = desiredFile; }
	
	public String getDesiredFile() { return desiredFile; }
	
	public void setPeersWithSolicitedFiles(String peers) { this.peersWithSolicitedFiles = peers; }

	public String getPeersWithSolicitedFiles() { return peersWithSolicitedFiles; }
	
	public void setMessageLength(Long messageLength) { this.messageLength = messageLength; }
	
	public Long getMessageLength() { return messageLength; }

	public String toStringServer() { return "Peer " + ip + ":" + port + " adicionado com arquivos " + getFiles(); }
	
	public String toStringPeer() { return "Sou Peer " + ip + ":" + port + " com arquivos " + getFiles(); }





}
