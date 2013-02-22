package javac.absyn;

import javac.util.Position;

public class NewArray extends Expr {
	
	public TypeSpecifier type;
	public Expr expr;

	public NewArray(Position pos, TypeSpecifier type, Expr expr) {
		super(pos);
		this.type = type;
		this.expr = expr;
		this.isConst = false;
		this.size=expr.size;
		this.depth=expr.depth;
	}

	@Override
	public void accept(NodeVisitor visitor) {
		type.accept(visitor);
		expr.accept(visitor);
		visitor.visit(this);
	}
}
