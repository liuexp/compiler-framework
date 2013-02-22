package javac.absyn;

import javac.util.Position;

public class WhileStmt extends Stmt {
	
	public Expr cond;
	public Stmt body;

	public WhileStmt(Position pos, Expr cond, Stmt body) {
		super(pos);
		this.cond = cond;
		this.body = body;
		size=cond.size+body.size+1;
	}

	@Override
	public void accept(NodeVisitor visitor) {
		visitor.previsit(this);
		cond.accept(visitor);
		body.accept(visitor);
		visitor.visit(this);
	}
}
