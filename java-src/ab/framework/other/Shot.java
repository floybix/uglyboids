/**This software is distributed under terms of the BSD license. See the LICENSE file for details.**/
package ab.framework.other;

import java.io.Serializable;


public class Shot implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 7009458239303515229L;
	private int x;
	private int y;
	private int dx;
	private int dy;
	public int getDx() {
		return dx;
	}


	public void setDx(int dx) {
		this.dx = dx;
	}


	public int getDy() {
		return dy;
	}


	public Shot(int x, int y, int dx, int dy, int t_shot, int t_tap) {
		super();
		this.x = x;
		this.y = y;
		this.dx = dx;
		this.dy = dy;
		this.t_shot = t_shot;
		this.t_tap = t_tap;
	}


	public void setDy(int dy) {
		this.dy = dy;
	}
	private int t_shot;
	private int t_tap;
public Shot()
{
	
}


public Shot(int x, int y, int t_shot, int t_tap) {
	super();
	this.x = x;
	this.y = y;
	this.t_shot = t_shot;
	this.t_tap = t_tap;
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


public int getT_shot() {
	return t_shot;
}


public void setT_shot(int t_shot) {
	this.t_shot = t_shot;
}


public int getT_tap() {
	return t_tap;
}


public void setT_tap(int t_tap) {
	this.t_tap = t_tap;
}


public String toString()
{
   return "Shoot from: ("+ x+ "  "+ y +" )" + " at time " + t_shot + " tap at " + t_tap;	
}

}
