package javac.linearScan;


public class Interval {
	public Integer endpoint,startpoint;
	public Interval(){
	
	}
	public Interval(int s,int e){
		startpoint=s;
		endpoint=e;
	}

}