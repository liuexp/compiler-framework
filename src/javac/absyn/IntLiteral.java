package javac.absyn;

import javac.type.INT;
import javac.util.Position;

public class IntLiteral extends Expr {
	
	public int i;

	public IntLiteral(Position pos, int i) {
		super(pos);
		this.i = i;
		this.isConst = true;
		this.size=1;
		hasSideEffect=false;
		inferType=INT.getInstance();
		value=i;
	}
	
	@Override
	public String toString() {
		return String.format("%d", i);
	}

	@Override
	public void accept(NodeVisitor visitor) {
		visitor.visit(this);
	}
}
