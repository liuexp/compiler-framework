package javac.absyn;

import javac.util.Position;

public class NewRecord extends Expr {
	
	public TypeSpecifier type;

	public NewRecord(Position pos, TypeSpecifier type) {
		super(pos);
		this.type = type;
		this.isConst = false;
		this.size=1;
		this.depth=1;
	}

	@Override
	public void accept(NodeVisitor visitor) {
		type.accept(visitor);
		visitor.visit(this);
	}
}
