package server.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;

import network.Connection;
import network.ConnectionFactory;
import network.ConnectionListener;
import network.ConnectionManager;
import network.Message;
import network.MessageListener;
import server.messages.MessageContext;
import server.messages.MessageParser;
import system.core.Item;
import system.core.SalesSystem;
import system.core.User;
import utils.ConfigManager;
import utils.TextAreaLogger;

public class Server implements ConnectionListener {
	private ConnectionManager listener;
	
	private Connection loadBalancerConnection;
	
	private UserConnection[] connections;
	private int port;
		
	private int next;
	private int capacity;
					
	public Server() {

	}
	
	public void start() {
		port = (Integer) ConfigManager.getConfig("server_port"); 
		capacity = (Integer) ConfigManager.getConfig("server_capacity");
		
		String loadBalancerAddress = (String) ConfigManager.getConfig("lb_address");
		int loadBalancerPort = (Integer) ConfigManager.getConfig("lb_port");

		next = 0;
		
		connections = new UserConnection[capacity];
		
		try {
			listener = ConnectionFactory.getConnectionManagerImplByConfig(port);
		} catch (IOException e) {
		}
		
		TextAreaLogger.getInstance().log("Iniciando servidor na porta " + port + "...");
		
		tellLoadBalancer(loadBalancerAddress, loadBalancerPort);
		
		listener.setConnectionListener(this);
				
		listener.listenForConnections();
	}
	
	private void tellLoadBalancer(String host, int port) {
		
		try {
			final Server server = this;
			
			loadBalancerConnection = ConnectionFactory.getConnectionImplByConfig(host, port);
			loadBalancerConnection.openConnection();
			
			Message msg = new Message("RegisterAsServer", "senha_secreta",
				String.valueOf(capacity), InetAddress.getLocalHost().getHostAddress(), String.valueOf(this.port));
			
			loadBalancerConnection.setMessageListener(new MessageListener() {
				
				@Override
				public void messageReceived(Message message) {					
					message.setConnection(loadBalancerConnection);
					MessageParser.parseMessage(new MessageContext(message, null, server));
				}
			});
			
			loadBalancerConnection.listen();
			
			loadBalancerConnection.sendMessage(msg);
			
		} catch (Exception e) {
			TextAreaLogger.getInstance().log("Tentando se registrar como servidor...");
			Thread.yield();
			tellLoadBalancer(host, port);
			return;
		}
	}
	
	public void close() {
		loadBalancerConnection.closeConnection();
	}
	
	@Override
	public void newConnection(Connection connection) {		
		connections[next] = new UserConnection(this, connection);
			
		try {
			loadBalancerConnection.sendMessage(new Message("NotifyNewConnection",
				InetAddress.getLocalHost().getHostAddress() + ":" + String.valueOf(this.port),
				connection.getHost().getHostAddress() + ":" + connection.getPort()));
		} catch (UnknownHostException e) {
		}
		
		TextAreaLogger.getInstance().log("Conex�o aceita de " + connection.getHost().toString() +
			" na porta " + connection.getPort());
				
		next++;
	}
	
	@Override
	public void connectionClosed(Connection connection) {
		for (int i = 0; i < next; i++) {
			if (connection.equals(connections[i].getConnection())) {
				connections[i] = null;
								
				break;
			}
		}
	}
	
	public void sendData() {
		Iterator<User> users = SalesSystem.getInstance().getAllUsers();
		ArrayList<String> usersResult = new ArrayList<String>();
		ArrayList<String> itensResult = new ArrayList<String>();
		
		usersResult.add("Users");
		itensResult.add("Itens");
		
		while (users.hasNext()) {
			User user = users.next();
			usersResult.add(user.toString());
			
			Iterator<Item> itens = user.getItensSelling();
			
			while (itens.hasNext()) {
				Item item = itens.next();
				itensResult.add(item.toString());
			}
		}
		
		loadBalancerConnection.sendMessage(new Message("BroadcastServerMessage",
			usersResult.toArray(new String[usersResult.size()])));		
		loadBalancerConnection.sendMessage(new Message("BroadcastServerMessage", 
			itensResult.toArray(new String[itensResult.size()])));
	}
	
	public void authenticateConnection(Connection conn, User user) {
		for (int i = 0; i < next; i++) {
			if (conn.equals(connections[i].getConnection())) {
				connections[i].setUser(user);
				
				break;
			}
		}
	}

	public void logoff(User user) {
		for (int i = 0; i < next; i++) {
			if (connections[i].getUser().equals(user)) {
				connections[i].setUser(null);
						
				try {
					loadBalancerConnection.sendMessage(new Message("NotifyConnectionClosed",
						InetAddress.getLocalHost().getHostAddress() + ":" + String.valueOf(this.port),
						user.getName()));
				} catch (UnknownHostException e) {
				}
								
				break;
			}
		}
	}
}
