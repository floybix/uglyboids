/**This software is distributed under terms of the BSD license. See the LICENSE file for details.**/
package ab.framework.client.commands;


public class ClientScreenShotCmd extends ClientCmd{

/**
	 * 
	 */
	private static final long serialVersionUID = 766585458497075718L;
public String directory;
public ClientScreenShotCmd(){}

public ClientScreenShotCmd(String directory)
{
	this.directory = directory;
			}
public String getDirectory() {
	return directory;
}

public void setDirectory(String directory) {
	this.directory = directory;
}

@Override
public String getCommandName() {
	return "Screen Shot";
}
}
