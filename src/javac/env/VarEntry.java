package javac.env;

import javac.env.Entry;

import javac.symbol.Symbol;
import javac.type.Type;

public class VarEntry extends Entry {

	public Type type;

	public VarEntry() {
		// TODO Auto-generated constructor stub
	}

	public VarEntry(Symbol n,Type t) {
		super(n);
		type=t;
	}

	@Override
	public Etype getType() {
		return Etype.Var;
	}

}
