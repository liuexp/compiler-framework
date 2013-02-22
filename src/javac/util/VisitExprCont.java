package javac.util;

import javac.absyn.NodeVisitor;

public class VisitExprCont implements Cont {

	@Override
	public boolean isNull() {
		return false;
	}
	
	public void visit(NodeVisitor visitor) {
		
	}

}
