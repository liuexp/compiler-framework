package javac.trans;

import javac.quad.Label;
import org.apache.commons.lang3.StringEscapeUtils;

public class DataFrags extends Frags {

	Label name;
	String s;
	public DataFrags() {
	}

	public DataFrags(Label name, String s) {
		this.name=name;
		this.s=s;
	}
	
	public String toString() {
		return name.toString() + ":\n" + s ; 
	}
	public String toAsm() {
		return ".data\n"+name.toString() + ":\n .asciiz \"" + StringEscapeUtils.escapeJava(s)+"\""; 
	}
}
