/**This software is distributed under terms of the BSD license. See the LICENSE file for details.**/
package ab.framework.player;

import java.io.Serializable;
import java.util.HashMap;

public class Configuration implements Serializable {
/**
	 * 
	 */
	private static final long serialVersionUID = -1106687524458529457L;
private String player_id;
private int current_level;
private int run;
private int max_level;
private String ssDir;
private String mainDir;

public int getRun() {
	return run;
}
public void setRun(int run) {
	this.run = run;
}
public String getSsDir() {
	return ssDir;
}
public void setSsDir(String ssDir) {
	this.ssDir = ssDir;
}
public String getMainDir() {
	return mainDir;
}
public void setMainDir(String mainDir) {
	this.mainDir = mainDir;
}
public int getMax_level() {
	return max_level;
}
public void setMax_level(int max_level) {
	this.max_level = max_level;
}
private HashMap<Integer,Integer> level_grades;
public String getPlayer_id() {
	return player_id;
}

public Configuration(String player_id, int current_level, int run,
		int max_level, 
		HashMap<Integer, Integer> level_grades) {
	super();
	this.player_id = player_id;
	this.current_level = current_level;
	this.run = run;
	this.max_level = max_level;
	this.level_grades = level_grades;
}
public Configuration() {
	// TODO Auto-generated constructor stub
}
public void setPlayer_id(String player_id) {
	this.player_id = player_id;
}
public int getCurrent_level() {
	return current_level;
}
public void setCurrent_level(int current_level) {
	this.current_level = current_level;
}
public HashMap<Integer, Integer> getLevel_grades() {
	return level_grades;
}
public void setLevel_grades(HashMap<Integer, Integer> level_grades) {
	this.level_grades = level_grades;
}
public void increaseMax()
{http://www.google.com.au/url?sa=t&rct=j&q=&esrc=s&source=web&cd=1&ved=0CDAQFjAA&url=http%3A%2F%2Fbbs.csdn.net%2Ftopics%2F90420281&ei=LTaxUNO6JZCziQfrq4GoAw&usg=AFQjCNFN62wzQgUEg7fDtZ_Uhpr8f6tbng&sig2=Xj9qtqltzZBQQS-oqCj2kg
   this.max_level ++;	
}
public void updateLevelGrades(int grades)
{
    if(!level_grades.containsKey(current_level))
    {
    	level_grades.put(current_level, grades);
    }
    else
    {
    	int _grade = level_grades.get(current_level);
        if(_grade < grades)
        {
        	level_grades.put(current_level, grades);
        }
    }
}
public String toString()
{
	String result = "";
	result += " The player is " + this.player_id + "\n Current Level: " + this.current_level + "\n Max Level: " + this.max_level;
	for (Integer key: level_grades.keySet())
	{
		result+= "\n Level " + key + " Grades: " + this.level_grades.get(key); 
	}
	result = "Configuration: #########################\n" + result + "\n #########################\n";
    return result;
}
public void increaseRun() {
	this.run++;
	
}
}
