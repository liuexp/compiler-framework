package javac.quad;

public class Temp {

	public Temp() {
		num = count++;
		ssanum=0;
		isOnceTemp=false;
		isConst=false;
		value=0;
		liveEnd=liveStart=-1;
		isParamTemp=false;
	}
	
	@Override
	public boolean equals(Object other) {
		if(other instanceof Temp)
			return ((toString()).equals(other.toString()));
		else return false;
	}
	
	@Override
	public int hashCode()
	{
		return num;
	}
	@Override
	public String toString() {
		return "t" + num;
	}
	
	public void addSSA(){
		ssanum++;
	}
	
	public String toSSA(){
		return "t" + num+"_"+ssanum;
	}
	int ssanum;
	int num;
	static int count = 0;
	public boolean isOnceTemp;//is this a generated temp and can't be used again in CSE?
	public boolean isConst;
	public int liveStart,liveEnd;
	public int value;
	public boolean isParamTemp;
}
