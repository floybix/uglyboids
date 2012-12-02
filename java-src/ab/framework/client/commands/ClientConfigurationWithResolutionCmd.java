/**This software is distributed under terms of the BSD license. See the LICENSE file for details.**/
package ab.framework.client.commands;


public class ClientConfigurationWithResolutionCmd extends ClientCmd{


/**
	 * 
	 */
	private static final long serialVersionUID = 5724435270575050368L;
private String id;
private String resolution = "" ;
public String getResolution() {
	return resolution;
}

public void setResolution(String resolution) {
	this.resolution = resolution;
}

public ClientConfigurationWithResolutionCmd()
{
	
}

public ClientConfigurationWithResolutionCmd(String id, String resolution)
{
   this.id = id;	
   this.resolution = resolution;

}
public ClientConfigurationWithResolutionCmd(String id)
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
   return "Player  Id: "+ id + " is configured" ;	
}
@Override
public String getCommandName() {
	return "Configuration";
}
}
