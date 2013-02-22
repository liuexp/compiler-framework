package javac.absyn;

import java.util.LinkedList;

import javac.util.Position;

public class StmtList extends Node {
	
	public LinkedList<Stmt> statements = new LinkedList<Stmt>();
	public int size;

	public StmtList(Position pos) {
		super(pos);
	}
	
	public void add(Stmt stmt) {
		statements.add(stmt);
		size+=stmt.size;
	}

	@Override
	public void accept(NodeVisitor visitor) {
		for (Stmt stmt : statements) {
			stmt.accept(visitor);
		}
		visitor.visit(this);
	}
}
