package javac.absyn;

import java.util.LinkedList;
import java.util.List;

import javac.util.Position;

public class FunctionCall extends Expr {
	
	public Expr expr;//, args;
	public ArgsList args;
	public boolean killedret;
	
	public LinkedList<Expr> params;

	public FunctionCall(Position pos, Expr expr, ArgsList args) {
		super(pos);
		this.expr = expr;
		this.args = args;
		this.size=expr.size;//+(args==null?0:args.exprList.size())+1;
		depth=expr.depth;
		//TODO:be more careful here?
		hasSideEffect=true;
		killedret=false;
		if(expr.toString().equals("ord")&&args!=null&&args.exprList.size()>0){
			Expr tmp=args.exprList.get(0);
			if(tmp.isConst||tmp instanceof CharLiteral){
				this.isConst=true;
				this.value=tmp.value;
			}
		}
	}

	@Override
	public void accept(NodeVisitor visitor) {
		expr.accept(visitor);
		if(args!=null)args.accept(visitor);
		visitor.visit(this);
	}
}
