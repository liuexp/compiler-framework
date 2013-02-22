package javac.quad;

public class Jump extends Quad {

	public Label label;
	public Jump(Label l) {
		label = l;
	}
	@Override
	public String toString() {
		return "j " + label.toString();
	}
	@Override
	public String toAsm() {
		return "j " + label.toString();
	}
}
