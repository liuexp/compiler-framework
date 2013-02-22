package javac.Instruction;

import javac.quad.LabelAddress;

public class LA {
	LabelAddress label;
	String dst;
	public LA(LabelAddress l,String dst){
		label=l;
		this.dst=dst;
	}
	@Override
	public String toString(){
		return "la "+dst+","+label;
	}
}
