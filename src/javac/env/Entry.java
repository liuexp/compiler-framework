package javac.env;

import javac.symbol.Symbol;

public abstract class Entry {
	protected Symbol name;
	public Entry(){}

	public Entry(Symbol n) {
		name = n;
	}
	
	public abstract Etype getType();
	
	public boolean isFunc(){
		return this.getType()==Etype.Func;
	}
	public boolean isVar(){
		return this.getType()== Etype.Var;
	}
	public boolean isType(){
		return this.getType()==Etype.Type;
	}
}
