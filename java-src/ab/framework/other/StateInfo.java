package ab.framework.other;

import java.io.Serializable;

public class StateInfo implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6605396042195204956L;
    private int score;
    private String state;
	public int getScore() {
		return score;
	}
	public void setScore(int score) {
		this.score = score;
	}
	public String getState() {
		return state;
	}
	public void setState(String state) {
		this.state = state;
	}
	public StateInfo(int score, String state) {
		super();
		this.score = score;
		this.state = state;
	}
    
}
