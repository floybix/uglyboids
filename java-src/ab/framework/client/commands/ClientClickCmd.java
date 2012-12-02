/**This software is distributed under terms of the BSD license. See the LICENSE file for details.**/
package ab.framework.client.commands;



public class ClientClickCmd extends ClientCmd{

	/**
	 * 
	 */
	private static final long serialVersionUID = -719919656024902525L;
	private int x, y;

public ClientClickCmd(){}

public ClientClickCmd(int x, int y) {
	super();
	this.x = x;
	this.y = y;
}

public int getX() {
	return x;
}

public void setX(int x) {
	this.x = x;
}

public int getY() {
	return y;
}

public void setY(int y) {
	this.y = y;
}



public String toString()
{
   return "Click at ("+ x + " " + y +")";	
}
@Override
public String getCommandName() {
	return "Click";
}
}
