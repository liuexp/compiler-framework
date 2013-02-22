package javac.linearScan;

import java.util.Comparator;

public class IntCompE implements Comparator<Interval>{

	public int compare(Interval o1, Interval o2) {
		if(o1.endpoint>o2.endpoint)return 1;
		return o1.endpoint < o2.endpoint?-1:(o1.startpoint<o2.startpoint?-1:0);
	}
}