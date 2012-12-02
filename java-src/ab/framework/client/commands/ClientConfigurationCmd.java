/**This software is distributed under terms of the BSD license. See the LICENSE file for details.**/
package ab.framework.client.commands;


public class ClientConfigurationCmd extends ClientCmd{

/**
	 * 
	 */
	private static final long serialVersionUID = -3245855248754224674L;
private String id;

public ClientConfigurationCmd()
{
	
}

public ClientConfigurationCmd(String id)
{
   this.id = id;	
}


public String getId() {
	return id;
}


public void setId(String id) {
	this.id = id;
}


public String toString()
{
   return "Player  Id: "+ id + " is configured";	
}
@Override
public String getCommandName() {
	return "Configuration";
}
}
