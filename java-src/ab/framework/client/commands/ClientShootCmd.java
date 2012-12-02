/**This software is distributed under terms of the BSD license. See the LICENSE file for details.**/
package ab.framework.client.commands;

import java.util.List;

import ab.framework.other.Shot;


public class ClientShootCmd extends ClientCmd{


/**
	 * 
	 */
	private static final long serialVersionUID = 2517812195452004077L;
    private List<Shot> shots;

public ClientShootCmd()
{
	
}
public ClientShootCmd(List<Shot> shots)
{
	this.shots = shots;
}



public List<Shot> getShots() {
	return shots;
}
public void setShots(List<Shot> shots) {
	this.shots = shots;
}
@Override
public String getCommandName() {
	return "Shoot Cmd";
}
}
