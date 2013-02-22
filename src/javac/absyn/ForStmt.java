package javac.absyn;

import javac.util.Position;

public class ForStmt extends Stmt {
	
	public Expr init, cond, step;
	public Stmt body;
	public boolean hasBreakOrContinue;

	public ForStmt(Position pos, Expr init, Expr cond, Expr step, Stmt body) {
		super(pos);
		this.init = init;
		this.cond = cond;
		this.step = step;
		this.body = body;
		size=(init==null?0:init.size)
				+(cond==null?0:cond.size)
				+(step==null?0:step.size)
				+(body==null?0:body.size)+1;
	}

	@Override
	public void accept(NodeVisitor visitor) {
		visitor.previsit(this);
		if(init != null)init.accept(visitor);
		cond.accept(visitor);
		if(step != null)step.accept(visitor);
		if(body != null)body.accept(visitor);
		visitor.visit(this);
	}
}
