package javac.env;

import javac.absyn.FunctionHead;

public class FuncEntry extends Entry {
	public FunctionHead head;
	public FuncEntry(FunctionHead h) {
		super(h.functionName);
		this.head = h;
	}
	public static FuncEntry transferFuncEntry(Entry e) {
		return (FuncEntry) e;
	}
	
	@Override
	public Etype getType(){
		return Etype.Func;
	}

}
