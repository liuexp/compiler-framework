package javac.Instruction;

import javac.quad.Label;

public class J {
	public Label label;
	public J(Label l) {
		label = l;
	}
	@Override
	public String toString() {
		return "j " + label.toString();
	}
}
