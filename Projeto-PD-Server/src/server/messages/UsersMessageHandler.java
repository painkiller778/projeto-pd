package server.messages;

import network.Message;
import system.core.SalesSystem;

public class UsersMessageHandler implements MessageHandler {

	@Override
	public boolean handleMessage(MessageContext context) {
		Message message = context.getMessage();
		
		if (!message.getCommand().equals("Users")) {
			return false;
		}		
		
		String[] params = message.getParams();
		
		for (String param: params) {
			String[] split = param.split("\\;");
			
			SalesSystem.getInstance().createUser(split[0], split[1], false);
		}
		
		message.getConnection().sendMessage(new Message("OK", String.valueOf(message.getID())));
		
		return true;
	}

}
