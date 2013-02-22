package javac.quad;
abstract public class Quad {
	//def can only contain 0 or 1 element.
	public java.util.HashSet<Temp> use,def;
	public int instructionNo;
	public boolean killed;
	abstract public String toString();
	abstract public String toAsm();
}
