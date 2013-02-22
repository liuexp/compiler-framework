package javac.env;

import javac.env.Entry;
import javac.symbol.Symbol;


public class TypeEntry extends Entry {

	public javac.type.Type type;
	public TypeEntry(Symbol n, javac.type.Type t) {
		super(n);
		type = t;
	}

	public static TypeEntry transferTypeEntry(Entry e) {
		return (TypeEntry) e;
	}
	
	@Override
	public Etype getType(){
		return Etype.Type;
	}

}
