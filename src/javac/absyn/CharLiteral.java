package javac.absyn;

import javac.type.CHAR;
import javac.type.STRING;
import javac.util.Position;

public class CharLiteral extends Expr {
	
	public char c;

	public CharLiteral(Position pos, char c) {
		super(pos);
		this.c = c;
		this.isConst = true;
		this.value=Integer.valueOf(c);
		size=1;
		hasSideEffect=false;
		inferType=CHAR.getInstance();
	}
	
	@Override
	public String toString() {
		return String.format("'%c'", c);
	}

	@Override
	public void accept(NodeVisitor visitor) {
		visitor.visit(this);
	}
}
