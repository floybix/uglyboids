package ab.framework.ai;
/**This software is distributed under terms of the BSD license. See the LICENSE file for details.**/


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;

import ab.framework.client.commands.ClientConfigurationCmd;
import ab.framework.client.commands.ClientConfigurationWithResolutionCmd;
import ab.framework.client.commands.ClientDragCmd;
import ab.framework.client.commands.ClientFinishPlayCmd;
import ab.framework.client.commands.ClientFinishRunCmd;
import ab.framework.client.commands.ClientGetConfCmd;
import ab.framework.client.commands.ClientGetGlobalConfCmd;
import ab.framework.client.commands.ClientGetStateInfoCmd;
import ab.framework.client.commands.ClientLoadLevelCmd;
import ab.framework.client.commands.ClientMouseWheelCmd;
import ab.framework.client.commands.ClientNextLevelCmd;
import ab.framework.client.commands.ClientRestartCmd;
import ab.framework.client.commands.ClientScreenShotCmd;
import ab.framework.client.commands.ClientShootCmd;
import ab.framework.client.commands.ClientShootWithStateInfoReturnedCmd;
import ab.framework.other.Shot;
import ab.framework.other.StateInfo;
import ab.framework.player.Configuration;

public class ClientActionRobot {
Socket requestSocket;
ObjectOutputStream out;
ObjectInputStream in;
String message;
String env_dir = "vision/Matlab/";
public ClientActionRobot(String... ip)
{
    if(ip.length == 0)
    {
    	ip[0] = "localhost";
    }
	try{
		//1. creating a socket to connect to the server
		requestSocket = new Socket(ip[0], 2004);
		System.out.println("Connected to " + ip[0] + " in port 2004");
		out = new ObjectOutputStream(requestSocket.getOutputStream());
		out.flush();
		in = new ObjectInputStream(requestSocket.getInputStream());
	}
	catch(UnknownHostException unknownHost){
		System.err.println("You are trying to connect to an unknown host!");
	}
	catch(IOException ioException){
		ioException.printStackTrace();
	}
}
public void screenShot(String imageName)
{
	try{
		//2. get Input and Output streams
	
		out.writeObject(new ClientScreenShotCmd());
		out.flush();
		
		System.out.println("client executes command: screen shot");
		//System.out.println("server feedback: "+(String)in.readObject());
		byte[] imageBytes = (byte[])in.readObject();
		FileOutputStream f;
		try {
			//System.out.println(imageName);
			f = new FileOutputStream(new File(env_dir +imageName));
			f.write(imageBytes);
			f.close();
			System.out.println("Screenshot saved");
		} catch (IOException e) {
		}	
	}
	catch(IOException ioException){
		ioException.printStackTrace();
	} catch (ClassNotFoundException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	

}
public boolean finishRun()
{
   try {
	out.writeObject(new ClientFinishRunCmd());
	out.flush();
	return in.readBoolean();
} catch (IOException e) {
	// TODO Auto-generated catch block
	e.printStackTrace();
}
return false;	
}
public boolean nextLevel()
{
   try {
	out.writeObject(new ClientNextLevelCmd());
	out.flush();
	return in.readBoolean();
} catch (IOException e) {
	// TODO Auto-generated catch block
	e.printStackTrace();
}
return false;	
}
public void zoomingOut()
{
	System.out.println("Zooming out");
	
	for (int i = 0; i < 15; i++) {
		try {
			//2. get Input and Output streams
			out.writeObject(new ClientMouseWheelCmd(-1));
			out.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	System.out.println("Waiting 2 seconds for zoom animation");
	try {
		Thread.sleep(2000);
	} catch (InterruptedException e) {
	}	

}
public void makeMove(int x, int y, int toX,int toY,int wait)
{
	try {
		out.writeObject(new ClientDragCmd(x, y, toX, toY));
		Thread.sleep(wait * 500);
	} catch (IOException e1) {
		// TODO Auto-generated catch block
		e1.printStackTrace();
	} catch (InterruptedException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}


}
public StateInfo shootWithStateInfoReturned(List<Shot> csc)
{
    try {
		out.writeObject(new ClientShootWithStateInfoReturnedCmd(csc));
		out.flush();
		return (StateInfo)( in.readObject());
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (ClassNotFoundException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}	
    return null;
}

public boolean shoot(List<Shot> csc)
{
    try {
		out.writeObject(new ClientShootCmd(csc));
		out.flush();
		return in.readBoolean();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
    return false;
}
public boolean loadLevel(int... i)
{
	try {
		out.writeObject(new ClientLoadLevelCmd(i.length==0?-1:i[0]));
		return in.readBoolean();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	return false;
}
public boolean loadALevel(int i)
{
	try {
		out.writeObject(new ClientLoadLevelCmd(i));
		return in.readBoolean();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	return false;
}
public boolean configureWithResolution()
{
   try {
    	out.writeObject(new ClientConfigurationWithResolutionCmd("AUS_Team","1244768"));
    	System.out.println(" Using resolution 1244*768. can be set in ClientActionRobot class");
    	return in.readBoolean();
} catch (IOException e) {
	// TODO Auto-generated catch block
	e.printStackTrace();
}
return false;	
}

public boolean configure(String team)
{
   try {
    	out.writeObject(new ClientConfigurationCmd(team));
    	return in.readBoolean();
} catch (IOException e) {
	// TODO Auto-generated catch block
	e.printStackTrace();
}
return false;	
}

public Configuration getConfiguration(String team)
{
    try {
    	out.writeObject(new ClientGetConfCmd(team));
    	return (Configuration)(in.readObject());
} catch (IOException e) {
	// TODO Auto-generated catch block
	e.printStackTrace();
} catch (ClassNotFoundException e) {
	// TODO Auto-generated catch block
	e.printStackTrace();
}
	return null;

}
public void finishPlay() {
	   try {
	    	out.writeObject(new ClientFinishPlayCmd());
	    	
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	
}
public boolean restartLevel()
{
	try {
		out.writeObject(new ClientRestartCmd());
		return in.readBoolean();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}	
	return false;

}
public HashMap<Integer,Integer> getGlobalData()
{
	try {
		out.writeObject(new ClientGetGlobalConfCmd());
		return (HashMap<Integer,Integer>)in.readObject();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (ClassNotFoundException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	return null;
	

}
public StateInfo getStateInfo()
{
	try {
		out.writeObject(new ClientGetStateInfoCmd());
		return (StateInfo)in.readObject();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (ClassNotFoundException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	return null;

}




}
