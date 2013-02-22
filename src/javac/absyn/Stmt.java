package javac.absyn;

import javac.util.Position;

public abstract class Stmt extends Node {

	public int size;
	protected Stmt(Position pos) {
		super(pos);
	}
}
