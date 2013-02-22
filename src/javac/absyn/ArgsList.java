package javac.absyn;

import javac.util.Position;
import java.util.LinkedList;
import java.util.List;

public class ArgsList extends Node {
	public LinkedList<Expr> exprList = new LinkedList<Expr>();
	public ArgsList(Position pos) {
		super(pos);
	}
	public void add(Expr e){
		exprList.add(e);
	}

	@Override
	public void accept(NodeVisitor visitor) {
		for (Expr e : exprList) {
			e.accept(visitor);
		}
		visitor.visit(this);

	}

}

