package javac.Instruction;

import javac.quad.LabelAddress;

public class LI {
	int c;
	String dst;
	public LI(int c,String dst){
		this.c=c;
		this.dst=dst;
	}
	@Override
	public String toString(){
		return "addiu "+dst+",$zero,"+c;
	}
}
