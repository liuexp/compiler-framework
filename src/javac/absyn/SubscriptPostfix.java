package javac.absyn;

import javac.util.Position;

public class SubscriptPostfix extends Expr {
	
	public Expr expr, subscript;

	public SubscriptPostfix(Position pos, Expr expr, Expr subscript) {
		super(pos);
		if(expr instanceof FunctionCall&&((FunctionCall)expr).expr.toString().equals("substring")&&subscript.isConst&&subscript.value==0){
			FunctionCall f=(FunctionCall)expr;
			if(f.args.exprList.size()>=3){
				expr=f.args.exprList.get(0);
				subscript=f.args.exprList.get(1);
			}
		}
		this.expr = expr;
		this.subscript = subscript;
		this.size=expr.size+subscript.size+1;
		this.depth=(expr.depth+subscript.depth)+1;
	}

	@Override
	public void accept(NodeVisitor visitor) {
		expr.accept(visitor);
		subscript.accept(visitor);
		visitor.visit(this);
	}
}
