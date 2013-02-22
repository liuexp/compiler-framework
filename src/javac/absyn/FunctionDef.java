package javac.absyn;

import java.util.HashMap;

import javac.trans.rewrittenArray;
import javac.util.Position;

public class FunctionDef extends ExternalDecl {
	
	public FunctionHead head;
	public VariableDeclList vardec;
	public StmtList stmts;
	public boolean canInline;
	public boolean canRewriteArray;
	public HashMap<rewrittenArray, Integer> arrayCount;

	public FunctionDef(Position pos, FunctionHead head, VariableDeclList varDec, StmtList stmtList) {
		super(pos);
		this.head = head;
		this.vardec = varDec;
		this.stmts = stmtList;
		canInline=true;
		canRewriteArray=true;
	}

	@Override
	public void accept(NodeVisitor visitor) {
		head.accept(visitor);
		vardec.accept(visitor);
		stmts.accept(visitor);
		visitor.visit(this);
	}
}
