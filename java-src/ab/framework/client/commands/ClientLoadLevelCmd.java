/**This software is distributed under terms of the BSD license. See the LICENSE file for details.**/
package ab.framework.client.commands;


public class ClientLoadLevelCmd extends ClientCmd{



/**
	 * 
	 */
	private static final long serialVersionUID = 3170570308252347809L;
private int level = -1;

public ClientLoadLevelCmd(){}
public ClientLoadLevelCmd(int level) {
	super();
	this.level = level;
}

public int getLevel() {
	return level;
}

public void setLevel(int level) {
	this.level = level;
}

@Override
public String getCommandName() {
	return "Load Level: ";
}
}
