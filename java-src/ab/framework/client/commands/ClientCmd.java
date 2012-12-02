/**This software is distributed under terms of the BSD license. See the LICENSE file for details.**/
package ab.framework.client.commands;

import java.io.Serializable;

public abstract class ClientCmd implements Serializable  {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1412004057556874851L;

	public abstract String getCommandName();
}
