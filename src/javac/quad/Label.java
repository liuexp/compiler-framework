package javac.quad;

public class Label {
	String name;
	public Label() {
		name = "_L" + count++;
	}
	
	public Label(String s) {
		name = s;
	}
	@Override
	public int hashCode()
	{
		return name.hashCode();
	}

	public String toString() {
		return name;
	}
	
	@Override
	public boolean equals(Object a){
		if(a instanceof Label)return toString().equals(a.toString());
		else return false;
	}
	
	static int count = 0;
}
