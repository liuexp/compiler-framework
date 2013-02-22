package javac.quad;

import java.util.HashSet;


public class UnaryOp extends Quad {

	public Oprand src;
	public Oprand dst;
	public javac.absyn.UnaryOp op;
	public UnaryOp(Oprand d,Oprand s,javac.absyn.UnaryOp o) {
		src=s;
		dst=d;
		op=o;
		use=new HashSet<Temp>();
		def=new HashSet<Temp>();
		if(s instanceof TempOprand&&!((TempOprand)s).temp.isOnceTemp)use.add(((TempOprand)s).temp);
		else if(s instanceof Mem)use.add(((Mem)s).base);
		if(d instanceof TempOprand&&!((TempOprand)d).temp.isOnceTemp)def.add(((TempOprand)d).temp);
		else if(d instanceof Mem)def.add(((Mem)d).base);
	}

	@Override
	public String toString() {
		return dst.toString() + "<-"+ trans(op) + src.toString() ;
	}
	@Override
	public String toAsm() {
		//return getAsm(op) + dst.toString() + ","+ src.toString() ;
		return getAsm(op) + " $t1, $t0";
	}
	public static String getAsm(javac.absyn.UnaryOp o) { 
		switch(o) {
		case NOT:
			return "not";
		default:
			return "error";
		}
	}
	public static String trans(javac.absyn.UnaryOp o) { 
		switch(o) {
		case PLUS: 
			return "+";
		case MINUS: 
			return "-";
		case NOT:
			return "!";
		default:
			return "error";
		}
	}
}
