package javac.Instruction;


import javac.quad.Label;

public class JAL {
	public JAL(Label name) {
		function = name;
	}
	public Label function;
	@Override
	public String toString() {
		return "jal "+function;
	}
}
