package javac.Instruction;

public class MOVE {
	public String src;
	public String dst;
	public MOVE(String d, String s) {
		dst = d;
		src = s;
	}
	@Override
	public String toString() {
		return "move "+dst+","+src;
	}
}
