package javac.absyn;

import javac.type.Type;
import javac.util.Position;

public abstract class Expr extends Node {
	
	public Type ty;
	public Type inferType;//filled by parser
	public boolean lvalue,isConst;//can be lvalue or not
	public Integer size,depth;
	public int value;
	public boolean hasSideEffect;

	public Expr(Position pos) {
		super(pos);
		hasSideEffect=false;
		size=1;
		depth=1;
	}
}
