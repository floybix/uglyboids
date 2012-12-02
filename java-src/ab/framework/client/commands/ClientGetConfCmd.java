/**This software is distributed under terms of the BSD license. See the LICENSE file for details.**/
package ab.framework.client.commands;


public class ClientGetConfCmd extends ClientCmd{

/**
	 * 
	 */
	private static final long serialVersionUID = 4253073757932322507L;
private String id;
public ClientGetConfCmd()
{
	}
public ClientGetConfCmd(String id)

{
    this.id = id;	
}


public String getId() {
	return id;
}
public void setId(String id) {
	this.id = id;
}
@Override
public String getCommandName() {
	return "Get Configuration";
}
}
