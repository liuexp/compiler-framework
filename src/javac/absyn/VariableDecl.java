package javac.absyn;

import javac.util.Position;

public class VariableDecl extends Node {
	
	public TypeSpecifier type;
	public IdList ids;
	public boolean inRecord;

	public VariableDecl(Position pos, TypeSpecifier type, IdList ids) {
		super(pos);
		this.type = type;
		this.ids = ids;
		inRecord=false;
	}

	@Override
	public void accept(NodeVisitor visitor) {
		type.accept(visitor);
		ids.accept(visitor);
		visitor.visit(this);
	}
}
