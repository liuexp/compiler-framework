package javac.semantic;

import javac.absyn.*;
import javac.env.Env;
import javac.env.FuncEntry;
import javac.env.TypeEntry;
import javac.type.RECORD;

public class GlobalVisitor extends Semantic {

	public GlobalVisitor(Env env) {
		super(env);
		// TODO Auto-generated constructor stub
	}
	@Override
	public void visit(FunctionDef functionDef) {
		env.put(functionDef.head.functionName, new FuncEntry(functionDef.head));
	}
	@Override
	public void visit(PrototypeDecl prototypeDecl) {
		env.put(prototypeDecl.head.functionName, new FuncEntry(prototypeDecl.head));
	}
	@Override
	public void visit(RecordDef recordDef) {
		env.put(recordDef.name, new TypeEntry(recordDef.name, new RECORD(recordDef.name)));
	}
}
