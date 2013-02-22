package javac.util;

public class NullVisitCont extends VisitExprCont {

	@Override
	public boolean isNull() {
		return true;
	}

}
