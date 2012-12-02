/**This software is distributed under terms of the BSD license. See the LICENSE file for details.**/
package ab.framework.client.commands;



public class ClientDragCmd extends ClientCmd{

/**
	 * 
	 */
	private static final long serialVersionUID = 3238791684351832499L;
	private int x, y, dx, dy;

public ClientDragCmd(){}

public ClientDragCmd(int x, int y, int dx, int dy) {
	super();
	this.x = x;
	this.y = y;
	this.dx = dx;
	this.dy = dy;
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

public int getDx() {
	return dx;
}

public void setDx(int dx) {
	this.dx = dx;
}

public int getDy() {
	return dy;
}

public void setDy(int dy) {
	this.dy = dy;
}

public String toString()
{
   return "Drag from ("+ x + " " + y +") to (" + (x + dx) +"  " + (y + dy)+")";	
}
@Override
public String getCommandName() {
	return "Drag";
}
}
