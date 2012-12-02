/**This software is distributed under terms of the BSD license. See the LICENSE file for details.**/
package ab.framework.client.commands;



public class ClientMouseWheelCmd extends ClientCmd{



/**
	 * 
	 */
	private static final long serialVersionUID = 327322740478391995L;
	private int operation;
public int getOperation() {
		return operation;
	}

	public void setOperation(int operation) {
		this.operation = operation;
	}

public ClientMouseWheelCmd(){}

public String toString()
{
   return "Scroll mouse wheel ";	
}
public ClientMouseWheelCmd(int operation) {
	super();
	this.operation = operation;
}

@Override
public String getCommandName() {
	return "Mouse Wheel";
}
}
